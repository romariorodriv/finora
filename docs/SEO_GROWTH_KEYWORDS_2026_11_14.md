# Sarela — mapa SEO, adquisición y conversión (14/11/2026)

**Estado**: plan editorial implementado en rama `feature/seo-content-design-growth-20261114`, no publicado en AWS. No existen en este informe datos de volumen, ranking, tráfico, registros ni ventas verificados de Search Console, Keyword Planner, GA4 o Bing. Las keywords siguientes son hipótesis de intención a contrastar tras indexación.

## Propuesta de valor verificable

Sarela es una app en beta orientada a identificar y organizar gastos que logra reconocer mediante **notificaciones financieras compatibles de Gmail**, previa autorización opcional separada del inicio de sesión con Google. No ofrece acceso bancario directo ni garantiza registrar todas las transacciones. Las calculadoras y guías funcionan públicamente sin registro. Las funciones futuras no deben describirse como disponibles.

## Mapa de intención de búsqueda → página única

| Búsqueda objetivo (hipótesis) | Intención | URL canónica | CTA principal |
| --- | --- | --- | --- |
| app para controlar gastos personales | Solución | /mis-finanzas.html | Conocer Sarela |
| controlar gastos automáticamente con Gmail | Solución / comparar | /mis-finanzas.html | Cómo funciona |
| cómo saber en qué gasto mi dinero | Diagnóstico | /guia-donde-se-va-mi-dinero.html | Calculadora gastos |
| cómo controlar gastos personales | Aprendizaje | /guia-control-de-gastos.html | Calculadora presupuesto |
| cómo organizar mi sueldo en Perú | Aprendizaje | /guia-presupuesto-sueldo.html | Calculadora presupuesto |
| calculadora presupuesto mensual soles | Herramienta | /calculadora-presupuesto.html | Calcular |
| calculadora gastos hormiga | Herramienta | /calculadora-gastos-hormiga.html | Calcular |
| qué son los gastos hormiga | Aprendizaje | /guia-gastos-hormiga.html | Calculadora gastos |
| cómo saber mis suscripciones | Solución | /suscripciones.html | Calculadora suscripciones |
| revisar suscripciones olvidadas | Aprendizaje | /guia-revisar-suscripciones.html | Calculadora suscripciones |
| calculadora suscripciones anual | Herramienta | /calculadora-suscripciones.html | Calcular |
| fecha de corte vs fecha de pago | Aprendizaje | /guia-fecha-corte-pago.html | Presupuesto |
| cómo llegar a fin de mes con dinero | Aprendizaje | /guia-fin-de-mes.html | Presupuesto |
| finanzas personales para principiantes Perú | Informacional | /aprende.html | Elegir guía |
| cómo funciona Sarela Google Gmail | Marca / confianza | /como-funciona.html | Registro |
| datos Gmail privacidad Sarela | Confianza | /seguridad.html | Privacidad |

**Regla anti-canibalización**: cada URL responde una intención distinta. No crear una segunda guía con un título casi equivalente sin consultar primero Search Console. Las calculadoras se posicionan para operaciones; las guías para preguntas; las páginas de producto para evaluación de soluciones.

## Arquitectura del usuario

1. Llega desde una búsqueda a una guía, producto o calculadora.
2. Resuelve una pregunta y puede usar una herramienta gratuita.
3. Descubre Mis Finanzas y entiende el alcance del permiso Gmail.
4. Decide si inicia sesión con Google; Gmail se conecta en otro paso opcional.
5. Se registra y debe experimentar el primer valor real. **No contabilizar registro como cliente de pago.**

## Eventos para instrumentar con GA4/GTM (necesita configuración externa)

| Evento propuesto | Disparo | Parámetros no sensibles |
| --- | --- | --- |
| `view_public_page` | Visita de contenido | page_type, page_slug |
| `calculator_result` | Calcular resultado | calculator_type; **no enviar montos de usuario** |
| `click_product_cta` | Enlace de una guía a producto | source_slug, destination_slug |
| `google_signin_start` | Comienzo del login | source_slug |
| `google_signin_complete` | Registro/login completado | user_state **sin correo ni identificadores personales** |
| `gmail_optin` | Consentimiento Gmail autorizado | success boolean |
| `first_value` | Primer gasto reconocido / primer informe útil | source_channel |

No se añadieron IDs ficticios de Google Analytics, cookies adicionales ni endpoints de tracking. Configurar consentimiento y políticas antes de enviar eventos; proteger información de gastos y datos de correo. Medir tráfico de marca vs no marca, CTR por URL, registros y activaciones reales. Reportar tasas solo cuando existan datos.

## Contenidos y límites editoriales

- Usar ejemplos explícitamente **ilustrativos**, con la fórmula descrita.
- Referenciar SBS para uso de tarjetas y educación financiera; CFPB para metodologías generales de revisión de gastos.
- No presentar autores, certificaciones, auditorías, número de usuarios, testimonios o tarifas no verificados.
- No prometer una detección universal de gastos/suscripciones, un bloqueo de compras, análisis del uso de apps ni conexiones directas con bancos.
- Incluir autoría individual y fecha de revisión solo con datos reales autorizados.
- Probar desde móvil la navegación, calculadoras y legibilidad; asegurar una sola H1, canonical, sitemap y JSON-LD válido.

## Revisión técnica antes del release

```powershell
cd frontend
npm ci
npm run build
npm run qa:prerender
npm run qa:seo
npm run qa:public
npm run qa:browser
npm run qa:domain
npm run qa:legal
npm run qa:brand
```

Los workflows de GitHub Actions deben validar la rama. Los seis fallos previos conocidos del dashboard/Gmail se registran por separado y no se atribuyen al SEO sin evidencia.

## Dependencias externas: NO COMPLETADAS DESDE GIT

- Confirmar que versión publicada use este commit y contenido completo, vía HTTP raw.
- CloudFront: 301/308 de www y dominio antiguo, 404 reales, rutas `/admin`, política de caché y verificación OAuth.
- Google Search Console: propiedad verificada, inspección de URLs, sitemap enviado, reporte real de indexación.
- Bing Webmaster Tools: envío de sitemap y revisión de rastreo / métricas de IA.
- GA4/GTM: conexión a propiedad real, consentimiento y medición de conversión.
- Investigación real de volumen/dificultad de keywords: Keyword Planner + consultas de Search Console; ajustar mapa.

## Fuentes de contenido educativo

- SBS, Finanzas para ti — guía práctica: https://www.sbs.gob.pe/Portals/0/jer/mat_edu_finan/Jovenes%20y%20adultos/Guias/Domina-tus-finanzas.pdf
- SBS, uso responsable de tarjetas: https://www.sbs.gob.pe/noticia/detallenoticia/idnoticia/4003
- CFPB, evaluación del gasto mensual: https://www.consumerfinance.gov/owning-a-home/prepare/assess-your-spending/

**Posicionamiento para crecimiento**: responder primero a "no sé en qué se va mi dinero" con claridad verificable; no competir por palabras genéricas solo por volumen.
