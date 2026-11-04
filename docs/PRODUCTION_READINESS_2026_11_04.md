# Finora: bloque prioritario de preparacion para produccion

Fecha: 4 de noviembre de 2026 (America/Lima).
Rama local: `fix/finora-production-readiness`.

## Alcance y evidencia

El arbol inicial estaba limpio. No se encontraron archivos AGENTS.md en el repositorio, incluidos archivos ocultos, ni en los directorios superiores hasta C:\.
Se leyo `TESTER_READINESS_AUDIT.md` (17/09/2026); sus contratos de moneda e historial ya no describen completamente el codigo actual. La auditoria nueva esta en otra conversacion: se trabajaron los cuatro hallazgos que el usuario transcribio expresamente en esta sesion. No se afirma haber revisado el documento completo no recibido.

## Hallazgos verificados y cambios

| Hallazgo confirmado en codigo | Correccion |
| --- | --- |
| El perfil no guardaba cuando se confirmo el saldo | `balance_confirmed_at` generado en backend al confirmar; lectura y operaciones de pagos pendientes conservan la fecha |
| Saldos antiguos se usaban sin fecha comprobable | Fecha historica NULL, alerta de reconfirmacion y proyeccion suspendida hasta confirmar |
| No existia advertencia por antiguedad | Se advierte al leer si han transcurrido mas de 24 horas; se conserva la estimacion con advertencia |
| Los vencidos se excluian de la suma | Todo pago pendiente anterior al siguiente ingreso se descuenta, incluidos vencidos; el dia del ingreso queda excluido como antes |
| Solo se podian eliminar pagos | Se guarda `paid_at`; marcar realizado exige saldo real resultante, actualiza ambos en una transaccion y excluye el pago del pendiente. No resta su importe de nuevo |
| Historial descargaba solo el mes actual y paginaba localmente | Endpoint `/transactions/history` con fechas opcionales, busqueda, categoria vigente, tipo, moneda y paginacion SQL |
| Asistente ignoraba la pregunta y sumaba monedas bajo S/ | Lista explicita de preguntas soportadas, resumen por moneda o filtro ISO, nombres del catalogo actual, respuesta explicita ante preguntas no soportadas |

El caso obligatorio saldo PEN 1000, reserva 200 y pago vencido pendiente 300 devuelve disponible PEN 500. Tras pagarlo y confirmar saldo real 700, devuelve tambien PEN 500: el pago ya no vuelve a restarse.

El plan permanece exclusivamente en PEN. Cambiar monedas del resumen no lo convierte ni cambia su saldo. Las horas se muestran en Lima (UTC-05:00). La UI denomina expresamente la accion de guardar como confirmacion del saldo. Las confirmaciones no descuentan transacciones importadas automaticamente.

Los pagos realizados se conservan en la lista con su estado. El contador y limite de 200 corresponden a pendientes; la portada excluye realizados e incluye vencidos pendientes entre los proximos compromisos. Completar requiere perfil previo y saldo no negativo, de hasta dos decimales. Repetir la misma finalizacion devuelve 409 sin sobrescribir saldo. Hay bloqueo del perfil y versiones optimistas para evitar finalizaciones simultaneas del mismo pago.

## Contratos

