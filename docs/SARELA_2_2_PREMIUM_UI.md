# Sarela 2.2 — recuperación y mejora de la interfaz

Fecha: 30 de octubre de 2026. Rama creada: `feature/sarela-2.2-premium-ui`, desde `feature/sarela-2.2` (`1bbfc81`). No se hizo reset, commit, push ni despliegue.

## Investigación y regresión encontrada

Se revisaron las ramas locales/remotas y el historial de app.component.css/html, incluidos `3c32ed7`, `dfc5a11`, `0615162` y `1bbfc81`. La referencia visual se encontró en `C:\Users\Romario\secondMind\SARELA_2_1_DISENO_Y_PLANIFICACION\sarela\frontend\src\app`, especialmente el bloque CSS «Sarela 2.1 — visual refinement» y el HTML del shell.

La referencia 2.1 contiene una sidebar blanca, estados verdes suaves, tarjetas de 16–17 px, mayor jerarquía tipográfica y encabezado más cuidado. Esos refinamientos locales no estaban incorporados en la base Git actual. No se encontró un commit que eliminara exactamente ese bloque local: no se atribuye la regresión a un supuesto revert. `1bbfc81` añadió planning a la base anterior, sin trasladar todo el sistema visual 2.1.

Además, `dfc5a11` sustituyó las tarjetas de balance/ingresos y retiró el gráfico diario de la plantilla, aunque la API y las funciones de transformación siguen ofreciendo esa información. Se recuperó su presentación sin revertir las nuevas categorías, comercios ni feedback.

| Elemento | Antes de este cambio | Referencia 2.1 | Implementación actual |
|---|---|---|---|
| Navegación | Sidebar oscura; navegación horizontal móvil | Sidebar blanca y estados verde suave | Sidebar blanca, perfil, acceso a planificación; barra inferior móvil con cinco destinos |
| Tarjetas | Radio 8 px y poca separación visual | Radio 16–17 px y sombras sutiles | Tokens de 10/18/24 px, espaciados consistentes y sombras discretas |
| Dashboard | Predominio de gastos; planificación como enlace | Balance, ingresos y gráfico diario | Disponibilidad real protagonista, balance mensual explícito, ingresos/gastos, actividad, categorías, comercios y recurrentes |
| Planificación | Estilos independientes y formulario antes del resultado | Paneles verdes y proyección destacada | Proyección destacada, desglose, orientación diaria, avisos, estados y pagos ordenados |
| Formularios | Estados visuales básicos | Mejora de bordes y botones | Estilos comunes, mensajes, guardado visible y prevención de solicitudes repetidas |

La nueva interfaz toma esos elementos de la referencia disponible y los extiende. No es una restauración indiscriminada de archivos ni una copia de las versiones antiguas de los módulos.

## Implementación

- Tokens compartidos en styles.css: paleta, tipografía de sistema/Inter si está disponible localmente, bordes, radios, sombras, espaciados, estados, formularios y reducción de movimiento. No se añadieron fuentes remotas ni dependencias.
- Estilos de shell y módulos separados de los estilos del dashboard por responsabilidad. El módulo planning comparte las bases globales. Se conservaron los presupuestos CSS del proyecto.
- Planificación protagonista con datos de GET planning/state: disponibilidad, saldo confirmado, reserva, compromisos, límite diario y alertas. No se sustituyen los cálculos del backend por cálculos alternativos del cliente.
- Al añadir o quitar un pago se conserva cualquier borrador de saldo aún no guardado. Una proyección basada en datos anteriores deja de mostrarse mientras hay cambios pendientes. El formulario distingue datos guardados, carga, error, reintento y confirmación.
- Fechas y distintivos de pendientes/vencidos legibles; el vencimiento se presenta con referencia de calendario America/Lima. Se conserva la semántica de pagos y alertas del backend.
- Dashboard con balance mensual diferenciado del saldo disponible real, ingresos y gastos, gráfico diario SVG, categorías y comercios con detalle, movimientos recientes y suscripciones/recurrentes.
- Movimientos conservan filtros, paginación, edición, borrado, tipos y marca de recurrente. Sus tipos se presentan con etiquetas legibles y los signos no convierten transferencias en supuestos gastos.
- Gmail y feedback conservan sus acciones y mensajes de estado. No se agregó una pantalla de configuración ficticia: el estado de configuración de Gmail sigue viniendo de la API.
- Login/registro conservan sus campos, confirmación de contraseña y flujo JWT, con estados de solicitud y prevención de envíos repetidos.
- Accesibilidad: foco visible, enlace al contenido, aria-current en navegación, estados anunciados, gráfico con alternativa textual, control de foco en drawers, Escape para cerrar y devolución del foco. Las listas de categorías/comercios usan identidad estable para conservar el foco al actualizar Angular.
- No hay datos de demostración en la aplicación. Los fixtures nuevos se usan exclusivamente en pruebas; se comprobó que sus marcadores no aparecen en el bundle de producción.

