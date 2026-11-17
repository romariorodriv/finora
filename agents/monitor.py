"""Primer trabajador automático de Sarela: vigila cambios en fuentes públicas autorizadas.

No interpreta ofertas como contratos equivalentes ni recomienda ahorros.
Ejecutar: python -m agents.monitor --state agents/state.json
"""
from __future__ import annotations

import argparse
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from datetime import datetime, timezone
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from urllib.robotparser import RobotFileParser

USER_AGENT = "SarelaResearchBot/0.1 (+https://sarelaapp.com/)"
MAX_BYTES = 2_000_000
CURRENCY_PATTERN = re.compile(r"(?:S/\s*|S\s*/\s*)(\d{1,4}(?:[.,]\d{2})?)", re.I)


class VisibleText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript", "svg"):
            self.skip += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript", "svg") and self.skip:
            self.skip -= 1

    def handle_data(self, value):
        if not self.skip and value.strip():
            self.parts.append(value.strip())


def extract_evidence(html: str) -> dict:
    parser = VisibleText()
    parser.feed(html)
    visible = " ".join(parser.parts)
    visible = " ".join(visible.split())
    # These are unassociated price mentions, NOT verified plan prices.
    mentions = list(dict.fromkeys(m.group(0) for m in CURRENCY_PATTERN.finditer(visible)))
    return {
        "content_sha256": hashlib.sha256(visible.encode("utf-8")).hexdigest(),
        "price_mentions_unverified": mentions[:30],
        "visible_characters": len(visible),
    }


def load_sources(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    sources = payload["sources"]
    if not sources or len(sources) > 10:
        raise ValueError("Se requieren de 1 a 10 fuentes.")
    ids = set()
    for source in sources:
        parsed = urlparse(source["url"])
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("Solo se admiten fuentes HTTPS públicas.")
        if parsed.hostname not in {"www.claro.com.pe", "www.entel.pe"}:
            raise ValueError("Dominio no autorizado: " + parsed.hostname)
        if source["id"] in ids or not re.fullmatch(r"[a-z0-9_]{2,40}", source["id"]):
            raise ValueError("Identificador de fuente inválido o repetido.")
        ids.add(source["id"])
    return sources


def fetch_source(url: str) -> str:
    parsed = urlparse(url)
    # Check site crawling policy, and fail closed if inaccessible.
    robots_url = f"{parsed.scheme}://{parsed.netloc}/robots.txt"
    robots = RobotFileParser()
    robots.set_url(robots_url)
    robots.read()
    if not robots.can_fetch(USER_AGENT, url):
        raise PermissionError("robots.txt no permite esta consulta automatizada")
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
    with urlopen(request, timeout=15) as response:
        final = urlparse(response.geturl())
        if final.scheme != "https" or final.hostname != parsed.hostname:
            raise PermissionError("La fuente redirigió fuera de su dominio autorizado")
        if "text/html" not in response.headers.get("Content-Type", ""):
            raise ValueError("La respuesta no contiene HTML")
        raw = response.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise ValueError("La página supera el tamaño permitido")
        return raw.decode(response.headers.get_content_charset() or "utf-8", errors="replace")


def check_once(sources: list[dict], previous: dict, fetcher=fetch_source) -> tuple[dict, list[dict]]:
    current = dict(previous)
    events = []
    checked_at = datetime.now(timezone.utc).isoformat()
    for source in sources:
        source_id = source["id"]
        try:
            evidence = extract_evidence(fetcher(source["url"]))
            old_hash = previous.get(source_id, {}).get("content_sha256")
            changed = old_hash is not None and old_hash != evidence["content_sha256"]
            current[source_id] = {
                **evidence, "url": source["url"], "label": source["label"],
                "checked_at_utc": checked_at,
            }
            events.append({
                "source": source_id, "status": "changed" if changed else ("baseline" if old_hash is None else "unchanged"),
                "url": source["url"], "checked_at_utc": checked_at,
                "price_mentions_unverified": evidence["price_mentions_unverified"],
            })
        except Exception as exc:
            # Preserve the last good evidence; do not pretend a failed check succeeded.
            events.append({"source": source_id, "status": "error", "reason": str(exc), "checked_at_utc": checked_at})
    return current, events


def main():
    parser = argparse.ArgumentParser(description="Investigador de fuentes públicas de Sarela")
    parser.add_argument("--sources", type=Path, default=Path(__file__).with_name("sources.json"))
    parser.add_argument("--state", type=Path, default=Path(__file__).with_name("state.json"))
    args = parser.parse_args()
    sources = load_sources(args.sources)
    previous = json.loads(args.state.read_text(encoding="utf-8")) if args.state.exists() else {}
    current, events = check_once(sources, previous)
    args.state.parent.mkdir(parents=True, exist_ok=True)
    temp = args.state.with_suffix(".tmp")
    temp.write_text(json.dumps(current, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(args.state)
    print(json.dumps({"events": events}, ensure_ascii=False, indent=2))
    if all(event["status"] == "error" for event in events):
        raise SystemExit(2)


if __name__ == "__main__":
    main()
