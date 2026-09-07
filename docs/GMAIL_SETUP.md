# Configurar Gmail en Finora

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

Finora solicita únicamente `gmail.readonly`: puede leer mensajes, pero no modificar ni eliminar correos.

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
2. Inicia sesión con `demo@finora.pe` / `Demo1234` o crea una cuenta.
3. En **Importar**, pulsa **Conectar Gmail**.
4. Autoriza la cuenta.
5. De regreso en Finora, pulsa **Sincronizar ahora**.

La primera sincronización revisa 30 días. Puedes cambiarlo con `GMAIL_INITIAL_SYNC_DAYS`.

## Problemas frecuentes

- `redirect_uri_mismatch`: la URI en Google debe coincidir carácter por carácter.
- `GMAIL_NOT_CONFIGURED`: faltan `GOOGLE_CLIENT_ID` o `GOOGLE_CLIENT_SECRET`.
- La app no está verificada: agrega tu Gmail como usuario de prueba mientras desarrollas.
- No aparecen gastos: confirma que existan notificaciones bancarias recientes y revisa el remitente/contenido que reconoce `PeruBankParser`.

