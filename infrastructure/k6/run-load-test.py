"""Run isolated Day 13 fixtures and report k6 results plus Order DB stock snapshots.

Requires only Python 3.10+ and Docker Compose. Run from the repository root.
"""

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone


ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "infrastructure/k6/results"


def request_json(base_url, path, body, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=json.dumps(body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Do not print request bodies, which can include login credentials.
        raise RuntimeError(f"{path} returned HTTP {error.code}") from None


def fixture_admin_token(base_url):
    email = os.environ.get("FIXTURE_ADMIN_EMAIL")
    password = os.environ.get("FIXTURE_ADMIN_PASSWORD")
    if not email or not password:
        raise RuntimeError("Set FIXTURE_ADMIN_EMAIL and FIXTURE_ADMIN_PASSWORD for an existing ADMIN fixture account")
    auth = request_json(base_url, "/api/auth/login", {"email": email, "password": password})
    return auth["accessToken"]


def compose_command(args):
    return ["docker", "compose", "--env-file", args.env_file, "-f", args.compose_file]


def run_command(command, **kwargs):
    result = subprocess.run(command, cwd=ROOT, text=True, encoding="utf-8",
                            capture_output=True, **kwargs)
    if result.returncode:
        raise RuntimeError("Docker Compose command failed; check service health and Docker access")
    return result.stdout.strip()


def order_database(args):
    # Resolve customized database names through Compose without printing credentials.
    config = json.loads(run_command(compose_command(args) + ["config", "--format", "json"]))
    if config["services"]["payment-service"]["environment"].get("PAYMENT_PROVIDER", "mock") != "mock":
        raise RuntimeError("Day 13 load tests require PAYMENT_PROVIDER=mock; do not load-test a real payOS channel")
    return config["services"]["order-inventory-service"]["environment"]["ORDER_DB"]


def sql(args, database, statement):
    command = compose_command(args) + [
        "exec", "-T", "postgres", "sh", "-c",
        'exec psql -U "$POSTGRES_USER" -d "$1" -X -A -t -v ON_ERROR_STOP=1',
        "sh", database,
    ]
    return run_command(command, input=statement)


def prepare_fixture(args, database, rate):
    admin_token = fixture_admin_token(args.base_url)
    run_id = f"day13-{rate}-{uuid.uuid4().hex[:12]}"
    email = f"{run_id}@example.test"
    password = secrets.token_urlsafe(24)
    sku = run_id.upper()
    stock = rate * args.duration + 100
    request_json(args.base_url, "/api/auth/register", {
        "firstName": "Load", "lastName": "Test", "email": email, "password": password,
    })
    category = request_json(args.base_url, "/api/categories", {"name": run_id}, admin_token)
    product = request_json(args.base_url, "/api/products", {
        "name": run_id,
        "categoryId": category["id"],
        # Mock payments succeed for amounts <= 10000; avoid compensation in a capacity test.
        "variants": [{"sku": sku, "name": "Load test", "price": 1000, "stock": stock}],
    }, admin_token)
    product_id = product["id"]
    if not re.fullmatch(r"[A-Za-z0-9_-]+", product_id):
        raise RuntimeError("Unexpected product ID format")
    # Operator fixture setup in the Order-owned DB, since there is no inventory write API.
    # Every run INSERTs a new SKU; existing inventory is never reset.
    sql(args, database, f"""
        INSERT INTO inventory
            (product_id, sku, total_stock, reserved_stock, available_stock, created_at, updated_at)
        VALUES ('{product_id}', '{sku}', {stock}, 0, {stock}, NOW(), NOW());
    """)
    return {"runId": run_id, "sku": sku, "productId": product_id, "initialStock": stock,
            "email": email, "password": password}


def snapshot(args, database, sku):
    # One SQL statement gives a consistent snapshot of inventory and orders for this fixture.
    statement = f"""
        BEGIN TRANSACTION READ ONLY;
        WITH fixture_orders AS (
            SELECT o.* FROM orders o
            WHERE EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id AND i.sku = '{sku}')
        )
        SELECT json_build_object(
            'totalStock', v.total_stock, 'reservedStock', v.reserved_stock,
            'availableStock', v.available_stock,
            'createdOrders', (SELECT count(*) FROM fixture_orders),
            'pendingOrders', (SELECT count(*) FROM fixture_orders WHERE status = 'PENDING'),
            'confirmedOrders', (SELECT count(*) FROM fixture_orders WHERE status = 'CONFIRMED'),
            'cancelledOrders', (SELECT count(*) FROM fixture_orders WHERE status = 'CANCELLED')
        ) FROM inventory v WHERE sku = '{sku}';
        COMMIT;
    """
    output = sql(args, database, statement)
    return json.loads(next(line for line in output.splitlines() if line.startswith("{")))


def wait_for_idle(args, database):
    """Keep previous Order/Saga work out of the next load measurement."""
    deadline = time.monotonic() + args.settle_timeout
    while True:
        output = sql(args, database, """
            SELECT json_build_object(
                'pendingOrders', (SELECT count(*) FROM orders o WHERE o.status = 'PENDING'
                    AND EXISTS (SELECT 1 FROM order_items i
                        WHERE i.order_id = o.id AND i.sku LIKE 'DAY13-%')),
                'pendingEvents', (SELECT count(*) FROM outbox_events WHERE published = false));
        """)
        backlog = json.loads(output)
        if backlog["pendingOrders"] == 0 and backlog["pendingEvents"] == 0:
            return
        if time.monotonic() >= deadline:
            raise RuntimeError("Previous Order/Saga backlog is not settled; no new load was started")
        time.sleep(2)


def stock_checks(initial, final, successful_orders):
    return {
        "nonNegative": all(final[key] >= 0 for key in
                           ("totalStock", "reservedStock", "availableStock")),
        "stockEquation": final["totalStock"] == final["availableStock"] + final["reservedStock"],
        "reservationMatchesPending": final["reservedStock"] == final["pendingOrders"],
        "deductionMatchesConfirmed": initial - final["totalStock"] == final["confirmedOrders"],
        "noOverselling": final["confirmedOrders"] + final["pendingOrders"] <= initial,
        "httpMatchesDatabase": successful_orders == final["createdOrders"],
    }


def write_report(report):
    run_id = report["runId"]
    (RESULTS / f"{run_id}-report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")
    k6 = report["k6"]
    final = report["finalStock"]
    rows = [
        ("Target RPS", k6["targetRps"]), ("Achieved order RPS", round(k6["achievedRps"], 2)),
        ("p95 latency (ms)", k6["p95LatencyMs"]), ("Error rate", k6["errorRate"]),
        ("Successful HTTP 201 orders", k6["successfulOrders"]),
        ("Dropped iterations", k6["droppedIterations"]),
        ("Initial stock", report["initialStock"]), ("Final total stock", final["totalStock"]),
        ("Final reserved stock", final["reservedStock"]),
        ("Final available stock", final["availableStock"]),
        ("Confirmed orders", final["confirmedOrders"]), ("Pending orders", final["pendingOrders"]),
        ("Cancelled orders", final["cancelledOrders"]),
        ("Saga settled", report["sagaSettled"]), ("k6 thresholds passed", k6["thresholdsPassed"]),
        ("Saga settling time after k6 (s)", report["sagaSettleSeconds"]),
        ("Stock checks passed", all(report["stockChecks"].values())),
    ]
    text = f"# Day 13 performance report: {run_id}\n\n"
    text += f"Measured at: {report['measuredAt']}\n\n"
    text += "| Measurement | Result |\n| --- | --- |\n"
    text += "".join(f"| {name} | {value} |\n" for name, value in rows)
    text += "\nRPS = completed order requests / scheduled load duration. Login and preflight excluded.\n"
    text += "POST success means order created; confirmed orders separately show completed Saga payments.\n"
    if not report["sagaSettled"]:
        text += "\nSaga did not finish within the settling timeout; this stock snapshot includes reservations.\n"
    text += "\nFixtures are retained for inspection. No production performance claim is implied.\n"
    (RESULTS / f"{run_id}-report.md").write_text(text, encoding="utf-8")


def run_test(args, database, rate):
    fixture = prepare_fixture(args, database, rate)
    print(f"Running {fixture['runId']}: {rate} requests/s for {args.duration}s", flush=True)
    before = snapshot(args, database, fixture["sku"])
    environment = os.environ.copy()
    environment.update({
        "LOAD_TEST_EMAIL": fixture["email"], "LOAD_TEST_PASSWORD": fixture["password"],
        "PRODUCT_ID": fixture["productId"], "SKU": fixture["sku"], "RUN_ID": fixture["runId"],
        "RATE": str(rate), "DURATION_SECONDS": str(args.duration),
        "BASE_URL": args.container_base_url,
        "PRE_ALLOCATED_VUS": str(args.pre_allocated_vus or rate),
        "MAX_VUS": str(args.max_vus or rate * 2),
        "P95_LIMIT_MS": str(args.p95_limit_ms), "MAX_ERROR_RATE": str(args.max_error_rate),
    })
    command = compose_command(args) + ["run", "--rm", "--no-deps"]
    for name in ("LOAD_TEST_EMAIL", "LOAD_TEST_PASSWORD", "PRODUCT_ID", "SKU", "RUN_ID", "RATE",
                 "DURATION_SECONDS", "BASE_URL", "PRE_ALLOCATED_VUS", "MAX_VUS", "P95_LIMIT_MS",
                 "MAX_ERROR_RATE"):
        # Pass environment variable names, keeping credentials out of command lines and reports.
        command += ["-e", name]
    command += ["k6", "run", "/scripts/orders.js"]
    completed = subprocess.run(command, cwd=ROOT, env=environment, check=False)
    summary_file = RESULTS / f"{fixture['runId']}-k6.json"
    if not summary_file.exists():
        raise RuntimeError("k6 did not produce a summary; inspect setup/service errors")
    summary = json.loads(summary_file.read_text(encoding="utf-8"))
    settle_started = time.monotonic()
    deadline = settle_started + args.settle_timeout
    final = snapshot(args, database, fixture["sku"])
    while final["pendingOrders"] and time.monotonic() < deadline:
        time.sleep(2)
        final = snapshot(args, database, fixture["sku"])
    report = {
        "runId": fixture["runId"], "measuredAt": datetime.now(timezone.utc).isoformat(),
        "sku": fixture["sku"], "productId": fixture["productId"],
        "initialStock": fixture["initialStock"], "before": before, "finalStock": final,
        "sagaSettled": final["pendingOrders"] == 0,
        "sagaSettleSeconds": round(time.monotonic() - settle_started, 3),
        "stockChecks": stock_checks(fixture["initialStock"], final, summary["successfulOrders"]),
        "k6ExitCode": completed.returncode, "k6": summary,
    }
    write_report(report)
    print(f"Report: infrastructure/k6/results/{fixture['runId']}-report.md", flush=True)
    return (completed.returncode == 0 and summary["thresholdsPassed"]
            and all(report["stockChecks"].values()) and report["sagaSettled"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-file", default="docker-compose.yml")
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--base-url", default="http://localhost:8080",
                        help="Gateway URL reachable from this host for fixture setup")
    parser.add_argument("--container-base-url", default="http://api-gateway:8080",
                        help="Gateway URL reachable from the k6 container")
    parser.add_argument("--rates", type=int, nargs="+", default=[500, 1000])
    parser.add_argument("--duration", type=int, default=30, help="Seconds per load level")
    parser.add_argument("--settle-timeout", type=int, default=120)
    parser.add_argument("--pre-allocated-vus", type=int)
    parser.add_argument("--max-vus", type=int)
    parser.add_argument("--p95-limit-ms", type=float, default=2000)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    args = parser.parse_args()
    if (any(rate <= 0 for rate in args.rates) or args.duration <= 0 or args.settle_timeout < 0
            or args.p95_limit_ms <= 0 or not 0 < args.max_error_rate <= 1
            or any(value is not None and value <= 0 for value in
                   (args.pre_allocated_vus, args.max_vus))):
        parser.error("Rates, duration, VUs and latency limit must be positive; error rate in (0, 1]")
    RESULTS.mkdir(parents=True, exist_ok=True)
    database = order_database(args)
    outcomes = []
    for rate in args.rates:
        wait_for_idle(args, database)
        outcomes.append(run_test(args, database, rate))
    return 0 if all(outcomes) else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, ValueError) as exception:
        print(f"Load test failed: {exception}")
        raise SystemExit(1)
