# Migración del frontend a sarelaapp.com

## Alcance y versión
Origen: rama `fix/finora-google-verification-qa` (documentos con estado `operator-reviewed`).
Rama de cambios: `fix/frontend-sarelaapp-com-migration`.
Cambios **exclusivamente** en `frontend/`. No se modificó Spring Boot, PostgreSQL, Google Cloud ni AWS. Nada fue desplegado.

### Configuración Angular
- Producción: `https://api.sarelaapp.com/api/v1`, definida en `src/environments/environment.prod.ts`.
- Desarrollo: `http://localhost:8080/api/v1`, sin cambios.
- `angular.json` reemplaza `environment.ts` por `environment.prod.ts` en builds de producción.
- Los servicios `AuthService`, `ApiService`, `AdminService` usan la misma variable; no necesitan URLs hardcodeadas.
- `authInterceptor` sigue estableciendo `withCredentials: true` **solo** para la API configurada. Sin tokens en localStorage ni debilitamiento de cookies.
- Landing: canonical y metadatos para `https://sarelaapp.com/`. URLs legales, robots.txt y sitemap.xml migrados.
- No se modificaron diseños, rutas, lógica financiera ni scopes OAuth.

## Validación local obligatoria antes de subir
Desde `frontend/`:

```powershell
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm run verify:domain
npm run verify:legal
```

Opcional, con PowerShell:
```powershell
pwsh -NoProfile -File scripts/tests/legal-deployment.tests.ps1
```
El verificador de dominio inspecciona `dist/frontend/browser` y rechaza bundles o páginas legales que aún contengan `sarela.pgoapp.com` o `api-sarela.pgoapp.com`. También valida el origen de la API compilada, canonicals, marcadores legales, robots y sitemap.

**Estado de pruebas de esta entrega:** las compilaciones y pruebas no han podido ejecutarse en el entorno de edición. No se debe declarar que pasaron hasta ejecutar los comandos indicados y revisar el resultado.

## Dependencias críticas de backend y Google (no implementadas)
**NO publicar el cambio para usuarios reales antes de resolver lo siguiente.**

1. En Lightsail, `/etc/sarela/backend.env` contiene o alimenta variables de producción. Verificar, **sin compartir secretos**, que:
   - `FRONTEND_BASE_URL=https://sarelaapp.com`
   - `CORS_ALLOWED_ORIGIN` admite de forma efectiva `https://sarelaapp.com` (y `https://www.sarelaapp.com` si se sirve desde www).
   - `GOOGLE_LOGIN_REDIRECT_URI=https://api.sarelaapp.com/api/v1/auth/google/callback` (según la ruta pública registrada en la aplicación).
   - `GOOGLE_REDIRECT_URI=https://api.sarelaapp.com/api/v1/integrations/gmail/callback` (según la ruta pública registrada).
2. Código existente de backend **SessionOriginFilter** compara `Origin` contra un único `app.frontend-url`. Aunque CORS responda correctamente, mutaciones de usuarios con cookie pueden recibir 403 si `FRONTEND_BASE_URL` no coincide exactamente con el dominio. El sitio `www` debe redirigir al apex o el backend deberá admitir explícitamente ambos orígenes, sin wildcard.
3. Google Auth Platform: registrar **exactamente** ambos redirect URI nuevos en el mismo cliente OAuth y confirmar pantalla de consentimiento y dominios autorizados según configuración de Google. Los redirects antiguos pueden permanecer durante un periodo de compatibilidad **solo si son necesarios y seguros**. Revisar también homepage, política y términos del perfil de Google para usar `https://sarelaapp.com`.
4. **Los callbacks antiguos pueden romper tanto Google Login como Gmail:** la API nueva expide cookies `__Host-...` ligadas al host `api.sarelaapp.com`; si Google devuelve al host antiguo, ese host no recibe las cookies de enlace del navegador. No usar redirección desde frontend para corregir un callback de backend.
5. Tras el callback correcto, el backend debe dirigir al usuario a `https://sarelaapp.com/?auth=connected` o `?gmail=connected`; no al dominio anterior. La cookie `SameSite=Lax` sirve para el callback OAuth por navegación superior GET si pertenece al host correcto. Verificar `Secure` + `HttpOnly` + `__Host-`.
6. **Compartir el bucket** `sarela.pgoapp.com` con la distribución anterior `E2K6NVKTSILZ56` significa que publicar en el bucket también cambia los objetos que ve la distribución anterior si utiliza el mismo origen y prefijo. Decidir redirección permanente del dominio viejo, aislamiento por prefijo o plan de retirada antes de subir. No presuponer compatibilidad de ambos dominios al compartir una sola compilación.
7. `www.sarelaapp.com` debe redirigir (301/308) al dominio canónico `https://sarelaapp.com`, o ser tratado explícitamente por el backend y por los flujos OAuth. El frontend no puede configurar la redirección de CloudFront.
8. Confirmar CloudFront deep links `/admin` y demás rutas Angular (fallback a `index.html` para rutas SPA, sin devolver `index.html` en peticiones de JS o páginas legales). Verificar invalidez de sesiones, recarga, logout, sincronización Gmail y aislamiento de cuentas con usuarios de prueba.

## Publicación (NO realizada)
Dominio nuevo: `https://sarelaapp.com`.
CloudFront esperado: `E2R1MKOPXY5S3Z`.
Bucket esperado: `sarela.pgoapp.com`.
Origen del build: `frontend/dist/frontend/browser/`.

El script `scripts/publish-google-frontend.ps1` hace una **vista previa sin cambios** si no se pasa `-Apply`, consulta la distribución exacta y bucket, y rechaza documentos sin estado `operator-reviewed`:

```powershell
# 1. Ejecutar primero validación y resolver dependencias OAuth.
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm run verify:domain
npm run verify:legal

# 2. Desde frontend/, vista previa SIN escribir en AWS:
.\scripts\publish-google-frontend.ps1 -ProfileName TU_PERFIL_AWS
```

La publicación real requiere autorización operacional específica:
```powershell
.\scripts\publish-google-frontend.ps1 -ProfileName TU_PERFIL_AWS -Apply
```
Al publicarse, hace respaldo previo del bucket, sube assets, actualiza HTML al final y crea invalidación en CloudFront. **No ejecutar `-Apply` en esta fase** hasta corregir callbacks, variables, dominio alternativo y convivencia con la distribución anterior. El respaldo se conserva fuera del bucket y permite el script `rollback-google-frontend.ps1` (vista previa y luego `-Apply`).

## QA funcional posterior a dependencias
Probar: landing, inicio Google cancelado/aceptado, cookies, restaurar sesión, logout, conexión Gmail rechazada/autorizada, sync/retry/no duplicados, dashboard, movimientos, administración (cuenta admin separada), recarga de `/admin`, enlaces legales, canonical, `robots.txt`, `sitemap.xml`, `www` -> apex y headers CORS en navegador real.

**No afirmar que los flujos OAuth funcionan completamente sin probar una cuenta real tras actualizar el backend y la consola Google.**
