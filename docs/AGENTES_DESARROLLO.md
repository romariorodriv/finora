# Sarela — manual técnico del motor de agentes (primer entregable)

> **Estado**: beta técnica en rama `feature/sarela-agent-hub-v1`. No desplegada ni validada con anuncios inmobiliarios reales. En producción, Gmail y la versión actual de Sarela siguen sin cambios.

## 1. Visión y límites

Sarela será un catálogo de agentes orientados a cuidar el dinero del usuario.

- **Vivienda**: primer módulo implementado. Recibe ingreso, gastos esenciales, deudas, ahorros y distrito; calcula un escenario hipotecario ilustrativo y, **solo si hay clave de búsqueda**, consulta enlaces públicos candidatos.
- **Auto**: tarjeta de próximo lanzamiento. No existe todavía lógica de investigación de vehículos.
- **Gastos**: conserva el dashboard, las transacciones y las herramientas actuales; conexión Gmail voluntaria y separada del login.
- **Deudas**: entrada informativa; NO presume balances ni tasas de deuda a partir de correos de consumo. Se construirá solo con datos suficientes y consentimiento.

Un **agente** no es solo un formulario con IA: debe perseguir un objetivo, usar herramientas y contrastar resultados. El primer entregable incorpora la estructura y herramientas iniciales, pero **todavía no es un agente autónomo de investigación completo**. No comercializarlo como si ya verificara propiedades, bancos o disponibilidades.

## 2. Arquitectura (petición de Vivienda)

```text
Angular (frontend/src/app/agents/)
   -> POST /api/v1/agents/housing/analyze (cookie de sesión)
Spring Boot (backend/.../agents/HousingAgentController.java)
   -> cabecera privada X-Sarela-Agent-Key
Python + FastAPI (agent_service/main.py)
   -> finance.py (cálculos deterministas)
   -> search.py (API externa de búsqueda, si está configurada)
   -> JSON {budget, search, note} -> Spring -> Angular
```

**No exponer el puerto Python a internet**, ni `SARELA_AGENT_INTERNAL_KEY` al navegador. Spring Boot conserva las sesiones y permisos. Los tokens de Gmail permanecen en su backend. No usar datos financieros reales en pruebas públicas.

## 3. Estructura de carpetas

- `agent_service/main.py`: modelos de entrada, validación, autenticación interna y endpoint.
- `agent_service/finance.py`: reglas matemáticas sin llamadas a LLM, fáciles de reproducir y testear.
- `agent_service/search.py`: herramienta de búsqueda que devuelve **enlaces preliminares**, nunca avisos confirmados.
- `agent_service/test_finance.py`: pruebas unitarias del cálculo y API.
- `agent_service/requirements.txt`: dependencias Python.
- `agent_service/Dockerfile`: imagen contenedorizada.
- `frontend/src/app/agents/agent-hub.component.ts`: catálogo de agentes.
- `frontend/src/app/agents/housing-agent.component.ts`: formulario, envío y resultado.
- `frontend/src/app/core/services/api.service.ts`: cliente HTTP de Vivienda.
- `backend/src/main/java/com/finora/app/agents/HousingAgentController.java`: pasarela autenticada.
- `.github/workflows/sarela-agents.yml`: pruebas automáticas Python/Java y compilación Angular.

### Cómo leerlo en orden (desde cero)

1. Lee `housing-agent.component.ts`, comenzando por `analyze()`, para entender los datos enviados.
2. Sigue `ApiService.analyzeHousing()` y la ruta REST de Java.
3. Abre `main.py` y localiza el modelo `HousingRequest` y la función `analyze`.
4. Revisa `finance.py` y prueba un escenario a mano; luego `search.py` y sus fuentes.
5. Sigue la respuesta JSON de vuelta a `housing-agent.component.ts`.

## Acceso simplificado para desarrollo local (sin Google OAuth)

El login de Google se conserva. Para probar las pantallas en tu PC existe un **modo demo explícito**, sin contraseña, exclusivamente en el perfil `local` y desde `localhost`.

Desde una terminal CMD abierta en `backend`:

