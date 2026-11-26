from decimal import Decimal, ROUND_HALF_UP, localcontext

def debt_cost(balance: Decimal, tea: Decimal, months: int) -> dict:
    if not balance.is_finite() or not tea.is_finite() or not 1 <= balance <= 1000000 or not 0 <= tea <= 300 or not 1 <= months <= 120:
        raise ValueError("Condiciones inválidas")
    with localcontext() as ctx:
        ctx.prec = 40
        rate = (1 + tea / 100) ** (Decimal(1) / 12) - 1
        payment = balance / months if rate == 0 else balance * rate / (1 - (1 + rate) ** (-months))
        cents = Decimal("0.01")
        return {"monthly_payment": float(payment.quantize(cents, rounding=ROUND_HALF_UP)),
                "total_cost": float((payment * months).quantize(cents, rounding=ROUND_HALF_UP))}
