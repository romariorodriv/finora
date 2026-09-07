# Finora Complete

Aplicación full-stack de finanzas personales con una interfaz premium y conexión real de solo lectura a Gmail para detectar notificaciones de gastos.

## Qué funciona

- Registro e inicio de sesión con JWT y BCrypt.
- Dashboard, balance, categorías, movimientos y recurrentes.
- Crear, editar y eliminar transacciones.
- OAuth 2.0 con Google, tokens cifrados en la base de datos y desconexión/revocación.
- Búsqueda de notificaciones bancarias en Gmail, lectura MIME, clasificación, deduplicación e importación.
- Importador manual para desarrollar nuevos parsers sin conectar una cuenta.
- Asistente financiero determinístico.
- H2 para ejecución local y PostgreSQL/Docker para despliegue.
- Diseño responsive propio, inspirado en la claridad visual de productos premium.

## Arranque rápido en Windows

Requisitos: JDK 21 y Maven 3.9 o superior.

```powershell
cd backend
mvn spring-boot:run
```

Abre `http://localhost:8080`.

Cuenta de demostración:

- Correo: `demo@finora.pe`
- Contraseña: `Demo1234`

Para conectar Gmail sigue [docs/GMAIL_SETUP.md](docs/GMAIL_SETUP.md). Esa es la única configuración externa obligatoria para el correo.

## Docker (opcional)

Instala Docker Desktop y luego ejecuta desde la raíz:

```powershell
docker compose up --build
```

Docker no es necesario para desarrollar: Maven usa H2 automáticamente.

## Estructura

```text
finora-complete/
├── backend/                 Spring Boot + frontend estático
│   └── src/main/
│       ├── java/            API, dominio, seguridad y Gmail
│       └── resources/static Interfaz web
├── docs/
│   ├── GMAIL_SETUP.md       Manual de Google paso a paso
│   ├── ARQUITECTURA.md      Arquitectura para Obsidian
│   └── API.md               Mapa de endpoints
├── .env.example
└── docker-compose.yml
```

## Antes de producción

- Usa PostgreSQL y secretos gestionados fuera del repositorio.
- Configura HTTPS y cambia `FRONTEND_BASE_URL`/`GOOGLE_REDIRECT_URI` al dominio real.
- Completa la pantalla de consentimiento y el proceso de verificación que corresponda en Google.
- Agrega observabilidad, copias de seguridad, rate limiting y pruebas con plantillas reales de cada banco.
- Mantén revisión humana para correos con baja confianza; el proyecto no manda correos bancarios a un proveedor de IA.
