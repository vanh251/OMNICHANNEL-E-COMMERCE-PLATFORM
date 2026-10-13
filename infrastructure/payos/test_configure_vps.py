import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("configure_vps", Path(__file__).with_name("configure-vps.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ConfigurationTest(unittest.TestCase):
    def values(self):
        return {"PAYOS_CLIENT_ID": "test-client", "PAYOS_API_KEY": "test-api",
                "PAYOS_CHECKSUM_KEY": "test-checksum", "PAYOS_RETURN_URL": "https://example.test/return",
                "PAYOS_CANCEL_URL": "https://example.test/cancel",
                "PAYOS_WEBHOOK_URL": "https://example.test/webhook", "IMAGE_TAG": "sha-abcdef1"}

    def test_preserves_database_settings_and_replaces_existing_payos_values_without_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text("POSTGRES_PASSWORD=keep-existing\nPAYMENT_PROVIDER=mock\nPAYOS_API_KEY=old\n", encoding="utf-8")
            module.configure(path, self.values())
            module.configure(path, self.values())
            result = path.read_text(encoding="utf-8")
            self.assertIn("POSTGRES_PASSWORD=keep-existing", result)
            self.assertIn("PAYMENT_PROVIDER='payos'", result)
            self.assertEqual(1, result.count("PAYOS_API_KEY="))
            self.assertFalse(list(path.parent.glob(".env-payos-*")))

    def test_missing_secret_cannot_partially_modify_vps_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text("POSTGRES_PASSWORD=keep-existing\n", encoding="utf-8")
            settings = self.values()
            settings.pop("PAYOS_CHECKSUM_KEY")
            with self.assertRaisesRegex(RuntimeError, "PAYOS_CHECKSUM_KEY"):
                module.configure(path, settings)
            self.assertEqual("POSTGRES_PASSWORD=keep-existing\n", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