## Funcionalidad y contratos conservados

Sin cambios en backend/, ApiService, modelos de API, interceptores, AuthService, entornos de desarrollo/producción, package.json ni package-lock.json. Se mantienen JWT, sincronización y conexión Gmail, categorías, clasificación de comercios, suscripciones, feedback, pagos y persistencia. El nuevo resumen reutiliza el endpoint planning/state ya existente.

## QA ejecutado

| Comprobación | Resultado |
|---|---|
| Unitarias y funcionales Angular | 31 PASS, 0 fallos, 0 omitidas; Edge 156 headless con Karma |
| Responsive 1440, 1024, 768 y 390 px | PASS en dashboard, planning, movimientos, Gmail, feedback, login y registro; sin desbordamiento horizontal |
| Navegación móvil | PASS: cinco destinos funcionales, estado activo y controles de al menos 44 px de ancho |
| Charts | PASS: datos de API en el gráfico y donuts circulares en los cuatro anchos |
| Drawers y teclado | PASS: detalle de categoría/comercio, Tab, Escape y restauración del foco |
| Contratos HTTP y JWT | PASS: GET/PUT de perfil y POST/DELETE de pagos con contratos existentes y Authorization |
| Planificación | PASS: recuperación, borradores, error, reintento, confirmación, déficit y avisos |
| Flujo real contra API local | PASS: registro/login JWT, cálculo, pagos, persistencia tras nuevo login, aislamiento entre usuarios, dashboard/recurrentes, categorías, comercios, feedback y lectura de estado Gmail |
| Build production | PASS: 440,30 kB iniciales; sin warnings de presupuesto |
| git diff --check | PASS |
| Errores de consola de las pruebas | Sin errores inesperados en las vistas ejercitadas |

El test de responsive usa iframes con viewports reales y copia las reglas CSS ya cargadas del navegador de pruebas; comprueba el layout calculado, no solo breakpoints declarados. No equivale a una aprobación estética manual ni a pruebas en hardware móvil real.

La API local se ejercitó con el JAR existente y una H2 en memoria, con datos demo y credenciales OAuth Gmail desactivados. Los datos introducidos eran exclusivamente datos de QA desechables. El script scripts/qa-local-api.ps1 rechaza destinos remotos. No se usaron cuentas ni datos de AWS. No se modificó el código del backend.

Contrastes medidos de los pares principales: texto 12,62:1; texto secundario 4,93:1; botón principal 11,80:1; navegación activa 6,50:1; aviso 6,74:1; error 5,54:1; texto de planificación 4,77:1. Esto verifica esos pares, no constituye una certificación completa WCAG.

## Capturas y limitaciones pendientes

Se intentó abrir la vista local antes de editar. El proveedor de navegador no estaba disponible. El control nativo Windows se detuvo después porque no pudo determinar la URL actual del navegador con suficiente confianza para aplicar su política. No se continuó esa automatización ni se usó otro canal para eludirla.

Por ello no se entregan capturas antes/después y no se afirma haber completado la comparación visual manual. La comparación disponible se basa en historial, CSS/HTML de 2.1 y pruebas de layout del frontend real. Quedan pendientes: revisión estética manual, Safari/iOS/Android reales, flujo OAuth/sincronización Gmail externa y prueba con usuarios/datos de staging. No se probó contra el backend AWS para evitar efectos sobre datos reales.

Las vulnerabilidades npm pendientes del informe SARELA_2_2_NPM_AUDIT.md siguen fuera de este cambio visual. No se cambiaron dependencias.

## Probar localmente

Frontend, desde una terminal:

```powershell
cd C:\Users\Romario\secondMind\finora-sarela\frontend
npm start -- --host 127.0.0.1 --port 4200
```

Abrir http://localhost:4200. El entorno de desarrollo conserva http://localhost:8080/api/v1. Para una prueba aislada, iniciar el JAR existente en otra terminal; todos los datos de esta instancia se pierden al cerrarla:

