import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location("demo", Path(__file__).with_name("run-demo.py"))
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


class AcceptanceChecksTest(unittest.TestCase):
    def test_real_payos_mode_is_rejected_before_demo_creates_fixtures(self):
        config = {"services": {"payment-service": {"environment": {"PAYMENT_PROVIDER": "payos"}}}}
        with patch.object(demo.Demo, "compose", return_value=demo.json.dumps(config)):
            with self.assertRaisesRegex(RuntimeError, "PAYMENT_PROVIDER=mock"):
                demo.Demo(Mock())

    def test_catalog_fixture_uses_admin_token(self):
        runner = object.__new__(demo.Demo)
        runner.args = Mock()
        runner.run_id = "day14-test"
        runner.database = "order_db"
        runner.admin_token = "admin-access-token"
        runner.snapshot = Mock(return_value={"availableStock": 1})
        with patch.object(demo, "api", side_effect=[
                {"id": "category"}, {"id": "product"}]) as api, \
                patch.object(demo, "eventually", return_value={
                    "active": True, "variants": [{"sku": "DAY14-TEST-E2E"}]}):
            runner.fixture("e2e")
        self.assertEqual("admin-access-token", api.call_args_list[0].args[4])
        self.assertEqual("admin-access-token", api.call_args_list[1].args[4])

    def test_only_exact_stock_error_counts_as_expected_rejection(self):
        outcomes = [(201, {"id": 1}), (400, {"message": "Insufficient stock for SKU: TEST"}),
                    (400, {"message": "Invalid product"}), (401, {}), (500, {}), (0, {}),
                    (201, {}), (400, {"message": "Insufficient stock for SKU: OTHER"})]
        self.assertEqual({"successful": 1, "insufficientStock": 1, "unexpected": 6},
                         demo.classify_outcomes(outcomes, "TEST"))

    def test_failure_demo_restarts_notification_when_order_fails(self):
        runner = object.__new__(demo.Demo)
        runner.args = Mock(timeout=1)
        runner.fixture = Mock(return_value={})
        runner.queue = Mock(side_effect=[{"messages_ready": 0, "messages_unacknowledged": 0},
                                         {"consumers": 1}, {"consumers": 0}])
        runner.compose = Mock()
        runner.create_order = Mock(side_effect=RuntimeError("order failed"))
        with self.assertRaisesRegex(RuntimeError, "order failed"):
            runner.service_failure()
        self.assertEqual([(("stop", "notification-service"),), (("start", "notification-service"),)],
                         runner.compose.call_args_list)

    def test_failure_demo_does_not_start_an_already_stopped_service(self):
        runner = object.__new__(demo.Demo)
        runner.args = Mock(timeout=1)
        runner.fixture = Mock(return_value={})
        runner.queue = Mock(return_value={"messages_ready": 0, "messages_unacknowledged": 0, "consumers": 0})
        runner.compose = Mock()
        with self.assertRaisesRegex(RuntimeError, "must be running"):
            runner.service_failure()
        runner.compose.assert_not_called()

    @patch.object(demo.time, "sleep")
    def test_eventual_check_does_not_accept_pending_state(self, sleep):
        probe = Mock(side_effect=["PENDING", "CONFIRMED"])
        self.assertEqual("CONFIRMED", demo.eventually(probe, lambda s: s == "CONFIRMED", 10, "Saga"))
        self.assertEqual(2, probe.call_count)


if __name__ == "__main__":
    unittest.main()
