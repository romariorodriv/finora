import unittest
from decimal import Decimal
from .finance import debt_cost

class DebtCostTest(unittest.TestCase):
    def test_zero_rate(self):
        self.assertEqual(debt_cost(Decimal("1200"), Decimal("0"), 12), {"monthly_payment":100.0,"total_cost":1200.0})
    def test_positive_rate(self):
        result=debt_cost(Decimal("10000"),Decimal("30"),24)
        self.assertGreater(result["total_cost"],10000)
    def test_invalid(self):
        with self.assertRaises(ValueError):
            debt_cost(Decimal("100"),Decimal("10"),0)
