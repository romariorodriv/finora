# Sarela: prerender seguro de la portada pública

Fecha: 2026-11-14

## Alcance real

Esta implementación genera **HTML estático inicial de la portada Angular** en `frontend/dist/frontend/browser/index.html`. Las páginas de Mis Finanzas, Suscripciones, Calculadoras, Aprende, Cómo funciona, Seguridad y calculadoras ya eran HTML estático y siguen publicándose de la misma forma.

La solución elegida es una **captura prerenderizada del Angular público durante el build** con Chrome/Chromium aislado, no un servidor SSR con acceso a sesiones. Se conserva Angular en el navegador para login Google, restauración de sesiones y dashboard. No se generan snapshots de `/admin`, Gmail ni datos de usuarios.

## Flujo de build

```powershell
cd frontend
npm ci
npm run build
npm run qa:prerender
npm run qa:seo
npm run qa:domain
npm run qa:legal
npm run qa:brand
```

`npm run build` ejecuta **`ng build && node scripts/prerender-public-home.mjs`**. El segundo paso:
1. Inicia servidor **localhost** solo con archivos del build.
2. Abre Chrome con perfil nuevo, sin sesiones ni datos persistentes, bloqueando la resolución DNS de los hosts de API de producción.
3. Extrae el DOM renderizado y los estilos de la portada anónima.
4. Rechaza si faltan H1, productos, Aprende y enlaces esenciales, o si aparece una pantalla privada o scripts inyectados en la raíz.
5. Escribe el HTML público dentro de `<app-root>` y los estilos Angular en `<head>`.
6. Verifica la versión final con JavaScript **desactivado** y con Angular activado (un único componente de landing, sin duplicación).
7. Elimina el perfil temporal. Si falla cualquier comprobación, el build falla.

El browser necesita Chrome/Chromium instalado; puede utilizar `CHROME_BIN` para indicar ruta si no se autodetecta. En Windows normalmente detecta la instalación de Chrome en Program Files.

`frontend/scripts/verify-prerender.mjs` confirma que el `index.html` terminado contiene el contenido comercial legible para rastreadores sin JavaScript y que no se han serializado vistas privadas.

`publish-google-frontend.ps1` rechaza un release sin la marca de prerenderizado y los enlaces obligatorios. Conserva respaldo S3 e invalidación CloudFront. **No** modifica configuración CloudFront ni backend.

## Límites

- Esta solución es **prerender por snapshot de la versión pública**, no Angular SSR/hidratación oficial. Angular reemplaza el contenido estático al iniciar para restaurar una sesión o mostrar el dashboard; puede producir una transición visual breve, a comprobar con QA real.
- No incluir en el build datos que se obtengan de una sesión, cookies o Gmail. La captura tiene reglas de rechazo, que deben actualizarse si cambia la arquitectura de acceso.
- Las rutas incorrectas de CloudFront, el HTTP 403 en URLs inexistentes, las redirecciones `www` / dominio antiguo y `/admin` siguen siendo trabajo aparte (issue #5).
- No garantiza indexación ni citas de IA: verificar Search Console/Bing después del despliegue.
- La suite Angular completa tiene **6 fallos preexistentes también en main** (Gmail/dashboard/móvil). El workflow expone los fallos y mantiene como bloqueantes el build, prerender y las verificaciones SEO. No modificar el código financiero para silenciar tests.

## Cómo publicar

Usar perfil y distribución AWS ya verificados:

```powershell
cd frontend
npm ci
npm run build
npm run qa:prerender
npm run qa:seo
npm run qa:domain
npm run qa:legal
npm run qa:brand

.\scripts\publish-google-frontend.ps1 -ProfileName pgo
# Revisar ID, cuenta, bucket y compatibilidad OAuth, luego:
.\scripts\publish-google-frontend.ps1 -ProfileName pgo -Apply
```

Después de publicar, comprobar con `curl.exe -L https://sarelaapp.com/` que HTML contiene `data-sarela-prerender="anonymous-landing"`, un H1 y enlaces a `/calculadoras.html` y `/aprende.html`. Repetir QA de acceso Google/Gmail y validar en Search Console `Inspeccionar URL`.

## Rollback

El script de publicación genera un backup local de S3 y la ruta `release.json`; conservarlos. El script `rollback-google-frontend.ps1` restaura contenido con vista previa antes de `-Apply`.

No se ejecutaron cambios en AWS desde esta intervención del repositorio.
