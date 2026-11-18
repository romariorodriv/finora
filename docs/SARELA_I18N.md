# Español e inglés en Sarela

## Auditoría y base

Implementado sobre `restore/sarela-without-agents-20261118` (1a96602), la restauración publicada por el operador. Al iniciar el trabajo, `main` seguía en 32978b7 y el PR #11 estaba pendiente; no se reincorporan las ramas de agentes. El dominio canónico vigente del código es https://sarelaapp.com, no el dominio pgoapp anterior.

La SPA tiene AppComponent (acceso, dashboard, movimientos, Gmail y feedback), LandingComponent, LandingPreviewComponent y AdminComponent cargado de forma diferida en /admin. Las vistas financieras se seleccionan dentro de AppComponent. No existía i18n. Los textos de dashboard/importación/errores del servidor podían mostrarse directamente. Además hay 21 HTML públicos independientes (incluido 404), calculadoras con JavaScript inline y un prerender anónimo mediante Chrome. Ningún contenido privado se prerenderiza.

## Diseño y mantenimiento

`core/i18n/language.service.ts` ofrece señales de idioma/locale, traducción parametrizada, persistencia y presentación de errores. `es.ts` y `en.ts` contienen claves de texto fuente en español (un esquema tipo gettext). Se usan claves completas para que las frases puedan reordenar parámetros `{0}`, `{1}`. El inglés comprueba sus claves contra español mediante TypeScript. TranslatePipe sirve para plantillas; las llamadas `lang.t` sirven para mensajes dinámicos. No hay traducción automática ni manipulación de textos del DOM de Angular.

El selector ES/EN está disponible globalmente, también en acceso y administración. Español es el valor inicial. Se persiste `sarela.language`; `?lang=es|en` permite enlaces explícitos. Los parámetros de OAuth, fragmentos y estado de navegación no se reemplazan al cambiar idioma. Se restaura la preferencia al regresar al mismo origen desde Google; no se cambian scopes ni URLs OAuth. La pantalla de consentimiento alojada por Google usa las preferencias de Google/navegador y no la controla este selector.

Se actualizan `html.lang`, título y metadatos sociales. Fechas, importes, meses y gráficas utilizan es-PE/en-US sin convertir monedas ni cambiar fechas almacenadas. Descripciones, comercios, asuntos de correo, feedback y categorías personalizadas siguen siendo datos originales. Los nombres de categorías predeterminadas tienen una etiqueta localizada sin alterar los valores enviados a la API. Los mensajes conocidos del servidor se traducen por catálogo/código; un error desconocido en inglés se presenta con un mensaje genérico en inglés en vez de texto español sin traducir. Los estados de importación desconocidos conservan una descripción localizada del estado.

La confirmación local de borrado utiliza ELIMINAR/DELETE según idioma; la petición de eliminación mantiene el contrato original del servidor. No cambian roles, cuentas, cookies, permisos ni esquema de datos.

## HTML públicos, SEO y despliegue

`i18n/public.es.json` y `public.en.json` traducen el contenido público, incluidos los textos legales existentes. No se han añadido promesas ni corregido pendientes legales como parte de traducirlos.

`npm run build` compila Angular, genera las páginas independientes en ambos idiomas y prerenderiza la landing anónima española e inglesa. Español conserva sus URLs originales. Inglés utiliza `/en.html` y `/en-<nombre-original>.html`, evitando depender de nuevas reglas de reescritura de CloudFront. Las páginas tienen canonical propio y alternativas recíprocas es/en/x-default; el sitemap del artefacto incluye ambas versiones. JSON-LD y resultados/etiquetas de calculadoras se traducen durante la compilación. El cambio de idioma en páginas independientes navega entre documentos completos; los campos de calculadora se reinician al cambiar de documento. La SPA cambia idioma sin reiniciarse y conserva formularios.

El HTML público incluye un selector de enlaces utilizable sin JavaScript. `public-language.js` comparte la preferencia con Angular y respeta enlaces explícitos. El proceso existente de publicación PowerShell incluye las nuevas páginas, archivos auxiliares y sus invalidaciones; el modo LegalOnly incluye las traducciones legales. No se ha ejecutado publicación ni modificado infraestructura.

Para comprobar TODA la web localmente, ejecutar `npm ci`, `npm run build` y servir `dist/frontend/browser` como sitio estático (por ejemplo `python3 -m http.server 4200 --directory dist/frontend/browser`). `ng serve` sirve las vistas Angular para desarrollo; las traducciones de documentos independientes son artefactos generados por build.

## Validación

- `npm run qa:i18n`: igualdad de claves y parámetros, ausencia de textos sin marcar en las plantillas, documentos estáticos, canonical, alternativas e idioma.
- `npm test -- --watch=false --browsers=ChromeHeadless`: regresiones existentes más cambio de idioma en landing, dashboard, movimientos, Gmail, feedback y administración; retorno OAuth, persistencia, confirmación de borrado y datos originales.
- `npm run qa:browser`: calculadoras/páginas públicas españolas e inglesas en Chrome con tamaños móvil/escritorio.
- Gates existentes de prerender, SEO, dominio, marca y documentos legales permanecen activos. Las comprobaciones legales comprueban artefactos; no certifican cumplimiento jurídico.

La verificación OAuth real requiere una cuenta de prueba autorizada y grabar el flujo publicado. Los tests no equivalen a aprobación de Google.