```powershell
cd C:\Users\Romario\secondMind\finora-sarela\backend
java -jar target/finora-mvp-0.1.0.jar --server.address=127.0.0.1 --server.port=8080 --spring.profiles.active=local --spring.datasource.url=jdbc:h2:mem:sarela-premium-qa --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.hibernate.ddl-auto=create-drop --app.demo-data=false --app.gmail.client-id= --app.gmail.client-secret= --app.cors.allowed-origin=http://localhost:4200
```

Pruebas frontend y compilación:

```powershell
cd C:\Users\Romario\secondMind\finora-sarela\frontend
$env:CHROME_BIN='C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
npm test -- --watch=false --browsers=ChromeHeadless
npm run build -- --configuration production
```

Con la API desechable local iniciada:

```powershell
./scripts/qa-local-api.ps1
```

Recorrido visual pendiente: crear una cuenta local, revisar el dashboard vacío, registrar un ingreso/gasto y un recurrente, filtrar movimientos, abrir detalles, guardar saldo/reserva/ingreso, añadir un pago, salir y volver a entrar para comprobar recuperación. Revisar las cinco opciones móviles, Gmail no configurado y feedback. No añadir movimientos de QA a cuentas reales.

## Archivos

### Modificados (8)

- `frontend/src/app/app.component.css`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/planning/planning.component.css`
- `frontend/src/app/planning/planning.component.html`
- `frontend/src/app/planning/planning.component.spec.ts`
- `frontend/src/app/planning/planning.component.ts`
- `frontend/src/styles.css`

### Nuevos (9)

- `docs/SARELA_2_2_PREMIUM_UI.md`
- `frontend/scripts/qa-local-api.ps1`
- `frontend/src/app/app.component.spec.ts`
- `frontend/src/app/core/services/planning-http.spec.ts`
- `frontend/src/app/dashboard/dashboard.styles.css`
- `frontend/src/app/premium-layout.spec.ts`
- `frontend/src/app/premium.test-fixtures.ts`
- `frontend/src/app/shared/focus-trap.directive.ts`
- `frontend/src/app/shared/icon.component.ts`

La carpeta .qa/ ya existía sin seguimiento al comenzar y quedó intacta.

## Actualización posterior: landing pública integrada

Se incorporó `frontend/src/app/landing/` con componente, HTML, CSS y pruebas. La entrada sin sesión muestra una landing coherente con la referencia 2.1 y el diseño premium: propuesta de valor, explicación del plan, funcionalidades, información sobre los datos, FAQ y accesos reales. La ilustración explica la relación entre saldo, pagos y reserva; no presenta cantidades ni usuarios ficticios.

La selección de acceso se conecta a los formularios existentes mediante eventos Angular. `#acceso` abre login y `#registro` abre registro, también al recargar. El botón «Volver a Sarela» regresa a la landing; al cerrar sesión se vuelve a esa entrada pública. Una sesión autenticada sigue entrando al dashboard. No se modificaron el backend ni los contratos de API.

Cambios adicionales: AppComponent (TS/HTML/CSS), ampliación de premium-layout.spec.ts y cuatro archivos nuevos en landing/ (componente, plantilla, estilos y especificación).

Validación posterior que sustituye los números anteriores: **37 pruebas PASS**, cero fallos; layout sin desbordamiento en 1440/1024/768/390 px, incluida landing, login y registro; compilación production PASS de **474,21 kB** iniciales y sin advertencias de presupuesto; git diff --check PASS. Se mantiene pendiente la revisión visual manual y las capturas por la limitación del control de navegador indicada antes.

Para ver la landing: iniciar el frontend con `npm start` y abrir http://localhost:4200 sin fragmentos en la URL. Si hay una sesión activa, cerrarla primero. No se publicó ni se desplegó esta actualización.

## Actualización visual con imágenes

La landing pública se refinó con una composición más minimalista: hero centrado, tipografía sans grande, mayor espacio en blanco, cabecera translúcida y cuadrícula de módulos con dos imágenes propias en verde bosque/salvia. Se conservaron los accesos y el comportamiento. Ver SARELA_LANDING_VISUAL_ASSETS.md para los recursos locales y prompts de generación.

Validación más reciente: 38 pruebas aprobadas, incluida decodificación de imágenes; responsive en los cuatro anchos solicitado; build production de 474,25 kB sin advertencias. Recursos optimizados WebP y fotografía secundaria con carga diferida. Sin cambios en backend/API ni despliegue.
