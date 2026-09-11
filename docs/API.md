# API de Sarela

Todas las rutas privadas usan `Authorization: Bearer <jwt>`.

| Método | Ruta | Función |
|---|---|---|
| POST | `/api/auth/register` | Crear cuenta |
| POST | `/api/auth/login` | Iniciar sesión |
| GET | `/api/dashboard` | Resumen financiero |
| GET/POST | `/api/transactions` | Listar o crear movimientos |
| PUT/DELETE | `/api/transactions/{id}` | Editar o eliminar |
| GET | `/api/integrations/gmail/status` | Estado de Gmail |
| POST | `/api/integrations/gmail/authorization-url` | Comenzar OAuth |
| GET | `/api/integrations/gmail/callback` | Callback de Google |
| POST | `/api/integrations/gmail/sync` | Importar correos |
| DELETE | `/api/integrations/gmail` | Revocar y desconectar |
| POST | `/api/import/email` | Probar texto manualmente |
| POST | `/api/assistant` | Consultar asistente financiero |