- `GET /api/v1/planning/state`: agrega `balanceConfirmedAt` (ISO UTC o NULL); cada pago agrega `paidAt`.
- `PUT /api/v1/planning/state`: mantiene el payload y representa confirmacion explicita del saldo actual. No acepta una fecha de confirmacion suministrada por cliente.
- `POST /api/v1/planning/payments/{id}/complete`: `{ "resultingBalance": 700 }`; devuelve el estado actualizado. Verifica propiedad del pago.
- `GET /api/v1/transactions/history?from=2026-08-01&to=2026-10-31&page=0&size=12`: `{items,total,page,totalPages}`. Sin fechas incluye todo el historial guardado, no solo el mes. Fechas inclusivas, orden `date DESC, id DESC`, tamano 1..100. Conserva `/transactions` para compatibilidad.
- Filtros adicionales: `search`, `category` (nombre vigente, o `Sin categoria` con tilde en el contrato), `type`, `currency`. La busqueda ignora mayusculas y acentos habituales del castellano; `%`, `_` y `!` se buscan literalmente. El filtro moneda normaliza a mayusculas.
- Angular manda los filtros al servidor, reinicia pagina al cambiarlos, cancela lecturas anteriores y al destruir/cerrar sesion, distingue carga/error/vacio y corrige la pagina al eliminar el ultimo resultado.
- `POST /api/v1/assistant`: `{ "message": "Cuanto llevo este mes?", "currency": "USD" }`; `currency` opcional. Sin filtro entrega cada moneda separada, sin conversion. El periodo es el mes actual de Lima.
- Preguntas soportadas: `Cuanto llevo este mes?`, `Cuanto gaste este mes?`, `Cuanto he gastado este mes?`, `En que gaste mas?`, `Cual es mi categoria mas alta?`, `En que categoria gaste mas?`, `Que pagos se repiten?`, `Que pagos recurrentes tengo?`. Admite tildes, mayusculas y puntuacion. El resto se declara no soportado.
- Recurrencia: solo lista gastos efectivamente marcados `recurring` del mes y moneda consultados, indicando que no confirma frecuencia ni pagos futuros. No se infieren recurrencias, ingresos futuros ni periodicidades.
- Sin nuevas dependencias de IA. La aplicacion actual no tiene pantalla de asistente; el servicio Angular conserva el acceso al endpoint y agrega el parametro moneda.

## Migracion PostgreSQL

`backend/sql/V2_5__production_readiness.sql`, manual e idempotente, posterior a V2_4. Agrega timestamps nullable, versiones BIGINT NOT NULL DEFAULT 0 e indice de historial por usuario/fecha/id. No inventa fechas historicas ni marca pagos antiguos como realizados. No se ejecuto contra ninguna base persistente.

Antes de un despliegue futuro autorizado: revisar backup y ventana de mantenimiento, aplicar V2_5 antes del nuevo backend (JPA validate), ejecutar `backend/sql/verify_production_readiness.sql`, verificar los cuatro campos y el indice; despues publicar backend compatible y frontend. El indice se crea dentro de una transaccion normal, por lo que puede bloquear escrituras durante su construccion en tablas grandes. Evaluar una variante operacional CONCURRENTLY fuera de transaccion si el volumen lo requiere.

Reversion de aplicacion: los campos adicionales permiten volver al binario previo. No borrar timestamps de pagos realizados para simular rollback: el calculador anterior no los conoce y podria volver a descontarlos. Reconciliar el plan antes de volver al calculador anterior. No se realizo despliegue ni reversion.

## Pruebas reales

- La primera ejecucion Maven restringida fallo por acceso a dependencias. La ejecucion con acceso permitido a la cache y Maven Central funciono.
- `cd backend; mvn -B test`: 165 pruebas, 0 fallos/errores tras agregar las integraciones iniciales.
- `cd backend; mvn -B package`: 170 pruebas, 0 fallos, 0 errores, 0 omitidas; JAR empaquetado correctamente. Incluye 4 pruebas de integracion nuevas y 4 unitarias nuevas del asistente, ademas de la regresion unitaria del caso obligatorio.
- Repeticion final `mvn -B -o package`: BUILD SUCCESS y codigo nativo 0; reportes Surefire verificados: 170 pruebas, 0 fallos, 0 errores. PowerShell habia tratado las advertencias Java en stderr como error del envoltorio; se comprobo explicitamente `$LASTEXITCODE`.
- `cd frontend; npm test -- --watch=false --browsers=ChromeHeadless`: 77 pruebas correctas. La primera ejecucion tuvo 11 fallos; se actualizaron selectores que apuntaban al dashboard anterior, se mantuvo cobertura funcional sobre el DOM actual y se agregaron pruebas de filtros enviados, cancelacion, estados, pago realizado y hora Lima.
- Integracion H2 en memoria (modo PostgreSQL): aislamiento entre usuarios, autenticacion, rangos invalidos, tamano de pagina, orden estable, filtros reales y nombres renombrados, monedas independientes, saldos antiguos/desactualizados, pago completado atomico y duplicados rechazados.
- Unitarias: preguntas no soportadas sin consultas financieras, tildes, moneda invalida, exclusion de ingresos y moneda desconocida separada, pagos vencidos y redondeo del limite diario.
- Angular: contratos HTTP, carga/error/vacio y cancelacion de historial, filtros/paginacion, finalizacion que exige saldo, errores recuperables, hora de Lima y estados antiguos; responsive en 1440/1024/768/390 px.
- `cd frontend; npm run build`: correcto. Bundle inicial 534.70 kB frente al aviso de 500 kB; CSS del landing 14.84 kB frente al aviso de 14 kB. Son advertencias, no errores; no se cambiaron los presupuestos para ocultarlas.
- `git diff --check`: correcto, sin errores de espacios.