```cmd
set "JWT_SECRET=clave-larga-para-pruebas-locales-no-produccion"
set "DB_URL=jdbc:h2:file:./data/sarela-agentes-test;MODE=PostgreSQL;AUTO_SERVER=TRUE"
set "SARELA_DEV_LOGIN_ENABLED=true"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Abre Angular en `http://localhost:4200`, presiona el botón de acceso y selecciona **Entrar en modo demo (local)**. Se crea una cuenta ficticia `demo@sarela.invalid` con permisos normales (`USER`) y una cookie de sesión que gestiona Java, igual que con Google. Puedes entrar en Vivienda sin conectar Gmail.

**Protecciones**: el endpoint solo existe si se cumple **perfil local** + **SARELA_DEV_LOGIN_ENABLED=true**; además rechaza peticiones cuya IP no sea loopback o cuyo Origin no coincida con el frontend local. `app.dev-login.enabled` está desactivado por defecto. Nunca activar en instancias públicas ni copiar esta variable a AWS. La cuenta demo **no es tu cuenta real de Google** y sus datos no se sincronizan.

Si el navegador indica que modo demo está deshabilitado, reinicia Java desde la misma ventana CMD donde definiste la variable. Si Angular ya estaba en ejecución, actualiza `http://localhost:4200`.

## 4. Ejecutar Python localmente

Requiere Python >= 3.11. Desde la raíz de Finora:

```bash
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell: .venv\\Scripts\\Activate.ps1
python -m pip install -r agent_service/requirements.txt
python -m pip install httpx
python -m unittest agent_service.test_finance -v
```

Configura `SARELA_AGENT_INTERNAL_KEY` con un secreto aleatorio suficientemente largo para desarrollo. **No uses claves reales en Git**.

```bash
# Linux/macOS (ejemplo: reemplaza por secreto privado real)
export SARELA_AGENT_INTERNAL_KEY='usa-un-secreto-largo-y-aleatorio'
python -m uvicorn agent_service.main:app --host 127.0.0.1 --port 8001
```

Prueba salud: `GET http://localhost:8001/health`. Para ejecutar una misión, se requiere la cabecera `X-Sarela-Agent-Key`. Una petición sin cabecera obtiene 401.

### ¿Cómo activar búsquedas web?

El servicio usa la API externa configurada en `search.py`. Define `TAVILY_API_KEY` en el proceso Python si cuentas con un plan y autorización correspondientes. Sin clave, el servicio **sigue respondiendo el cálculo**, pero muestra «búsqueda no configurada» y cero anuncios, sin inventarlos.

**Las URLs mostradas son candidatos, no departamentos confirmados.** Todavía deben implementarse extracción de fichas, deduplicación, comparación de precio, moneda, área, vigencia, contacto y cobertura de los portales. No prometer «el depa más barato de Lima».

### Inicio rápido con Docker (sin AWS)

Se añadió un overlay `docker-compose.agents.yml` para ejecutar PostgreSQL, Java y Python en red interna. Desde la raíz del repositorio, define `SARELA_AGENT_INTERNAL_KEY` en un archivo `.env` local excluido de Git, y opcionalmente `TAVILY_API_KEY`. Después:

```bash
docker compose -f docker-compose.yml -f docker-compose.agents.yml up --build
```

Java queda en `http://127.0.0.1:8080`. Python **no publica puerto al host**: Java lo contacta por `http://agent:8001` en la red del Compose. En otra terminal inicia Angular: `cd frontend && npm ci && npm start`. No conectar este Compose a datos productivos.

## 5. Conectar con Spring Boot y Angular

En el backend de Java, define:

```text
SARELA_AGENT_URL=http://127.0.0.1:8001
SARELA_AGENT_INTERNAL_KEY=el-mismo-secreto-privado-del-proceso-python
```

Para el resto de variables de Java/Gmail, conservar la configuración existente en `backend/src/main/resources/application.yml` y `README.md`.

La ruta pública de Spring Boot es `POST /api/v1/agents/housing/analyze`, **solo para sesión autenticada**. Si Python no está configurado, devuelve 503; si falla, devuelve un error controlado. La interfaz no accede directamente a Python.

