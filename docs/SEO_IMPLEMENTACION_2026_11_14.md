# Sarela SEO — implementación por fases (14-11-2026)

## Estado y alcance

Esta rama añade recursos públicos indexables independientes de Angular, enlaces a productos desde la landing existente, sitemap, metadatos sociales y verificación del manifiesto de publicación. **No altera backend, Gmail, OAuth, cookies, base de datos ni dashboard privado.**

**No equivale a completar las cuatro fases**: hay tareas de infraestructura, prerender real, medición y contenido editorial que dependen de AWS, Google/Bing y pruebas de producción. No desplegar hasta ejecutar la matriz de verificación.

## Fase 1 — Dominio y rutas: PENDIENTE EN AWS

- Inspeccionar distribuciones CloudFront de `sarelaapp.com`, `www.sarelaapp.com` y `sarela.pgoapp.com`, certificados, origins, behaviours, funciones y cachés.
- Configurar 301/308 URL-equivalente para `www` y el dominio anterior; conservar parámetros necesarios y revisar antes callbacks OAuth.
- En dominio principal, `/index.html` debe redirigir permanentemente a `/`. Solicitudes inexistentes deben dar 404 real, nunca 200 global con `index.html`.
- Verificar acceso directo a `/admin` con guard, sin hacer indexables páginas privadas. El acceso a API y callbacks no debe cambiar.
- Antes de modificar la configuración, tomar snapshot/backup del config de cada distribución y evaluar la opción de rollback.

## Fase 2 — SEO Angular: PARCIAL

- Completado: corrección de textos, enlaces semánticos de navegación, OG básico y JSON-LD conservador de organización/sitio.
- Completado: páginas públicas HTML autónomas rastreables sin JavaScript para productos, Aprende, Seguridad y calculadoras.
- **Pendiente**: prerender real de la landing principal `/`. Angular 19 actual ejecuta servicios del navegador en inicialización: ajustar `localStorage`, `history`, `location` y restauración de sesión; después evaluar build Angular 19 SSR/SSG en rama independiente con pruebas de hidratación y ausencia de datos privados.
- Pendiente: corregir políticas de caché reales de CloudFront; el script de publicación es solo una parte de la configuración.

## Fase 3 — Validación e indexación: PREPARADA, PENDIENTE EXTERNA

1. Ejecutar `cd frontend && npm ci && npm run build && npm run qa:seo && npm run qa:domain && npm run qa:legal && npm run qa:brand`.
2. Abrir localmente cada página HTML y calculadora; comprobar accesibilidad, cálculo y enlaces.
3. Ejecutar `./scripts/publish-google-frontend.ps1 -ProfileName <AWS_PROFILE>` (PowerShell, vista previa) y confirmar destino, cuenta y compatibilidad del frontend con API.
4. Solo con pruebas satisfactorias y respaldo, publicar con `-Apply`. El script incluye las nuevas páginas SEO y su CSS y no toca AWS CloudFront routing.
5. Revalidar `https://sarelaapp.com/sitemap.xml`, `robots.txt`, páginas públicas y cabeceras HTTP con un cliente externo. Inspeccionar páginas en Google Search Console y Bing Webmaster Tools. Revisar rastreo de OAI-SearchBot.
6. Revisar Core Web Vitals y métricas de conversión reales.

## Fase 4 — Crecimiento: INICIADA

- Páginas: Mis Finanzas, Suscripciones, Calculadoras, Aprende, Cómo funciona y Seguridad.
- Recursos: una guía de control de gastos y tres calculadoras gratuitas (gastos hormiga, presupuesto, suscripciones).
- Expandir contenido según volumen, intención de búsqueda y Search Console. Revisar con fuentes técnicas/financieras, autores y fechas reales antes de afirmar expertise.
- Medir clics orgánicos, conversiones a registro y activaciones; comparar preguntas representativas de asistentes IA, sin garantizar inclusión ni atribución.

## Publicación y rollback

El script existente exige la distribución dedicada `E2R1MKOPXY5S3Z`, hace backup S3, publica sin `--delete` e invalida CloudFront. No ejecutar manualmente en un bucket supuesto. Tras publicar, registrar backup path y commit de frontend. Si hay regresiones, usar `rollback-google-frontend.ps1` con el backup generado.

**Bloqueantes para declarar 'SEO OK':** renderizado real de `/` sin depender de JS, redirecciones, HTTP 404 para rutas inválidas, revisión de CloudFront y comprobaciones Search Console/Bing. Ninguno de esos bloqueantes se debe marcar como resuelto sin prueba de producción.


## Actualización del recorrido de la landing — 14/11/2026

Se detectó que la navegación a productos estaba escondida principalmente en el footer. Se modificó el componente Angular de la landing sin tocar el flujo de autenticación:
- Navegación superior visible: Productos, Calculadoras, Aprende y Cómo funciona.
- En móvil los enlaces ya no se ocultan: se distribuyen en una fila horizontal accesible.
- Tres tarjetas de producto con CTA reales a sus páginas HTML públicas.
- Sección separada 'Cómo funciona' con pasos de Google, permiso opcional de Gmail y gastos reconocidos.
- Sección 'Aprende' visible, dos recursos con imágenes existentes y enlaces a guía y calculadora.
- Diseño original conservado: blanco, verde lima, tipografía y mockups existentes.
- Nueva 404.html con directiva noindex; **solo funcionará como HTTP 404 real después de configurar el origen / distribución para entregar ese status**.
- Nueva herramienta de solo lectura: `frontend/scripts/audit-cloudfront-seo.ps1 -ProfileName pgo`. Inspecciona los IDs CloudFront documentados; nunca escribe configuraciones.

### Pendientes de infraestructura que no se resuelven modificando S3
- `www.sarelaapp.com` y `sarela.pgoapp.com` siguen requiriendo redirecciones permanentes reales, según distribución y hostname. No ejecutar redirects del dominio anterior antes de validar OAuth, cookies, retornos y permisos.
- `/index.html` debe consolidarse en `/` con redirección 301/308 verificada, sin perder querystrings de callback.
- `/admin` debe ofrecer la SPA únicamente a clientes autorizados, con guard y exclusión de indexación. Las rutas inexistentes deben devolver HTTP 404 real.
- La landing inicial Angular requiere prerender/SSG tras adaptar `localStorage`, `history`, `location` y restauración de sesión; no usar un HTML falso que exponga información privada.
- CloudFront routing, políticas de caché, WAF y headers deben verificarse en la cuenta real antes de desplegar.

### Comandos de QA antes de fusionar
```powershell
cd frontend
npm ci
npm run build
npm run qa:seo
npm run qa:domain
npm run qa:legal
npm run qa:brand
npm test -- --watch=false --browsers=ChromeHeadless
```
El workflow de GitHub `.github/workflows/sarela-seo.yml` ejecuta esas comprobaciones. **Un fallo en la suite completa es bloqueo de release hasta determinar si es regresión de la rama o deuda previa en main.**

### Release AWS
```powershell
cd frontend
.\scripts\audit-cloudfront-seo.ps1 -ProfileName pgo
.\scripts\publish-google-frontend.ps1 -ProfileName pgo
# Solo tras confirmar cuenta, backup, pruebas y OAuth:
.\scripts\publish-google-frontend.ps1 -ProfileName pgo -Apply
```
No se ha ejecutado ningún despliegue AWS desde GitHub. La publicación que ya existe en producción fue realizada previamente fuera de estas herramientas.