Los logs locales estan en `.qa/production-backend-tests.log`, `.qa/production-backend-package.log`, `.qa/production-frontend-tests.log` y `.qa/production-frontend-build.log` (artefactos locales ignorados por Git).

## Riesgos y pendientes

- No hay PostgreSQL/psql ni Docker disponibles en PATH: no se valido DDL en un servidor PostgreSQL real. Los tests de integracion usan H2; hace falta ensayo de migracion sobre una copia de desarrollo antes de autorizar despliegue.
- El umbral de 24 horas es una decision implementada y documentada; la advertencia se calcula al consultar el estado. Una pesta?a abierta no recalcula la antiguedad sin recargar.
- La proyeccion depende de lo confirmado por el usuario; no verifica el saldo contra un banco. Marcar realizado requiere el saldo real completo, no solo el importe del pago.
- La paginacion por offset puede desplazarse si entran movimientos nuevos mientras se navega; el orden es estable para un conjunto fijo. No constituye una instantanea congelada.
- Los nombres de categorias inactivas se resuelven desde catalogo en resultados; el selector de categorias sigue usando el catalogo activo del producto.
- El asistente es determinista y admite exclusivamente las preguntas enumeradas; no comprende lenguaje libre ni otras fechas.
- No se probo Gmail real, aprobacion OAuth externa, navegacion contra cuentas externas ni datos de produccion. Las pruebas responsive son en navegador headless con fixtures.
- Los demas hallazgos del documento externo quedan pendientes de recibir su contenido. No se declara la auditoria completa ni una aprobacion de produccion.
- No se hicieron push, merge, despliegues AWS ni modificaciones de datos de produccion.

## Archivos modificados o creados

- `backend/sql/V2_5__production_readiness.sql`
- `backend/sql/verify_production_readiness.sql`
- `backend/src/main/java/com/finora/app/assistant/AssistantDtos.java`
- `backend/src/main/java/com/finora/app/assistant/AssistantService.java`
- `backend/src/main/java/com/finora/app/planning/PlanningController.java`
- `backend/src/main/java/com/finora/app/planning/PlanningPayment.java`
- `backend/src/main/java/com/finora/app/planning/PlanningPaymentRepository.java`
- `backend/src/main/java/com/finora/app/planning/PlanningProfile.java`
- `backend/src/main/java/com/finora/app/planning/PlanningProfileRepository.java`
- `backend/src/main/java/com/finora/app/planning/PlanningService.java`
- `backend/src/main/java/com/finora/app/planning/PlanningStateService.java`
- `backend/src/main/java/com/finora/app/transaction/TransactionController.java`
- `backend/src/main/java/com/finora/app/transaction/TransactionService.java`
- `backend/src/test/java/com/finora/app/ReadinessIntegrationTest.java`
- `backend/src/test/java/com/finora/app/assistant/AssistantServiceTest.java`
- `backend/src/test/java/com/finora/app/planning/PlanningServiceTest.java`
- `docs/PRODUCTION_READINESS_2026_11_04.md`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.spec.ts`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/core/models/api.models.ts`
- `frontend/src/app/core/services/api.service.ts`
- `frontend/src/app/core/services/planning-http.spec.ts`
- `frontend/src/app/planning/planning.component.css`
- `frontend/src/app/planning/planning.component.html`
- `frontend/src/app/planning/planning.component.spec.ts`
- `frontend/src/app/planning/planning.component.ts`
- `frontend/src/app/premium-layout.spec.ts`
- `frontend/src/app/premium.test-fixtures.ts`
