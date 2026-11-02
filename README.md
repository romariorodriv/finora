# Sarela

> Acceso Google y Gmail: consulta [la guía vigente](docs/GOOGLE_GMAIL_ONBOARDING.md), la migración V2_4 y la configuración antes de desplegar. El acceso público por contraseña fue sustituido; no se ha habilitado todavía el acceso Gmail para público general.

Sarela está separada en dos aplicaciones:

```text
Angular frontend
  -> REST/JSON
Spring Boot REST API
  -> Service
  -> Repository
  -> JPA/Hibernate
  -> PostgreSQL
```

## Estructura

```text
sarela/
├── backend/      Spring Boot REST API
├── frontend/     Angular web app
├── docs/
├── docker-compose.yml
└── README.md
```

## Backend

Requisitos: JDK 21 y Maven 3.9+.

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

API local:

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/api/v1/health`

Variables relevantes:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JPA_DDL_AUTO
JWT_SECRET
TOKEN_ENCRYPTION_KEY
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GOOGLE_REDIRECT_URI
GOOGLE_LOGIN_REDIRECT_URI
AUTH_SECURE_COOKIES
GMAIL_JOBS_ENABLED
FRONTEND_BASE_URL
CORS_ALLOWED_ORIGIN
DEMO_DATA
```

El perfil local usa H2 y creación de esquema solo para desarrollo; producción exige migración previa y validate. Para entrar con Google localmente, configura las credenciales del cliente de pruebas y sus dos redirecciones.

Perfil local:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

## Frontend

Requisitos: Node 22/npm 10 o compatibles con Angular 19.

```powershell
cd frontend
npm install
npm start
```

Angular queda en `http://localhost:4200` y consume `http://localhost:8080/api/v1`.

La sesión nueva usa una cookie HttpOnly administrada por backend; el frontend no guarda tokens en localStorage. El Bearer anterior solo se admite para compatibilidad de clientes existentes.

## Docker

```powershell
docker compose up --build
```

Docker levanta PostgreSQL y backend. El frontend Angular se ejecuta aparte con `npm start`.

## Gmail

La integración Gmail permanece completamente en backend:

- OAuth callback
- client secret
- exchange/refresh tokens
- cifrado AES-GCM
- Gmail API
- parser bancario
- deduplicación
- persistencia

Configura Google siguiendo [docs/GMAIL_SETUP.md](docs/GMAIL_SETUP.md). Para desarrollo, usa:

```text
GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/integrations/gmail/callback
GOOGLE_LOGIN_REDIRECT_URI=http://localhost:8080/api/v1/auth/google/callback
FRONTEND_BASE_URL=http://localhost:4200
```
