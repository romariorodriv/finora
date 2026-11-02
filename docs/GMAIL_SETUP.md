> Actualización 31/10/2026: el acceso público y la sincronización cambiaron a Google/OIDC y trabajos persistentes. La guía vigente está en [GOOGLE_GMAIL_ONBOARDING.md](GOOGLE_GMAIL_ONBOARDING.md). Las secciones anteriores de acceso por contraseña o sincronización síncrona son históricas.

# Configurar Gmail en Sarela

## 1. Crear el proyecto de Google

1. Abre [Google Cloud Console](https://console.cloud.google.com/).
2. Crea o selecciona un proyecto.
3. Ve a **APIs y servicios > Biblioteca** y habilita **Gmail API**.
4. Configura la pantalla de consentimiento OAuth.
5. Durante desarrollo, agrega tu correo en **Usuarios de prueba**.

## 2. Crear las credenciales

En **APIs y servicios > Credenciales**, crea un **ID de cliente OAuth** de tipo **Aplicación web**.

URI de redirección autorizada exacta:

```text
http://localhost:8080/api/integrations/gmail/callback
```

Sarela solicita únicamente `gmail.readonly`: puede leer mensajes, pero no modificar ni eliminar correos.

## 3. Definir variables en Windows PowerShell

Ejecuta esto en la misma terminal donde iniciarás Maven:

```powershell
$env:GOOGLE_CLIENT_ID="TU_CLIENT_ID"
$env:GOOGLE_CLIENT_SECRET="TU_CLIENT_SECRET"
$env:GOOGLE_REDIRECT_URI="http://localhost:8080/api/integrations/gmail/callback"
$env:FRONTEND_BASE_URL="http://localhost:8080"
$env:TOKEN_ENCRYPTION_KEY="pon-aqui-un-secreto-largo-unico"
$env:JWT_SECRET="pon-aqui-otro-secreto-largo-unico"
mvn spring-boot:run
```

Las variables desaparecen al cerrar esa terminal. No publiques los secretos ni los subas a Git.

## 4. Probar

1. Abre `http://localhost:8080`.
2. Inicia sesión o crea una cuenta.
3. En **Conexión Gmail**, pulsa **Conectar Gmail**.
4. Autoriza la cuenta.
5. De regreso en Sarela, pulsa **Actualizar movimientos**.

La primera sincronización revisa 30 días. Puedes cambiarlo con `GMAIL_INITIAL_SYNC_DAYS`.

## Problemas frecuentes

- `redirect_uri_mismatch`: la URI en Google debe coincidir carácter por carácter.
- `GMAIL_NOT_CONFIGURED`: faltan `GOOGLE_CLIENT_ID` o `GOOGLE_CLIENT_SECRET`.
- La app no está verificada: agrega tu Gmail como usuario de prueba mientras desarrollas.
- No aparecen gastos: confirma que existan notificaciones bancarias recientes y revisa el remitente/contenido que reconoce `PeruBankParser`.

## Renovación OAuth: error 400 de Google (31/10/2026)
El log observado falla en GoogleGmailClient.refresh, llamado desde validAccessToken, antes de buscar o importar mensajes. El cliente anterior descartaba el cuerpo de error, por lo que HTTP 400 no permite afirmar si fue invalid_grant, invalid_client u otro motivo. Reconectar Gmail desde la aplicación es la primera prueba para renovar una autorización inválida; no elimina transacciones ya importadas.

La corrección conserva solo códigos OAuth conocidos del campo error; nunca expone el cuerpo de Google, error_description, tokens, secreto ni cabeceras. Registra el estado HTTP y ese código seguro para diagnóstico.

Resultados de renovación:
- invalid_grant o ausencia de refresh token: 401 GMAIL_RECONNECT_REQUIRED; guarda REAUTHORIZATION_REQUIRED incluso al responder con error. /status devuelve connected=false y la interfaz ofrece Conectar Gmail. Una nueva sincronización vuelve a pedir reconectar sin repetir el token inválido.
- invalid_client, unauthorized_client o deleted_client: 503 GMAIL_OAUTH_CONFIGURATION_ERROR. Revisar las credenciales del cliente OAuth en el servidor; conserva la autorización guardada y no pide al usuario reconectar inútilmente.
- HTTP 429/5xx o fallo de comunicación: 503 GMAIL_TEMPORARILY_UNAVAILABLE. Conserva conexión y credenciales.
- Otros errores de renovación: 502 GMAIL_OAUTH_REQUEST_FAILED. No interpreta automáticamente cualquier 400 como token expirado.

La sesión de Sarela se conserva al pedir reconexión de Gmail. El estado solo se modifica para la cuenta afectada. El estado REAUTHORIZATION_REQUIRED ya existía: no requiere nueva columna, migración ni cambios SQL.

Archivos modificados: gmail/client/GoogleGmailClient.java, gmail/service/GmailOAuthService.java, frontend/src/app/app.component.ts y su prueba. Nuevos tests: GoogleGmailClientTest, GmailOAuthServiceTest y GmailOAuthIntegrationTest.

Validación: mvn clean package con 117 pruebas correctas; Angular headless con 54 correctas; npm run build -- --configuration production correcto. Tests usan HTTP Google simulado y H2 desechable, sin credenciales reales. Se verifican privacidad del error, persistencia tras 401, aislamiento entre cuentas, renovación exitosa y fallos de configuración/temporales. No se probó el consentimiento real del usuario ni se publicó esta corrección en AWS.

Artefacto local independiente: backend/target/finora-gmail-preview.jar. Requiere publicación de backend y frontend para activar el manejo completo; el código local aún debe guardarse en Git. El JAR se construyó en una copia temporal con las mismas fuentes para conservar los procesos locales abiertos.

Referencias oficiales:
- https://developers.google.com/identity/protocols/oauth2/web-server (invalid_grant e invalid_client).
- https://developers.google.com/identity/protocols/oauth2 (los refresh tokens de aplicaciones externas en Testing con scopes Gmail pueden expirar a los siete días; no se ha comprobado el estado del proyecto Sarela).
