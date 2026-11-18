import os
import unittest
from decimal import Decimal
from unittest.mock import patch
from fastapi.testclient import TestClient
from .finance import housing_budget
from .main import app

class HousingTests(unittest.TestCase):
    def test_budget_positive_with_savings(self):
        result = housing_budget(Decimal(5000), Decimal(2000), Decimal(200), Decimal(60000))
        self.assertGreater(result["estimated_max_price"], 0)
        self.assertLessEqual(result["monthly_budget"], 1300)
        self.assertEqual(result["annual_effective_rate_assumed"], .10)
    def test_budget_zero_when_debt_exceeds_income(self):
        result = housing_budget(Decimal(2000), Decimal(2000), Decimal(1500), Decimal(0))
        self.assertEqual(result["estimated_max_price"], 0)
    def test_rejects_negative_values(self):
        with self.assertRaises(ValueError):
            housing_budget(Decimal(5000), Decimal(-2), Decimal(0), Decimal(0))
    def test_api_requires_internal_key(self):
        client = TestClient(app)
        with patch.dict(os.environ, {"SARELA_AGENT_INTERNAL_KEY":"test-secret"}, clear=False):
            response = client.post("/internal/v1/housing/analyze", json={
              "income":5000,"essential_expenses":2000,"monthly_debts":0,
              "savings":40000,"districts":["San Miguel"]})
            self.assertEqual(response.status_code, 401)
    def test_api_does_not_fabricate_homes_without_search_key(self):
        client = TestClient(app)
        with patch.dict(os.environ, {"SARELA_AGENT_INTERNAL_KEY":"test-secret", "TAVILY_API_KEY":""}):
            response = client.post("/internal/v1/housing/analyze",
                headers={"X-Sarela-Agent-Key":"test-secret"},json={
                  "income":5000,"essential_expenses":2000,"monthly_debts":0,
                  "savings":40000,"districts":["San Miguel"]})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["search"]["status"],"not_configured")
        self.assertEqual(response.json()["search"]["results"],[])
    def test_validation_rejects_injection_district(self):
        response = TestClient(app).post("/internal/v1/housing/analyze",
            headers={"X-Sarela-Agent-Key":"test-secret"}, json={
              "income":5000,"essential_expenses":0,"monthly_debts":0,"savings":0,
              "districts":["http://internal"]})
        self.assertEqual(response.status_code,422)

if __name__ == "__main__":
    unittest.main()