Lanzar frontend desde `frontend/`: `npm ci`, `npm start` y navegar a la aplicación con la sesión normal de Sarela. Requiere el backend de Java y su base de datos de desarrollo, **no conectada a producción**.

## 6. Qué editar para cambiar comportamiento

| Quiero cambiar… | Archivo |
|---|---|
| Las preguntas al usuario | `frontend/src/app/agents/housing-agent.component.ts` |
| El aspecto del catálogo | `frontend/src/app/agents/agent-hub.component.ts` |
| La regla de cuota y capacidad | `agent_service/finance.py` + pruebas |
| La fuente de búsqueda | `agent_service/search.py` + pruebas con respuestas simuladas |
| La validación de datos | `agent_service/main.py` y `HousingAgentController.java` |
| El endpoint frontend | `frontend/src/app/core/services/api.service.ts` |
| Agregar Auto | Nuevo módulo Python + pantalla Angular, reutilizando la pasarela y pruebas |

Cuando cambies el cálculo, modifica **primero las pruebas** para describir el nuevo comportamiento. No utilices un LLM para efectuar aritmética hipotecaria que puede hacerse determinísticamente.

## 7. Aprender agentes: orden recomendado

1. **Python intermedio**: módulos, funciones tipadas, `venv`, `Decimal`, excepciones, `unittest`.
2. **HTTP y REST**: GET/POST, JSON, estados 200/401/422/502/503, timeout, autenticación por cabeceras.
3. **FastAPI y Pydantic**: modelos de petición, endpoints, validaciones.
4. **Uso de herramientas por modelos de lenguaje**: `tool calling`, esquema de entradas/salidas, ejecución controlada, permisos.
5. **RAG y búsqueda**: obtener fuentes, fechas, relevancia, deduplicación, verificación y citas. **Buscar un enlace no verifica un inmueble**.
6. **Orquestación y estado**: planes de varios pasos, reintentos, límites de costo, colas y tareas periódicas.
7. **Evaluación**: pruebas de cálculos, comparaciones con fuentes, datos falsos, prompt injection, métricas de uso y costo.
8. **Finanzas inmobiliarias de Perú**: TEA frente a TCEA, cuota de amortización, inicial, seguros, gastos notariales y registrales, y reglas de cada banco.

**Ejercicio práctico**: cambia el supuesto de TEA solo en un test, observa que falle, actualiza la función y vuelve a pasar todas las pruebas. Así entenderás cómo mantener coherencia entre lógica y resultados.

## 8. Checklist antes de desplegar

- [ ] CI verde en la rama, incluidas compilación Angular, pruebas Java y Python.
- [ ] Validar que login Google no obliga a conectar Gmail.
- [ ] Comprobar que usuarios sin sesión no llaman a Vivienda.
- [ ] Configurar `SARELA_AGENT_URL` y `SARELA_AGENT_INTERNAL_KEY` en staging, jamás en frontend.
- [ ] Mantener Python privado en red y colocar límites de memoria, tiempo y peticiones.
- [ ] Agregar cuota de búsquedas por usuario y tope de gasto de API antes de beta pública.
- [ ] Revisar privacidad de datos de ingresos, ahorros y deudas, su retención y envío a proveedores.
- [ ] Corregir y actualizar las páginas legales/SEO que todavía hablan del producto como solo Gmail.
- [ ] Verificar las ofertas inmobiliarias y condiciones bancarias antes de sugerir decisiones reales.
- [ ] Probar el ciclo completo en entorno de staging con datos ficticios.
- [ ] Recién después plantear publicación en producción.

## 9. Qué se despliega

| Cambio | Componente |
|---|---|
| Catálogo de agentes, navegación, formulario Vivienda | **Frontend Angular: SÍ** |
| Pasarela autenticada para Python | **Backend Spring Boot: SÍ** |
| Motor financiero e investigación | **Nuevo servicio Python: SÍ** |
| Cambios en tablas Gmail/DB | **NO** |
| Producción AWS actual | **NO se modifica por crear este PR** |

No desplegar solo frontend: el botón de Vivienda necesitaría backend Java y motor Python.
