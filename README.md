# Sarela

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
mvn spring-boot:run
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
FRONTEND_BASE_URL
CORS_ALLOWED_ORIGIN
DEMO_DATA
```

Perfil local opcional:

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

El JWT se conserva en `localStorage` para mantener compatibilidad con el flujo anterior. Es simple para desarrollo, pero en producción exige cuidado contra XSS.

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
FRONTEND_BASE_URL=http://localhost:4200
```
