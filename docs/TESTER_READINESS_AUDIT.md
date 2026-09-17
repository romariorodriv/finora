# Sarela MVP tester readiness audit

Fecha local: 2026-09-17

## Cambios implementados

- Dashboard: el backend ahora entrega `expenseCount` y `averageTicket` calculados sobre todos los movimientos elegibles del periodo, no sobre la vista previa de ultimos movimientos. La UI consume esas metricas explicitas.
- Categorias: `Comida` queda normalizada al catalogo efectivo como `Alimentacion`; `Ahorro` no se trata como categoria de gasto.
- Clasificacion: se cubren variantes observadas de OXXO, Tambo, Rokys, PedidosYa, Servicentro, Primax, Peajes, Spotify y Paramount+.
- Falsos positivos: FACEBK, PAGOEFECTIVO*FACEBOOK, PLIN con nombres ambiguos, Apple generico y Max dentro de otra palabra no se clasifican como suscripcion.
- Movimientos: se agregaron busqueda, filtros por periodo/categoria/tipo, reset, conteo de resultados, estados vacios y paginacion local. Los filtros no cambian las metricas del dashboard.
- Gmail: el texto separa estado de conexion, ultima actualizacion y accion manual de sincronizacion. El boton de sync queda deshabilitado mientras sincroniza.
- Detalles: los paneles de categoria/comercio tienen `role="dialog"`, cierre con nombre accesible, backdrop y cierre con Escape.

## Causa confirmada

La inconsistencia "Transacciones: 4" era reproducible por diseno de codigo: el frontend contaba gastos desde `dashboard.transactions`, pero el backend enviaba solo una vista previa limitada a 8 movimientos y mezclada por tipos. El gasto total, categorias y comercios se calculaban desde la lista completa del mes, por eso el denominador del ticket promedio podia quedar desacoplado.

## Reglas de metricas

- Periodo: mes solicitado al endpoint de dashboard; por defecto, mes actual del servidor.
- Zona horaria: fechas `LocalDate` sin hora; para UI se formatea al mediodia local para evitar desplazamientos.
- Moneda: el parser bancario soportado registra PEN. No se suman monedas distintas porque el modelo de transaccion no expone moneda por movimiento manual.
- Gasto elegible: `type == EXPENSE`.
- Excluidos de gasto, conteo y ticket promedio: `INCOME`, `SAVINGS_WITHDRAWAL`, `SAVINGS_DEPOSIT`, `TRANSFER`, `CASH_WITHDRAWAL`, `REFUND`.
- Ticket promedio: `expenses / expenseCount`, escala 2, `HALF_UP`; si no hay gastos, `0.00`.
- Categorias efectivas: Alimentacion, Transporte, Salud, Servicios, Suscripciones, Otros.
- Operaciones ambiguas: se dejan en Otros hasta correccion manual; no se infiere PLIN por destinatario ni Facebook como suscripcion.

## Fixtures deterministas

### Conciliacion de metricas

Entradas:

| Comercio | Tipo | Monto | Categoria origen |
|---|---:|---:|---|
| Tambo | EXPENSE | 40.00 | Alimentacion |
| OXXO Peru | EXPENSE | 20.00 | Alimentacion |
| Uber | EXPENSE | 40.00 | Transporte |
| Empresa | INCOME | 2000.00 | Otros |
| BCP | TRANSFER | 300.00 | Transferencias |

Esperado:

- Gasto total: S/ 100.00
- Numero de gastos: 3
- Ticket promedio: S/ 33.33
- Alimentacion: S/ 60.00
- Transporte: S/ 40.00

### Vista previa no altera metricas

Entradas: 12 gastos OXXO de S/ 10.00.

Esperado:

- Gasto total: S/ 120.00
- Numero de gastos: 12
- Ticket promedio: S/ 10.00
- `transactions` del dashboard puede traer 8 items sin afectar las metricas.

### Clasificacion

Positivos esperados:

- OXXO MIRTOS, OXXO COSTA RICA, OXXO TREE -> Alimentacion
- TAMBO ARAMBURU-C9 -> Alimentacion
- MOLINA ROKYS -> Alimentacion
- PedidosYa -> Alimentacion
- SERVICENTRO SMILE SA., Primax, Peajes -> Transporte
- PARAMOUNT+, Spotify -> Suscripciones

Negativos esperados:

- FACEBK ADS -> Otros
- PAGOEFECTIVO*FACEBOOK -> Otros
- PLIN JUAN PRIME -> Otros
- APPLE STORE MIRAFLORES -> Otros
- MAXIMA LIBRERIA -> Otros

## Hallazgos originales

