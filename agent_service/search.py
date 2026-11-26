"""Búsqueda mediante API oficial externa, no scraping indiscriminado."""
import json
import os
import urllib.request
import urllib.error
from urllib.parse import urlparse

API_URL = "https://api.tavily.com/search"

def search_debt_options() -> dict:
    key = os.getenv("TAVILY_API_KEY", "").strip()
    if not key:
        return {"status": "not_configured", "results": [],
                "message": "La búsqueda de inmuebles aún no está configurada. No se inventarán ofertas."}
    query = "Perú compra de deuda bancos condiciones TCEA comisiones sitio oficial"
    payload = json.dumps({"query": query, "search_depth": "basic", "max_results": 5,
                          "include_answer": False, "include_raw_content": False}).encode("utf-8")
    request = urllib.request.Request(API_URL, data=payload, method="POST",
       headers={"Content-Type":"application/json", "Authorization":f"Bearer {key}"})
    try:
        with urllib.request.urlopen(request, timeout=18) as response:
            body = response.read(120_000)
        data = json.loads(body)
    except (urllib.error.URLError, TimeoutError, ValueError, OSError):
        return {"status": "unavailable", "results": [],
                "message": "No se pudo completar la búsqueda. Intenta nuevamente."}
    sources = []
    for entry in data.get("results", [])[:5]:
        url = entry.get("url", "")
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
            continue
        sources.append({"title": str(entry.get("title", ""))[:180],
                        "url": url, "excerpt": str(entry.get("content", ""))[:420],
                        "verified": False})
    return {"status": "preliminary" if sources else "no_results", "results": sources,
            "message": "Enlaces preliminares: comprueba TCEA, vigencia, elegibilidad y condiciones directamente con la entidad. No son recomendaciones verificadas."}
