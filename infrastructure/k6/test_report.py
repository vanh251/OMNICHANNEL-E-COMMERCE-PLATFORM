"""Verify stock reconciliation rejects misleading load-test results."""

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("load_test", Path(__file__).with_name("run-load-test.py"))
load_test = importlib.util.module_from_spec(spec)
spec.loader.exec_module(load_test)


class StockReportTest(unittest.TestCase):
    def snapshot(self, **changes):
        result = {"totalStock": 7, "reservedStock": 2, "availableStock": 5,
                  "createdOrders": 6, "pendingOrders": 2, "confirmedOrders": 3,
                  "cancelledOrders": 1}
        result.update(changes)
        return result

    def test_pending_reservations_and_compensation_reconcile(self):
        self.assertTrue(all(load_test.stock_checks(10, self.snapshot(), 6).values()))

    def test_negative_stock_is_rejected(self):
        checks = load_test.stock_checks(10, self.snapshot(availableStock=-1), 6)
        self.assertFalse(checks["nonNegative"])
        self.assertFalse(checks["stockEquation"])

    def test_overselling_detected_even_with_nonnegative_inventory(self):
        checks = load_test.stock_checks(10, self.snapshot(confirmedOrders=9), 6)
        self.assertFalse(checks["noOverselling"])
        self.assertFalse(checks["deductionMatchesConfirmed"])

    def test_missing_reservation_is_rejected(self):
        checks = load_test.stock_checks(10, self.snapshot(pendingOrders=3), 6)
        self.assertFalse(checks["reservationMatchesPending"])

    def test_lost_http_response_is_not_counted_as_matching(self):
        checks = load_test.stock_checks(10, self.snapshot(), 5)
        self.assertFalse(checks["httpMatchesDatabase"])

    def test_idle_stack_does_not_delay_next_load(self):
        with patch.object(load_test, "sql", return_value='{"pendingOrders":0,"pendingEvents":0}'), \
                patch.object(load_test.time, "sleep") as sleep:
            load_test.wait_for_idle(SimpleNamespace(settle_timeout=0), "order_test")
            sleep.assert_not_called()

    def test_pending_saga_blocks_next_load(self):
        with patch.object(load_test, "sql", return_value='{"pendingOrders":1,"pendingEvents":0}'):
            with self.assertRaisesRegex(RuntimeError, "no new load was started"):
                load_test.wait_for_idle(SimpleNamespace(settle_timeout=0), "order_test")

    def test_unpublished_events_block_next_load(self):
        with patch.object(load_test, "sql", return_value='{"pendingOrders":0,"pendingEvents":1}'):
            with self.assertRaises(RuntimeError):
                load_test.wait_for_idle(SimpleNamespace(settle_timeout=0), "order_test")

    def test_load_waits_for_backlog_to_drain(self):
        with patch.object(load_test, "sql", side_effect=[
                '{"pendingOrders":1,"pendingEvents":1}', '{"pendingOrders":0,"pendingEvents":0}']), \
                patch.object(load_test.time, "sleep") as sleep:
            load_test.wait_for_idle(SimpleNamespace(settle_timeout=30), "order_test")
            sleep.assert_called_once_with(2)


if __name__ == "__main__":
    unittest.main()