- Dashboard con contador/ticket incongruente: reproducido por codigo y corregido con metricas explicitas.
- Categorias Comida/Alimentacion: reproducido en parser y corregido a catalogo objetivo.
- Ahorro como categoria de gasto: mitigado en calculos; los tipos de ahorro quedan fuera de metricas de gasto.
- OXXO/Rokys/Servicentro/Paramount+ en Otros: cubierto por reglas y tests.
- Otros alto: pendiente de UX mas avanzada; hoy se mantiene descriptor original, detalle por comercio y edicion manual por movimiento.
- Gmail con mensajes ambiguos: corregido en copy/estado UI; no se probo con cuenta real.
- Movimientos sin filtros: corregido en UI.
- Cierre de detalles contradictorio: cierre visible existente reforzado con Escape y atributos accesibles.
- Feedback: validacion/envio duplicado ya existian de forma basica; no se envio feedback real.
- Insights 12 meses: no se encontro comparacion de 12 meses en codigo actual; los mensajes salen de calculos deterministas del mes.

## Pruebas ejecutadas

- `cd backend && mvn test` -> 70 tests, 0 fallas.
- `cd frontend && npm run build` -> build exitoso.
- `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless` -> 4 tests, 0 fallas.

## Checklist reproducible

### Login

- Ruta: `/`
- Paso: iniciar sesion con credenciales validas de desarrollo.
- Esperado: entra al dashboard y conserva sesion al recargar.
- Evidencia: captura del dashboard y token presente solo en `localStorage` local.

### Login invalido

- Ruta: `/`
- Paso: usar correo/password incorrectos.
- Esperado: mensaje generico "No pudimos iniciar sesion..." sin confirmar existencia de correo.
- Evidencia: captura del error.

### Dashboard y metricas

- Ruta: dashboard autenticado.
- Paso: cargar fixture de conciliacion.
- Esperado: gasto, numero de gastos y ticket promedio igual a los resultados esperados.
- Evidencia: captura de metricas y respuesta `/api/v1/dashboard`.

### Categorias

- Ruta: dashboard autenticado.
- Paso: abrir detalle de Alimentacion y Transporte.
- Esperado: totales iguales al resumen; solo gastos elegibles.
- Evidencia: captura de drawer y respuesta detail.

### Comercios

- Ruta: dashboard autenticado.
- Paso: revisar donut de comercios y abrir un comercio.
- Esperado: descriptor original conservado; Otros comercios agrupado cuando aplique.
- Evidencia: captura de lista y detalle.

### Movimientos

- Ruta: Movimientos.
- Paso: buscar "OXXO", filtrar por categoria y tipo, resetear filtros.
- Esperado: conteo "N de M", estado sin resultados cuando aplique, paginacion si supera 12.
- Evidencia: capturas de filtro aplicado y reset.

### Gmail

- Ruta: Conectar Gmail.
- Paso: probar estados mock/local no conectado, conectado, actualizando y error de API.
- Esperado: conexion y ultima actualizacion se muestran por separado; sync solo empieza con click.
- Evidencia: capturas por estado y conteo de requests.

### Feedback

- Ruta: Danos tu opinion.
- Paso: enviar sin rating, luego con payload valido usando mock/desarrollo.
- Esperado: validacion visible, boton deshabilitado durante envio, confirmacion de exito o error recuperable.
- Evidencia: captura de validacion y confirmacion.

### Responsive

- Viewports: 375, 390, 768, 1440 px.
- Paso: revisar dashboard, movimientos, Gmail, feedback y drawers.
- Esperado: sin overflow horizontal, botones alcanzables, importes no cortados.
- Evidencia: capturas por viewport.

### Sesion y errores

- Paso: logout, recarga sin token, token expirado/401 simulado, 403 en movimiento ajeno.
- Esperado: salida limpia, sin datos de otra cuenta, mensajes utiles.
- Evidencia: captura y respuesta HTTP.

### Estados vacios y errores

- Paso: usuario sin movimientos, solo ahorro/transferencias, API lenta/error.
- Esperado: gasto S/ 0.00, numero de gastos 0, ticket S/ 0.00, textos vacios claros.
- Evidencia: capturas y logs de red.

## Despliegue y reversion

No desplegar desde esta auditoria.

Procedimiento sugerido:

1. Crear backup/snapshot de base de datos si el despliegue apunta a datos persistentes.
2. Construir backend y frontend con los mismos comandos verificados.
3. Desplegar backend compatible con el nuevo contrato de dashboard antes del frontend.
4. Verificar `/api/v1/health`, login, dashboard y movimientos con cuenta de prueba.
5. Reversion: volver al artefacto backend/frontend anterior. No hay migraciones nuevas que revertir.

## Riesgos y limites

- No se hicieron cambios masivos sobre registros existentes.
- No se uso Gmail real ni se enviaron correos o feedback real.
- El modelo actual no tiene moneda por transaccion manual; por eso la regla de no mezclar monedas queda documentada, no plenamente imponible.
- La correccion manual persiste por edicion de movimiento existente; no hay flujo dedicado de "aplicar a similares".
- Capturas navegadas quedan pendientes de levantar backend local con datos fixture o mocks visuales.

## Bloqueos para una prueba con 10 usuarios

- Necesario: cuenta(s) de prueba, datos fixture representativos y confirmacion de configuracion Gmail de desarrollo si se quiere probar OAuth.
- Puede esperar: reclasificacion masiva historica, equivalencias avanzadas para comercios desconocidos y analitica de 12 meses.
