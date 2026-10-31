# Landing Sarela — dirección visual e imágenes

La landing se actualizó en feature/sarela-2.2-premium-ui con una dirección minimalista de producto: encabezado translúcido, hero centrado, tipografía sans de gran escala, espacio en blanco, imágenes protagonistas, botones redondeados y módulos organizados en una cuadrícula. La paleta mantiene verde bosque, verde salvia y blanco. No se incorporaron marcas ni logotipos de terceros.

Se modificaron landing.component.html, landing.component.css y landing.component.spec.ts. Los eventos de acceso, formularios, JWT, dashboard y API permanecen intactos. No se desplegó.

## Recursos finales locales

Herramienta utilizada: image_gen integrada, modo de generación nuevo. No se usó CLI ni una API key propia. Los originales PNG se conservaron en C:/Users/Romario/.codex/generated_images/01a18e5b-c084-7ed0-bb51-a0534ad1aecd/. Se inspeccionaron las dos imágenes y se exportaron como WebP con Pillow (calidad 84), sin cambiar su contenido ni recortar los archivos. El ajuste a cada pantalla se hace con CSS.

- C:/Users/Romario/secondMind/finora-sarela/frontend/public/images/sarela-emerald-sculpture.webp — 1536×1024; 71.216 bytes.
- C:/Users/Romario/secondMind/finora-sarela/frontend/public/images/sarela-emerald-sculpture-960.webp — 960×640; 36.794 bytes.
- C:/Users/Romario/secondMind/finora-sarela/frontend/public/images/sarela-emerald-sculpture-640.webp — 640×427; 20.172 bytes.
- C:/Users/Romario/secondMind/finora-sarela/frontend/public/images/sarela-calm-desk.webp — 1536×1024; 141.280 bytes.
- C:/Users/Romario/secondMind/finora-sarela/frontend/public/images/sarela-calm-desk-640.webp — 640×427; 29.478 bytes.

La portada tiene prioridad de carga alta; la fotografía editorial se carga de forma diferida. Ambas usan variantes para móvil y texto alternativo. No hay peticiones a bancos de imágenes ni datos financieros ficticios dentro de las imágenes.

## Prompts finales de generación

### Portada

Create a premium photorealistic 3D studio product photograph for Sarela, a personal finance website. No words, no letters, no numbers, no logos, no UI, no currency. One elegant large sculptural continuous folded glass ribbon, two softly overlapping arches forming an abstract balanced flowing shape, translucent deep forest emerald green glass with subtle sage reflections and delicate physically realistic refractions. Minimal warm off-white seamless studio surface and background (#f7f9f6), soft natural side light, long very subtle shadow, abundant negative space around the single sculpture. Calm, refined, luxury consumer technology art direction, restrained sculptural beauty, precise material realism, immaculate composition. Wide landscape 3:2 composition, sculpture centered and occupying about 65 percent of frame, generous clear margins so it crops safely on mobile. Rich forest green #123f30 and muted sage #b6cdbb, no bright neon, no gold, no coins, no hands, no clutter. This will be the main hero artwork on a minimalist white-and-green financial SaaS landing page. Output high quality raster artwork.

### Fotografía editorial

Photorealistic editorial lifestyle image for Sarela personal finance website, quietly premium minimalist art direction. Close-up still life on an immaculate pale warm stone desk beside a large window: one closed dark forest green linen notebook, an unbranded off-white ceramic coffee cup, and a single fresh olive-green branch, all arranged with thoughtful generous spacing. The notebook is the main object; natural tactile linen and ceramic textures. Soft morning sunlight from upper left produces refined long diffused shadows. Calm feeling of having time and mental space, no person, no hands, no phone, no monitor, no visible money, no charts, no text, no logo, no letters or numbers. Strict color palette warm white #f7f9f6, deep green #123f30, sage #b6cdbb, gentle natural neutral shadows. Highly realistic luxury editorial photograph, no busy props, no decorative frames. Landscape 3:2 image, objects placed slightly to right of center, lots of clean space, safe for portrait mobile crop. High resolution photograph intended as a large image panel next to text on a very clean premium financial SaaS landing page.

## Verificación

- 38 pruebas frontend aprobadas, sin fallos ni omitidas.
- Prueba real de carga y decodificación de las dos imágenes locales aprobada.
- Layout en 1440, 1024, 768 y 390 px sin desbordamiento.
- Login, registro, vuelta a landing y dashboard preservados.
- npm run build -- --configuration production aprobado: 474,25 kB iniciales, sin advertencias de presupuesto.
- Recursos WebP incluidos en dist/frontend/browser/images.
- git diff --check aprobado. Backend, contratos y entornos sin cambios.

Se inspeccionaron visualmente los recursos generados. La revisión visual manual de la página completa y sus capturas sigue pendiente por la limitación del control de navegador documentada en SARELA_2_2_PREMIUM_UI.md; los tests de layout no sustituyen esa revisión.
