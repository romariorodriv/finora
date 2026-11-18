"""Cálculos financieros deterministas: escenarios, no aprobación de crédito."""
from decimal import Decimal, ROUND_HALF_UP

def housing_budget(income: Decimal, essential: Decimal, debt: Decimal, savings: Decimal,
                   annual_rate: Decimal = Decimal("0.10"), years: int = 20) -> dict:
    if any(v < 0 for v in (income, essential, debt, savings)) or income <= 0:
        raise ValueError("Los importes deben ser válidos y los ingresos positivos")
    if annual_rate <= 0 or years < 1 or years > 35:
        raise ValueError("Tasa o plazo fuera de rango")
    # Conservative *illustrative* planning caps, NOT lending criteria.
    cash_available = max(Decimal(0), income - essential - debt)
    monthly_budget = max(Decimal(0), min(income * Decimal("0.30") - debt, cash_available * Decimal("0.70")))
    monthly_rate = (Decimal(1) + annual_rate) ** (Decimal(1) / Decimal(12)) - 1
    months = years * 12
    loan = monthly_budget * (1 - (1 + monthly_rate) ** (-months)) / monthly_rate
    # Reserve 5% of cash savings for purchase costs; exact fees vary.
    available_down = savings * Decimal("0.95")
    # Illustration requiring 20% down, not a bank requirement.
    total_price = min(loan + available_down, available_down / Decimal("0.20")) if available_down else Decimal(0)
    def money(value: Decimal) -> float:
        return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
    return {
        "currency": "PEN",
        "monthly_budget": money(monthly_budget),
        "estimated_max_loan": money(loan),
        "estimated_max_price": money(total_price),
        "available_down_payment": money(available_down),
        "annual_effective_rate_assumed": float(annual_rate),
        "years_assumed": years,
        "assumptions": [
            "Escenario ilustrativo con TEA del 10%, no es una tasa bancaria vigente ni una TCEA.",
            "Se limita la cuota a 30% del ingreso menos otras cuotas y a 70% del excedente mensual.",
            "Se reserva 5% de ahorros para costos y se presupone una inicial mínima de 20%.",
            "No incluye seguros hipotecarios, tasación, gastos registrales ni otros costos efectivos.",
            "No constituye preaprobación ni evaluación crediticia."
        ],
    }
