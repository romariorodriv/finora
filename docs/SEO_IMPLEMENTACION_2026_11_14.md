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
