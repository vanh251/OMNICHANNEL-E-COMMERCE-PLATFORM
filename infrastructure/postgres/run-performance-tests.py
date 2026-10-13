"""Run Order/Payment concurrency tests against disposable PostgreSQL databases.

Requires Docker and a Java 17 toolchain. Uses the PostgreSQL image from Compose
in a disposable container with a random host port, avoiding native PostgreSQL.
Each service gets a fresh performance_test_* database, removed after the run.
Generated test credentials are passed through environment variables.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import secrets
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "infrastructure/k6/results"


def run_checked(command, *, operation, secret_values=(), **kwargs):
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", **kwargs)
    if result.returncode:
        detail = result.stderr.strip()
        for secret in secret_values:
            if secret:
                detail = detail.replace(secret, "***")
        detail = detail[-1200:]
        message = f"{operation} failed (exit {result.returncode})"
        raise RuntimeError(f"{message}: {detail}" if detail else message)
    return result.stdout


def start_postgres(image, credentials, environment):
    for attempt in range(1, 4):
        container = f"performance-postgres-{uuid.uuid4().hex[:12]}"
        try:
            run_checked(["docker", "run", "--rm", "-d", "--name", container,
                         "-e", "POSTGRES_USER", "-e", "POSTGRES_PASSWORD",
                         "-p", "127.0.0.1::5432", image],
                        operation=f"Start PostgreSQL container (attempt {attempt}/3)",
                        secret_values=(credentials["POSTGRES_PASSWORD"],), env=environment)
            return container
        except RuntimeError as error:
            subprocess.run(["docker", "rm", "-f", container], cwd=ROOT,
                           capture_output=True, check=False)
            if attempt == 3:
                raise
            print(f"{error}; retrying", flush=True)
            time.sleep(2 * attempt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-file", default="docker-compose.yml")
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--services", nargs="+", choices=["order-inventory-service", "payment-service"],
                        default=["order-inventory-service", "payment-service"])
    args = parser.parse_args()
    compose = ["docker", "compose", "--env-file", args.env_file, "-f", args.compose_file]
    config = json.loads(run_checked(compose + ["config", "--format", "json"],
                                    operation="Read Docker Compose configuration"))
    credentials = {"POSTGRES_USER": "postgres", "POSTGRES_PASSWORD": secrets.token_urlsafe(24)}
    environment = os.environ.copy()
    environment.update(credentials)
    container = start_postgres(config["services"]["postgres"]["image"],
                               credentials, environment)
    try:
        deadline = time.monotonic() + 60
        while True:
            # The official image starts a socket-only server for init scripts,
            # then stops it before starting the final TCP server.
            ready = subprocess.run(["docker", "exec", container, "pg_isready",
                                    "-h", "127.0.0.1", "-p", "5432",
                                    "-U", "postgres", "-d", "postgres"],
                                   capture_output=True)
            if ready.returncode == 0:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Disposable PostgreSQL did not accept TCP connections")
            time.sleep(0.5)
        port = run_checked(["docker", "port", container, "5432/tcp"],
                           operation="Read PostgreSQL host port").strip().rsplit(":", 1)[1]
        psql = ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", "postgres",
                "-X", "-v", "ON_ERROR_STOP=1"]
        return run_service_tests(psql, port, credentials, args.services)
    finally:
        stopped = subprocess.run(["docker", "stop", container], cwd=ROOT,
                                 capture_output=True, text=True, check=False)
        if stopped.returncode:
            print("Warning: disposable PostgreSQL container could not be stopped", flush=True)


def run_service_tests(psql, port, credentials, services):
    RESULTS.mkdir(parents=True, exist_ok=True)
    passed = True
    for service in services:
        suffix = "order" if service == "order-inventory-service" else "payment"
        database = f"performance_test_{suffix}_{uuid.uuid4().hex[:12]}"
        run_checked(psql, operation="Create performance test database",
                    input=f'CREATE DATABASE "{database}";')
        try:
            environment = os.environ.copy()
            environment.update({
                "PERFORMANCE_TEST_POSTGRES_URL": f"jdbc:postgresql://127.0.0.1:{port}/{database}",
                "PERFORMANCE_TEST_POSTGRES_USER": credentials["POSTGRES_USER"],
                "PERFORMANCE_TEST_POSTGRES_PASSWORD": credentials["POSTGRES_PASSWORD"],
            })
            wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
            command = [str(ROOT / service / wrapper), "test", "bootJar", "--rerun-tasks",
                       "--no-daemon", "--console=plain"]
            if os.name != "nt":
                command.insert(0, "sh")
            result = subprocess.run(command, cwd=ROOT / service, env=environment,
                                    capture_output=True, text=True, encoding="utf-8", errors="replace")
            log = RESULTS / f"{database}.log"
            log.write_text(result.stdout + result.stderr, encoding="utf-8")
            counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
            for path in (ROOT / service / "build/test-results/test").glob("TEST-*.xml"):
                suite = ET.parse(path).getroot()
                for key in counts:
                    counts[key] += int(suite.attrib.get(key, 0))
            success = result.returncode == 0
            print(f"{service}: {'PASS' if success else 'FAIL'} {counts}; log: {log.relative_to(ROOT)}", flush=True)
            passed = passed and success
        finally:
            run_checked(psql, operation="Drop performance test database",
                        input=f'DROP DATABASE "{database}" WITH (FORCE);')
    return 0 if passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, ValueError) as exception:
        print(f"PostgreSQL performance tests failed: {exception}")
        raise SystemExit(1)
