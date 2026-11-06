# Rebranding visual del frontend a Sarela

## Alcance
- Se mantiene el diseño actual (misma estructura Angular, clases CSS y componentes).
- Textos visibles y accesibles de la landing, acceso Google, app autenticada, dashboard, navegación y vista previa: Sarela.
- Título y descripción SEO del frontend, favicon en uso y páginas legales: Sarela.
- Documentos legales conservan su redacción y limitaciones existentes; solo cambia la marca y las referencias de recursos, sin inventar un responsable legal.
- Los recursos de marca activos son `sarela-icon.svg` (misma imagen que antes) y `sarela-legal.css` (mismos estilos). Los archivos históricos `finora.svg` y `finora-legal.css` no están referenciados por las páginas nuevas, pero se conservan temporalmente para compatibilidad con navegadores que tengan páginas antiguas en caché.
- No se modifican las rutas HTTP, servicios, base de datos, OAuth, scopes ni backend.

## Identificadores técnicos que permanecen
- Selectores CSS `finora-home-*`, `finora-app-*` y `finora-brand-*`: no son visibles; renombrarlos requeriría refactor simultáneo de estilos/plantillas/tests sin aportar al cambio de marca.
- Claves `finoraToken` y `finoraUser`: el servicio de autenticación sigue eliminándolas como limpieza de sesiones antiguas. No se usan como credenciales nuevas.
- Identificadores internos y nombres del JAR/paquetes Java se mantienen porque el cambio pedido es exclusivamente frontend.
- Los mensajes que el servidor pueda emitir con el nombre anterior, si existen, requieren evaluación posterior en backend.

## Validación en Windows
Desde `frontend/`:

```powershell
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm run qa:domain
npm run qa:legal
npm run qa:brand
```

`npm run qa:brand` inspecciona los HTML compilados, el título, favicon, referencias legales y la marca anterior. La prueba de landing también verifica la marca visible en acceso Google.

Para un ZIP de código y build, ejecutar desde `frontend/`:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-migration.ps1
```
Genera `sarelaapp-frontend-marca-sarela.zip` en la raíz del repositorio tras pasar las pruebas.

## Despliegue
La publicación del frontend no se ha realizado. El script `scripts/publish-google-frontend.ps1` sigue utilizando una comprobación de CloudFront para `sarelaapp.com`, la distribución `E2R1MKOPXY5S3Z` y un respaldo local previo a cualquier subida. No subir hasta revisar la salida de las pruebas y confirmar OAuth real. Actualizar CloudFront después del despliegue para evitar HTML en caché.

## Notas
La aprobación de la redacción de las páginas legales por el operador no significa que Google haya aprobado el uso de Gmail. Mantener las advertencias honestas de tratamiento y eliminación de datos.
