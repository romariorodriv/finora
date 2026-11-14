# Auditoría SEO y Growth después de la implementación — Sarela

**Fecha:** 14 de noviembre de 2026  
**Rama auditada:** `feature/seo-content-design-growth-20261114`  
**Base:** `main` (commit `ab6af25f28428106f6c08fc2bf9140881d8cc05c`)  
**Auditoría:** GitHub Actions run [43627528452](https://github.com/romariorodriv/finora/actions/runs/43627528452).  
**Alcance:** repositorio y compilación local en CI; **NO** producción AWS, indexación real ni analítica de conversiones.

## Resultado verificado

| Prueba / área | Resultado |
| --- | --- |
| `npm ci` | PASS |
| `npm run build` (Angular + prerender home) | PASS |
| `npm run qa:prerender` (HTML inicial, sin JS, con JS y sin datos privados) | PASS |
| `npm run qa:seo` (sitemap, referencias de despliegue, enlaces de landing) | PASS |
| `npm run qa:public` (contenido y metadatos) | PASS |
| `npm run qa:browser` (Chrome en páginas y calculadoras) | PASS |
| `npm run qa:domain` | PASS |
| `npm run qa:legal` | PASS |
| `npm run qa:brand` | PASS |
| Pruebas Angular completas sobre rama | 69 PASS, 6 FAIL |
| Misma suite en `main` original | 69 PASS, 6 FAIL |
| Regresiones Angular añadidas por la rama | 0 fallos nuevos detectados |

**Los seis fallos preexistentes** corresponden a pruebas del dashboard/Gmail y al layout integrado de Movimientos a 390 px. No se consideran solucionados ni se silenciaron al modificar el código financiero. La comparación estricta de nombre de prueba fallido coincide.

## Inventario SEO comprobado

- **21 URLs canónicas indexables previstas**: 1 home prerenderizada + 20 páginas HTML públicas (incluidos 3 documentos legales).
- 7 guías educativas (1 revisada + 6 nuevas).
- 3 calculadoras con resultados reales en Chrome, no solo código estático:
  - presupuesto inicial: **S/ 600** de saldo mensual;
  - gastos hormiga: **S/ 260** estimados al mes;
  - suscripciones: **S/ 74** mensuales equivalentes.
- 6 páginas de producto / información de Sarela; nueva página **Sobre Sarela**.
- Ningún duplicado de título o metadescripción detectado en páginas comerciales/educativas.
- Canónicas, entradas del sitemap y manifiesto de publicación concordantes.
- Sin enlaces internos rotos detectados por auditoría estática.
- JSON-LD parseable y compartición Open Graph configurada.
- CSS común consistente con la landing: crema `#fdfcf9`, tinta `#10110f` y lima `#d4fa60`.
- Chrome comprobó resultados de cálculo, disponibilidad de enlaces, esquema de color y desbordamiento horizontal en tamaños efectivos 500 y 1280 px. **Limitación:** Chrome headless impuso un mínimo efectivo de 500 px; no se considera una comprobación visual exhaustiva a 320/390 px para las páginas estáticas. La landing Angular sí tiene pruebas separadas de anchuras pequeñas.

## Arquitectura de adquisición terminada en Git

- Landing mantiene su diseño original y enlaza una guía inicial más orientada al dolor del usuario.
- Mis Finanzas explica cobertura, Gmail opcional y límites del procesamiento.
- Suscripciones describe pagos recurrentes, ejemplo anual y calculadora; no promete cancelación ni seguimiento de uso ajeno.
- Calculadoras muestran fórmulas, detalles accesibles, resultados inmediatos, navegación y lectura relacionada.
- Aprende enlaza siete recursos según intención (no duplica páginas para la misma keyword).
- Cómo funciona y Seguridad explican diferencias entre Google login y Gmail; se reutilizan las políticas legales existentes.
- Sobre Sarela describe misión y límites sin inventar fundador público, certificados ni cifras de usuarios.
- Guías financieras citan información SBS/CFPB cuando corresponde y distinguen valores hipotéticos de hechos.
- Sitemap y script AWS incluyen las páginas nuevas. La publicación no borra chunks antiguos y sigue exigiendo prerender previo.

## Hallazgos detectados y corregidos dentro de esta misma iteración

1. El primer script de QA trató páginas legales sin descripción como metadescripciones comerciales duplicadas: **corregido** excluyendo ese control de marketing solo en páginas legales. Su validación jurídica existente permanece independiente.
2. Cambió el enlace educativo principal de la landing: la prueba Angular seguía buscando la guía anterior y mostró un fallo adicional. **Corregido** el selector para que acompañe la URL real. CI regresó al mismo conjunto de seis fallos preexistentes.
3. La revisión de calculadoras ahora ejecuta JavaScript en Chrome y valida importes concretos; los campos inicialmente son ejemplos y el resultado se recalcula en el navegador.

## Pendientes importantes (no simular completitud)

### Infraestructura / indexación
- Desplegar esta versión desde `main` con script S3/CloudFront y backup. No se modificó AWS desde esta auditoría.
- CloudFront: redirección www y dominio antiguo, respuesta HTTP 404 y ruta `/admin`, después de comprobar OAuth.
- Verificar en Search Console que Google indexa cada URL; enviar sitemap de 21 URLs. Revisar en Bing Webmaster Tools.
- Recoger impresiones, clics, consultas, CTR, páginas excluidas, Core Web Vitals y posibles citas en IA.

### Crecimiento y negocio
- Configurar propiedad real GA4/GTM (no se inventa measurement ID) con consentimiento y sin enviar importes, correos ni datos sensibles.
- Medir `click_product_cta`, `google_signin_complete` y `first_value` de manera segura desde la aplicación, comparando tráfico orgánico y activaciones.
- Verificar volumen y dificultad de keywords con Search Console/Keyword Planner; el mapa de `SEO_GROWTH_KEYWORDS_2026_11_14.md` es una hipótesis, no un ranking observado.
- Publicar persona responsable y canales empresariales solo tras validar y aprobar información real; nunca inventar experiencia, reseñas, certificaciones o tasas de ahorro.
- Evaluar entrevistas con usuarios y demanda real antes de seguir produciendo contenido. No hay garantía de ventas, indexación o citas IA.

## Release recomendado

```powershell
git switch main
git pull --ff-only origin main
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

.\scripts\publish-google-frontend.ps1 -ProfileName pgo
# Revisar distribución, cuenta, bucket, backup y compatibilidad login.
.\scripts\publish-google-frontend.ps1 -ProfileName pgo -Apply
```

**Decisión de release:** integrar a `main` si la comprobación automática es verde y los únicos fallos Angular corresponden a la línea base; publicar AWS posteriormente con vista previa y rollback. No llamar "SEO terminado" sin pruebas sobre la versión pública e indexación real.
