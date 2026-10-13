"""Check a payOS channel, optionally smoke-test a link or register its HTTPS webhook.

Reads credentials from environment or the ignored .env file. Never prints keys,
bank details, request bodies or provider error descriptions.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
API = "https://api-merchant.payos.vn"


def environment(path):
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip('"').strip("'")
    values.update(os.environ)
    for key in ("PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY"):
        if not values.get(key):
            raise RuntimeError(f"{key} is required")
    return values


def signature(data, key):
    text = "&".join(f"{name}={data[name]}" for name in sorted(data))
    return hmac.new(key.encode(), text.encode(), hashlib.sha256).hexdigest()


def request(env, method, path, body=None):
    req = urllib.request.Request(API + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={"x-client-id": env["PAYOS_CLIENT_ID"],
                                          "x-api-key": env["PAYOS_API_KEY"],
                                          "User-Agent": "payos-java/2.0.1",
                                          "Accept": "application/json",
                                          "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.load(error)
        except (ValueError, UnicodeError):
            return error.code, {}


def checked(env, method, path, body=None):
    status, response = request(env, method, path, body)
    if status != 200 or response.get("code") != "00":
        raise RuntimeError(f"payOS request rejected (HTTP {status}, code {response.get('code')})")
    return response


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=".env")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--smoke-link", action="store_true",
                       help="Create a 2000 VND link and immediately cancel it; does not initiate a transfer")
    modes.add_argument("--confirm-webhook", action="store_true",
                       help="Register PAYOS_WEBHOOK_URL after the updated endpoint is publicly deployed")
    args = parser.parse_args()
    env = environment(ROOT / args.env_file)
    if args.confirm_webhook:
        url = env.get("PAYOS_WEBHOOK_URL", "")
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme != "https" or not parsed.hostname:
            raise RuntimeError("PAYOS_WEBHOOK_URL must be an HTTPS URL")
        checked(env, "POST", "/confirm-webhook", {"webhookUrl": url})
        print("Webhook confirmed:", url)
    elif args.smoke_link:
        code = secrets.randbelow((1 << 53) - 1) + 1
        body = {"orderCode": code, "amount": 2000, "description": "TEST",
                "cancelUrl": env["PAYOS_CANCEL_URL"], "returnUrl": env["PAYOS_RETURN_URL"]}
        body["signature"] = signature(body, env["PAYOS_CHECKSUM_KEY"])
        body["expiredAt"] = int(time.time()) + 300
        print("Smoke orderCode:", code, flush=True)
        failure = None
        try:
            response = checked(env, "POST", "/v2/payment-requests", body)
            data = response["data"]
            expected = signature(data, env["PAYOS_CHECKSUM_KEY"])
            if not hmac.compare_digest(expected, response.get("signature", "")):
                raise RuntimeError("payOS response signature verification failed")
            print("Signed link created; orderCode:", code, "status:", data.get("status"))
        except Exception as error:
            failure = error
        try:
            status, cancelled = request(env, "POST", f"/v2/payment-requests/{code}/cancel",
                                        {"cancellationReason": "Integration smoke test"})
            if status == 200 and cancelled.get("code") == "00" and cancelled["data"].get("status") == "CANCELLED":
                print("Cleanup status: CANCELLED")
            elif status == 200 and cancelled.get("code") in ("101", "231"):
                print("Cleanup: no payment link exists")
            else:
                raise RuntimeError(f"Cleanup requires checking orderCode {code} (HTTP {status}, code {cancelled.get('code')})")
        except urllib.error.URLError as error:
            raise RuntimeError(f"Unable to verify cleanup for orderCode {code}: {type(error.reason).__name__}") from None
        if failure is not None:
            raise failure
    else:
        status, response = request(env, "GET", "/v2/payment-requests/9007199254740990")
        if status == 200 and response.get("code") in ("101", "231"):
            print("Channel authentication accepted; probe order does not exist.")
        elif status == 200 and response.get("code") == "00":
            print("Channel authentication accepted.")
        else:
            raise RuntimeError(f"Channel check rejected (HTTP {status}, code {response.get('code')})")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.URLError as error:
        print("Network error:", type(error.reason).__name__)
        raise SystemExit(1)
    except Exception as error:
        print(str(error) if isinstance(error, RuntimeError) else type(error).__name__)
        raise SystemExit(1)
