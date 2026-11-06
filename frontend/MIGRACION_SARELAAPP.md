# Migración Angular a sarelaapp.com
Fecha: 2026-11-06. Rama: `fix/sarelaapp-domain-migration`. Solo se modificó `frontend/`.

## Configuración esperada
- Frontend canónico: `https://sarelaapp.com`.
- Alias de acceso: `https://www.sarelaapp.com`. Preferir una redirección 301/308 a la versión canónica desde CloudFront; el HTML por sí solo no fuerza el redirect.
- API de producción: `https://api.sarelaapp.com/api/v1`, configurada en `src/environments/environment.prod.ts`.
- Entorno desarrollo: `http://localhost:8080/api/v1`, conservado.
- Angular `angular.json` sustituye `environment.ts` por `environment.prod.ts` en el build de producción.
- Los servicios AuthService, ApiService y AdminService ya usan `environment.apiBaseUrl`, sin URLs absolutas independientes.
- El interceptor `authInterceptor` añade `withCredentials` **solo** a solicitudes dirigidas exactamente a la URL base configurada y sus rutas hijas. No envía las cookies ni cabeceras a dominios externos.
- Las páginas legales, canonical, robots, sitemap y las pruebas de publicación apuntan al nuevo dominio.
- No se modificaron los scopes de Google ni se incluyeron secretos.

## Bloqueo real: backend OAuth y cookies (NO corregido aquí)
La mera autorización CORS de los dominios **no es suficiente**. El backend revisado contiene:
1. `SessionOriginFilter`: exige `Origin == FRONTEND_BASE_URL` **exactamente**, con un único origen. Si sigue usando el dominio antiguo, las solicitudes autenticadas `POST` / `PUT` / `DELETE` desde el nuevo dominio pueden fallar con `ORIGIN_NOT_ALLOWED` (403), incluyendo `/auth/google/start`, `/auth/google/logout` y Gmail sync.
2. `GoogleAuthController`: redirige al `FRONTEND_BASE_URL` configurado. Debe apuntar a `https://sarelaapp.com` para evitar terminar en el sitio anterior.
3. `GoogleSignInService`: el redirect URI de Google login sale de `GOOGLE_LOGIN_REDIRECT_URI`; actualizar en backend y permitir la URL exacta `https://api.sarelaapp.com/api/v1/auth/google/callback` en el cliente OAuth Google Cloud (confirmar alias `/api` vs `/api/v1` según valor real).
4. `GmailOAuthService` usa `GOOGLE_REDIRECT_URI` para conectar Gmail y `FRONTEND_BASE_URL` para regresar al sitio. Actualizar la URI exacta para `https://api.sarelaapp.com/api/v1/integrations/gmail/callback`, también en Google Cloud.
5. `AuthCookies` usa cookies `__Host-`, `Secure`, `HttpOnly`, `SameSite=Lax` y sin atributo `Domain`. Son **host-only**. Una cookie establecida por `api.sarelaapp.com` no se enviará a `api-sarela.pgoapp.com` ni viceversa. No cambiar a cookies inseguras ni hacer un proxy de credenciales en Angular.

**Consecuencia:** mientras los callbacks antiguos estén activos, el frontend migrado **no puede considerarse OAuth/Gmail E2E funcional**. Resolver en una intervención autorizada de backend + consola Google Cloud antes de publicar para usuarios reales. Verificar también HTTPS, cookies, reverse proxy y `Access-Control-Allow-Credentials` para el nuevo origen.

## Validación local (Windows PowerShell)
Desde `frontend/`:
```powershell
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm run qa:domain
npm run qa:legal
```
El script `verify-domain-migration.mjs` inspecciona **el build de producción** en `dist/frontend/browser/`, confirma el dominio de API, ausencia del dominio anterior en recursos textuales, enlaces canónicos y SEO. `verify-legal-pages.mjs` comprueba la integridad de las tres páginas legales. Para QA posterior a despliegue: `node scripts/verify-legal-pages.mjs --public` compara hashes con el sitio público, pero solo después de publicar.

Empaquetado del ZIP con frontend fuente y build compilado, excluyendo `backend`, `node_modules` y secretos:
```powershell
git fetch origin
git switch --track origin/fix/sarelaapp-domain-migration
powershell -ExecutionPolicy Bypass -File frontend/scripts/package-migration.ps1
```
Si ya existe la rama local, usar `git switch fix/sarelaapp-domain-migration` y `git pull --ff-only`. El script de empaquetado **ejecuta pruebas y build, y se detiene si fallan**. Genera `sarelaapp-frontend-migrado.zip` en la raíz del repositorio.

## Instrucciones para publicación futura (NO EJECUTADAS)
**Precondición:** OAuth y origen exacto del backend corregidos y probados. Cuenta AWS CLI con permisos apropiados y un perfil elegido por el operador.

La distribución destinada a `sarelaapp.com` es `E2R1MKOPXY5S3Z`; la antigua es `E2K6NVKTSILZ56`. El bucket compartido reportado es `sarela.pgoapp.com`. Nunca publicar a una distribución basada solo en el bucket: confirmar que la distribución **nueva** apunta al bucket y ruta esperados.

1. Ejecutar `npm run build && npm run qa:domain && npm run qa:legal` en el frontend.
2. Ejecutar una **vista previa de solo lectura**:
   ```powershell
   .\scripts\publish-google-frontend.ps1 -ProfileName <PERFIL_AWS>
   ```
   El script exige el alias `sarelaapp.com` y el ID exacto `E2R1MKOPXY5S3Z`, inspecciona el origen S3 y muestra el destino sin realizar cambios. Verificar que el bucket sea `sarela.pgoapp.com`.
3. Cuando todas las dependencias estén resueltas y se autorice desplegar, publicar con respaldo automático:
   ```powershell
   .\scripts\publish-google-frontend.ps1 -ProfileName <PERFIL_AWS> -Apply
   ```
   El script sube assets, HTML, legales, robots y sitemap, configura caché, invalida las rutas de CloudFront y deja respaldo externo para rollback. **No usar `--delete`** ni subir a la distribución antigua.
4. Verificar `https://sarelaapp.com/`, `/privacidad.html`, `/terminos.html`, `/eliminar-datos.html`, `/robots.txt`, `/sitemap.xml`, CSS/JS y `/admin` recargando. Comprobar HTTPS y la redirección desde `www`. 
5. QA real con cuenta de prueba: login/callback/session restore, logout, Gmail consent/callback/sync/status/review/disconnect y sesiones independientes. Probar redirecciones y cookies en Network DevTools; nunca compartir valores de cookies o tokens.

## Verificación pendiente/resultado
Se implementaron cambios en GitHub y pruebas de contrato; **en la sesión de edición no se pudieron ejecutar Node/Angular o crear un ZIP local debido a que el runtime de archivos no estuvo disponible**. No declarar BUILD SUCCESS ni pruebas aprobadas en esta rama sin ejecutar los comandos indicados. No se tocó AWS, Google Cloud, backend o PostgreSQL.
