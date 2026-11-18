"""API interna de Sarela Vivienda; el navegador NO debe llamarla directamente."""
import os
import secrets
from decimal import Decimal
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, field_validator
from typing import Literal
from .finance import housing_budget
from .search import search_homes

app = FastAPI(title="Sarela Agents - internal", docs_url=None, redoc_url=None, openapi_url=None)

class HousingRequest(BaseModel):
    income: float = Field(gt=0, le=1_000_000)
    essential_expenses: float = Field(ge=0, le=1_000_000)
    monthly_debts: float = Field(ge=0, le=1_000_000)
    savings: float = Field(ge=0, le=20_000_000)
    districts: list[str] = Field(min_length=1, max_length=3)

    @field_validator("districts")
    @classmethod
    def validate_districts(cls, values: list[str]) -> list[str]:
        if any(not (2 <= len(item.strip()) <= 60) or not all(c.isalpha() or c.isspace() or c == '-' for c in item)
               for item in values):
            raise ValueError("Distritos inválidos")
        return [item.strip() for item in values]

@app.get("/health")
def health():
    return {"status":"ok"}

@app.post("/internal/v1/housing/analyze")
def analyze(body: HousingRequest, x_sarela_agent_key: str | None = Header(default=None)):
    key = os.getenv("SARELA_AGENT_INTERNAL_KEY", "")
    if not key or not x_sarela_agent_key or not secrets.compare_digest(key, x_sarela_agent_key):
        raise HTTPException(status_code=401, detail="Unauthorized")
    budget = housing_budget(*(Decimal(str(v)) for v in (
        body.income, body.essential_expenses, body.monthly_debts, body.savings)))
    if budget["estimated_max_price"] <= 0:
        search = {"status":"skipped","results":[],
                  "message":"No se identificó capacidad de compra en el escenario estimado; revisa tus obligaciones y ahorros."}
    else:
        search = search_homes(body.districts, budget["estimated_max_price"])
    return {"agent":"housing","status":"completed","budget":budget,"search":search,
            "note":"No se revisó una oferta hipotecaria vigente. Los enlaces encontrados requieren verificación humana."}
