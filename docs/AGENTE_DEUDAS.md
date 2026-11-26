# Beta local: agente de deudas

No modifica Gmail ni OAuth. Recupera la arquitectura del agente vivienda. Análisis bajo demanda; no incluye persistencia, programación, alertas ni comparación verificada. No enviar datos personales a Tavily: la búsqueda es genérica.

Windows CMD, desde raíz:

```
py -3 -m venv .venv
.venv\Scripts\activate.bat
python -m pip install -r agent_service/requirements.txt
python -m unittest agent_service.test_finance -v
set SARELA_AGENT_INTERNAL_KEY=local-debt-test-only
python -m uvicorn agent_service.main:app --host 127.0.0.1 --port 8001
```

En terminal backend, conservar variables locales existentes y añadir SARELA_AGENT_URL=http://127.0.0.1:8001 y SARELA_AGENT_INTERNAL_KEY con el mismo valor. Iniciar Java y Angular como antes. Opcional: TAVILY_API_KEY en Python antes de iniciar. No publicar claves ni puerto Python. Antes de AWS: pruebas de integración, privacidad, límites de búsqueda, SEO estático, memoria, scheduler y verificación de fuentes.
