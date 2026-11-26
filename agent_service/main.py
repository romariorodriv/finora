import os
import secrets
from decimal import Decimal
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from .finance import debt_cost
from .search import search_debt_options

app = FastAPI(title="Sarela Agents - internal", docs_url=None, redoc_url=None, openapi_url=None)

class DebtRequest(BaseModel):
    balance: Decimal = Field(ge=1, le=1000000)
    tea: Decimal = Field(ge=0, le=300)
    months: int = Field(ge=1, le=120, strict=True)

@app.get("/health")
def health():
    return {"status":"ok"}

@app.post("/internal/v1/debts/analyze")
def analyze(body: DebtRequest, x_sarela_agent_key: str | None = Header(default=None)):
    key = os.getenv("SARELA_AGENT_INTERNAL_KEY", "")
    if not key or not x_sarela_agent_key or not secrets.compare_digest(key, x_sarela_agent_key):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return {**debt_cost(body.balance, body.tea, body.months), "search":search_debt_options()}
