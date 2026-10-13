"""Day 14 acceptance demo against a LOCAL Docker Compose stack (Python 3.10+).

Creates isolated fixtures; retains them for inspection. Briefly stops Notification
and always attempts to restart it. Never run against a live customer environment.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import re
import secrets
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "infrastructure/day14/results"
spec = importlib.util.spec_from_file_location("load_test", ROOT / "infrastructure/k6/run-load-test.py")
load_test = importlib.util.module_from_spec(spec)
spec.loader.exec_module(load_test)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def request(args, method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(args.base_url.rstrip("/") + path,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.load(error)
        except (ValueError, UnicodeError):
            return error.code, {}


def api(args, method, path, body=None, token=None, expected=200):
    status, data = request(args, method, path, body, token)
    require(status == expected, f"{method} {path}: expected {expected}, got {status}")
    return data


def eventually(probe, predicate, timeout, description):
    deadline = time.monotonic() + timeout
    while True:
        value = probe()
        if predicate(value):
            return value
        require(time.monotonic() < deadline, f"Timed out: {description}")
        time.sleep(1)


class Demo:
    def __init__(self, args):
        self.args = args
        self.run_id = "day14-" + uuid.uuid4().hex[:12]
        self.started = datetime.now(timezone.utc).isoformat()
        self.config = json.loads(self.compose("config", "--format", "json"))["services"]
        require(self.config["payment-service"]["environment"].get("PAYMENT_PROVIDER", "mock") == "mock",
                "Day 14 requires PAYMENT_PROVIDER=mock; use the dedicated payOS checkout flow for real payments")
        self.database = self.config["order-inventory-service"]["environment"]["ORDER_DB"]
        self.token = None
        self.admin_token = None

    def compose(self, *arguments):
        return load_test.run_command(load_test.compose_command(self.args) + list(arguments), timeout=180)

    def fixture(self, label, stock=1, price=1000):
        name = f"{self.run_id}-{label}"
        sku = name.upper()
        category = api(self.args, "POST", "/api/categories", {"name": name}, self.admin_token, expected=201)
        product = api(self.args, "POST", "/api/products", {
            "name": name, "categoryId": category["id"],
            "variants": [{"sku": sku, "name": label, "price": price, "stock": stock}],
        }, self.admin_token, expected=201)
        product_id = product["id"]
        require(re.fullmatch(r"[A-Za-z0-9_-]+", product_id), "Unexpected product ID")
        # Operator setup in the owning DB: there is no inventory write endpoint.
        load_test.sql(self.args, self.database, f"""
            INSERT INTO inventory
                (product_id, sku, total_stock, reserved_stock, available_stock, created_at, updated_at)
            VALUES ('{product_id}', '{sku}', {stock}, 0, {stock}, NOW(), NOW());
        """)
        fetched = api(self.args, "GET", f"/api/products/{product_id}")
        require(any(v["sku"] == sku for v in fetched["variants"]), "Product preflight failed")
        return {"productId": product_id, "sku": sku, "initialStock": stock}

    def order_body(self, fixture):
        return {"items": [{"productId": fixture["productId"], "sku": fixture["sku"], "quantity": 1}],
                "shippingAddress": "Day 14 acceptance demo", "note": self.run_id}

    def create_order(self, fixture):
        return api(self.args, "POST", "/api/orders", self.order_body(fixture), self.token, 201)

    def settle(self, order, expected="CONFIRMED"):
        return eventually(lambda: api(self.args, "GET", f"/api/orders/{order['id']}", token=self.token),
                          lambda value: value["status"] == expected, self.args.timeout,
                          f"order {order['id']} becomes {expected}")

    def snapshot(self, fixture):
        return load_test.snapshot(self.args, self.database, fixture["sku"])

    def stock(self, fixture, created, confirmed, cancelled=0):
        final = self.snapshot(fixture)
        checks = load_test.stock_checks(fixture["initialStock"], final, created)
        checks.update({"allSettled": final["pendingOrders"] == 0,
                       "expectedConfirmed": final["confirmedOrders"] == confirmed,
                       "expectedCancelled": final["cancelledOrders"] == cancelled})
        require(all(checks.values()), f"Stock reconciliation failed for {fixture['sku']}: {checks}")
        return final

    def notification(self, order, failed=False):
        order_id = order["id"]
        markers = [f"Your order #{order_id} has been created."]
        if failed:
            markers += [f"Payment failed for order #{order_id}.",
                        f"Your order #{order_id} has been cancelled."]
        else:
            markers += [f"Payment succeeded for order #{order_id}."]
        eventually(lambda: self.compose("logs", "--no-color", "--since", self.started,
                                        "notification-service"),
                   lambda logs: all(marker in logs for marker in markers), self.args.timeout,
                   f"notification log markers for order {order_id}")
        return markers

    def queue(self):
        vhost = self.config["rabbitmq"]["environment"]["RABBITMQ_DEFAULT_VHOST"]
        output = self.compose("exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues",
                              "-p", vhost, "name", "messages_ready", "messages_unacknowledged",
                              "consumers", "--formatter=json")
        rows = json.loads(output[output.index("["):])
        return next(row for row in rows if row["name"] == "notification.queue")

    def register_login(self):
        self.admin_token = load_test.fixture_admin_token(self.args.base_url)
        email = self.run_id + "@example.test"
        password = secrets.token_urlsafe(24)
        api(self.args, "POST", "/api/auth/register", {
            "firstName": "Day14", "lastName": "Demo", "email": email, "password": password,
        }, expected=201)
        auth = api(self.args, "POST", "/api/auth/login", {"email": email, "password": password})
        self.token = auth["accessToken"]
        me = api(self.args, "GET", "/api/users/me", token=self.token)
        require(me["email"] == email, "JWT did not identify the fixture user")

    def end_to_end(self, failed=False):
        fixture = self.fixture("compensation" if failed else "e2e", price=10001 if failed else 1000)
        order = self.create_order(fixture)
        final = self.settle(order, "CANCELLED" if failed else "CONFIRMED")
        payment = api(self.args, "GET", f"/api/payments/{order['id']}", token=self.token)
        require(payment["status"] == ("FAILED" if failed else "SUCCESS"), "Unexpected payment status")
        return {**fixture, "orderId": order["id"], "orderStatus": final["status"],
                "paymentStatus": payment["status"],
                "stock": self.stock(fixture, 1, 0 if failed else 1, 1 if failed else 0),
                "notificationMarkers": self.notification(order, failed)}

    def flash_sale(self):
        fixture = self.fixture("flash", stock=10)
        # All 500 worker threads rendezvous before each sends exactly one request.
        # This measures a concurrent burst, not sustained requests per second.
        gate = threading.Barrier(500, timeout=60)
        body = self.order_body(fixture)

        def attempt(_):
            gate.wait()
            try:
                return request(self.args, "POST", "/api/orders", body, self.token)
            except (OSError, ValueError):
                return 0, {}

        started = time.monotonic()
        with ThreadPoolExecutor(max_workers=500) as pool:
            outcomes = list(pool.map(attempt, range(500)))
        counts = classify_outcomes(outcomes, fixture["sku"])
        require(counts == {"successful": 10, "insufficientStock": 490, "unexpected": 0},
                f"Flash sale HTTP counts failed: {counts}")
        orders = [body for status, body in outcomes if status == 201]
        require(len({order["id"] for order in orders}) == 10, "Duplicate order IDs")
        for order in orders:
            self.settle(order)
        return {**fixture, "requests": 500, "workers": 500, "http": counts,
                "elapsedSeconds": round(time.monotonic() - started, 3),
                "orderIds": [order["id"] for order in orders], "stock": self.stock(fixture, 10, 10)}

    def service_failure(self):
        fixture = self.fixture("failure")
        eventually(self.queue, lambda q: q["messages_ready"] == 0 and
                   q["messages_unacknowledged"] == 0, self.args.timeout, "notification queue drains")
        require(self.queue()["consumers"] > 0, "Notification must be running before failure demo")
        try:
            self.compose("stop", "notification-service")
            require(self.queue()["consumers"] == 0, "Notification consumer did not stop")
            order = self.create_order(fixture)
            final = self.settle(order)
            payment = api(self.args, "GET", f"/api/payments/{order['id']}", token=self.token)
            require(payment["status"] == "SUCCESS", "Payment failed while Notification was down")
            backlog = eventually(self.queue, lambda q: q["messages_ready"] >= 2,
                                 self.args.timeout, "durable notifications queued during outage")
            stock = self.stock(fixture, 1, 1)
        finally:
            # Recovery is attempted even if stop or subsequent assertions fail.
            self.compose("start", "notification-service")
        markers = self.notification(order)
        recovered = eventually(self.queue, lambda q: q["consumers"] > 0 and
                               q["messages_ready"] == 0 and q["messages_unacknowledged"] == 0,
                               self.args.timeout, "notification catches up after restart")
        return {**fixture, "orderId": order["id"], "orderStatusDuringOutage": final["status"],
                "paymentStatusDuringOutage": payment["status"], "queueDuringOutage": backlog,
                "queueAfterRecovery": recovered, "stock": stock, "notificationMarkers": markers}


def classify_outcomes(outcomes, sku):
    counts = {"successful": 0, "insufficientStock": 0, "unexpected": 0}
    for status, body in outcomes:
        if status == 201 and isinstance(body, dict) and body.get("id") is not None:
            counts["successful"] += 1
        elif status == 400 and isinstance(body, dict) and body.get("message") == f"Insufficient stock for SKU: {sku}":
            counts["insufficientStock"] += 1
        else:
            counts["unexpected"] += 1
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-file", default="docker-compose.yml")
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    require(urllib.parse.urlparse(args.base_url).hostname in ("localhost", "127.0.0.1", "::1"),
            "Run on the target Docker host using a loopback Gateway URL")
    demo = Demo(args)
    report = {"runId": demo.run_id, "measuredAt": demo.started, "passed": False, "scenarios": {}}
    RESULTS.mkdir(parents=True, exist_ok=True)
    try:
        demo.register_login()
        for name, scenario in (("endToEnd", demo.end_to_end),
                               ("compensation", lambda: demo.end_to_end(True)),
                               ("flashSale", demo.flash_sale), ("serviceFailure", demo.service_failure)):
            print(f"Running {name}...", flush=True)
            report["scenarios"][name] = scenario()
            print(f"PASS {name}", flush=True)
        report["passed"] = True
    except Exception as error:
        # No HTTP bodies, environment, login credentials or tokens in reports.
        report["failure"] = str(error) if isinstance(error, RuntimeError) else type(error).__name__
        print(f"FAIL: {report['failure']}", flush=True)
    finally:
        path = RESULTS / f"{demo.run_id}.json"
        path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {path.relative_to(ROOT)}", flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
