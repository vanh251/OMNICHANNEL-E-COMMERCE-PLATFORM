"""Write payOS settings supplied by Actions into the VPS .env without logging secrets."""
import argparse
import os
from pathlib import Path
import tempfile


def configure(path, environment):
    if not path.is_file():
        raise RuntimeError("VPS .env must exist before configuring payOS")
    values = {"PAYMENT_PROVIDER": "payos"}
    for key in ("PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY",
                "PAYOS_RETURN_URL", "PAYOS_CANCEL_URL", "PAYOS_WEBHOOK_URL", "IMAGE_TAG"):
        value = environment.get(key, "")
        if not value or any(character in value for character in "\r\n'"):
            raise RuntimeError(f"{key} is missing or invalid")
        values[key] = value
    lines = path.read_text(encoding="utf-8").splitlines()
    remaining = dict(values)
    for index, line in enumerate(lines):
        key = line.split("=", 1)[0].strip()
        if key in values:
            lines[index] = f"{key}='{values[key]}'"
            remaining.pop(key, None)
    lines.extend(f"{key}='{value}'" for key, value in remaining.items())
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=".env-payos-", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write("\n".join(lines) + "\n")
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=".env")
    args = parser.parse_args()
    try:
        configure(Path(args.env_file), os.environ)
        print("payOS configuration updated.")
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
