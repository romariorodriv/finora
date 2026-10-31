# Integración Sarela 2.2

Fecha: 30 de octubre de 2026. Rama: `feature/sarela-2.2`.
Repositorio: `C:\Users\Romario\secondMind\finora-sarela`.
Referencia: `C:\Users\Romario\Downloads\SARELA_2_2_PERSISTENCIA_ALERTAS.zip`.
La carpeta originalmente abierta y la copia SARELA_2_1_DISENO_Y_PLANIFICACION no contienen Git. Los cambios se aplicaron al repositorio que ya estaba en la rama solicitada.

## Funcionalidad implementada

- Backend planning importado del ZIP y adaptado al manejo de errores actual: perfil privado por usuario, alta/listado/borrado de pagos pendientes, cálculo de disponibilidad y orientación diaria, alertas de déficit, vencimiento próximo, pagos vencidos y fecha de ingreso caducada.
- Autenticación JWT existente en `/api/planning` y `/api/v1/planning`; el propietario se obtiene del principal autenticado, nunca del cuerpo enviado. Borrar un pago ajeno devuelve 404 y no revela su existencia.
- Importes BigDecimal con validación de escala y magnitud, incluido desbordamiento por notación científica; fechas de estado en America/Lima. JSON o fechas inválidas devuelven 400.
- Frontend integrado mediante componente independiente en el dashboard y menú actual. Conserva categorías, suscripciones/recurrentes, feedback, clasificación y detalle de comercios, transacciones, dashboard e integración Gmail. No se reemplazaron esos módulos por las versiones del ZIP.
- El plan se recupera del servidor al entrar. Las solicitudes pendientes se cancelan al salir de la vista y los botones impiden mutaciones duplicadas. Navegación móvil adaptada a las cinco opciones.
- Migración manual PostgreSQL `backend/sql/V2_2__planning.sql`: planning_profiles (usuario único) y planning_payments, FK a app_users(id) con ON DELETE CASCADE, índice por usuario y restricciones de importes. Las entidades también declaran las relaciones para esquemas locales generados por Hibernate. No se agregó un ejecutor automático de migraciones.

La disponibilidad sigue la semántica de la referencia: saldo confirmado menos reserva y pagos desde hoy, inclusive, hasta antes del próximo ingreso. Los pagos anteriores a hoy generan advertencias y no se descuentan automáticamente; la pantalla lo indica. El límite diario se redondea hacia abajo y nunca es negativo. Las alertas se recalculan al consultar el estado; no son notificaciones por email ni procesos programados. Borrar un pendiente no crea automáticamente una transacción bancaria.

## Validación ejecutada

| Validación | Resultado |
|---|---|
| `mvn clean package` | PASS: 84 pruebas, 0 fallos, 0 errores, 0 omitidas; JAR generado |
| Autorización HTTP con JWT de dos usuarios | PASS: perfiles y pagos aislados, propietario enviado en body ignorado, borrado ajeno 404, ambas rutas protegidas con 403 sin autenticación |
| Persistencia | PASS: guardado y lectura después de flush/clear del EntityManager |
| Cálculo y errores | PASS: reserva, fechas límite, déficit, redondeo diario, importes inválidos y desbordamientos |
| `npm test -- --watch=false --browsers=ChromeHeadless` | PASS: 9 pruebas, ejecutadas con Edge headless mediante CHROME_BIN |
| `npm run build -- --configuration production` | PASS: bundle inicial 392.09 kB, sin superar presupuestos |
| PostgreSQL 17 temporal, 127.0.0.1:55439 | PASS: migración ejecutada dos veces; tablas, FK, unicidad, checks y cascada comprobados |
| `git diff --check` | PASS |
| `npm audit` | 42 → 38 hallazgos; críticos 4 → 0 |
| `npm audit --omit=dev` | 7 pendientes: 4 altos y 3 moderados |

La configuración nueva de pruebas usa H2 en memoria y desactiva los datos demo. No se usó la base H2 en disco existente ni AWS. La validación PostgreSQL utilizó un clúster desechable en TEMP, detenido al terminar. El script `backend/sql/verify_planning.sql` reproduce la verificación sobre una base local vacía: `psql -v ON_ERROR_STOP=1 -f backend/sql/verify_planning.sql`. Nunca debe ejecutarse sobre una base con datos existentes.

Los tests del componente verifican renderizado, recuperación del plan, validación antes de enviar, solicitudes duplicadas, errores y cancelación. No se realizó una revisión visual manual completa en navegadores/dispositivos; queda pendiente antes de producción.

## Seguridad npm

Se corrigieron los cuatro críticos con overrides compatibles: piscina 4.9.4, proxy-addr 2.0.8, shell-quote 1.11.0 y pacote 20.0.1 (que incorpora tar 7.5.22). No se usó --force ni --legacy-peer-deps. Angular sigue en la versión mayor 19 y TypeScript 5.7.3. Consultar [revisión completa por paquete](SARELA_2_2_NPM_AUDIT.md) para cadenas afectadas, avisos y compatibilidad.

## Pendiente para producción

1. Resolver los 38 hallazgos npm restantes mediante una actualización coordinada de Angular y herramientas; 7 pertenecen a dependencias de ejecución. No considerar este resultado libre de vulnerabilidades.
2. Ensayar la migración sobre una copia representativa de PostgreSQL, revisar esquema existente y preparar backup y recuperación. La verificación local utilizó un esquema limpio.
3. Aplicar V2_2__planning.sql en el destino únicamente dentro del proceso autorizado de publicación, antes del backend con ddl-auto=validate. Esta sesión no ejecutó migraciones en AWS ni despliegues.
4. Completar QA visual desktop/móvil y un recorrido con cuentas reales de staging, incluida persistencia entre sesiones.
5. Mantener la configuración de producción existente: secretos propios de JWT/Gmail, DB y CORS; datos demo desactivados. Los comandos Maven usaron el JDK 25 disponible con release 21; validar también el entorno JDK 21 utilizado en producción y atender las advertencias de agentes Mockito/Jansi si se actualiza el toolchain.

No se modificó main, no se hizo commit/push y no se desplegó. La carpeta .qa/ ya estaba sin seguimiento antes de comenzar y se dejó intacta.

## Inventario exacto

### Nuevos (19)

- `backend/sql/V2_2__planning.sql`
- `backend/sql/verify_planning.sql`
- `backend/src/main/java/com/finora/app/planning/PlanningController.java`
- `backend/src/main/java/com/finora/app/planning/PlanningPayment.java`
- `backend/src/main/java/com/finora/app/planning/PlanningPaymentRepository.java`
- `backend/src/main/java/com/finora/app/planning/PlanningProfile.java`
- `backend/src/main/java/com/finora/app/planning/PlanningProfileRepository.java`
- `backend/src/main/java/com/finora/app/planning/PlanningService.java`
- `backend/src/main/java/com/finora/app/planning/PlanningStateService.java`
- `backend/src/test/java/com/finora/app/planning/PlanningAuthorizationTest.java`
- `backend/src/test/java/com/finora/app/planning/PlanningServiceTest.java`
- `backend/src/test/java/com/finora/app/planning/PlanningStateServiceTest.java`
- `backend/src/test/resources/application.properties`
- `docs/SARELA_2_2_INTEGRATION.md`
- `docs/SARELA_2_2_NPM_AUDIT.md`
- `frontend/src/app/planning/planning.component.css`
- `frontend/src/app/planning/planning.component.html`
- `frontend/src/app/planning/planning.component.spec.ts`
- `frontend/src/app/planning/planning.component.ts`

### Modificados (8)

- `backend/src/main/java/com/finora/app/shared/error/GlobalExceptionHandler.java`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/app/app.component.css`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/core/models/api.models.ts`
- `frontend/src/app/core/services/api.service.ts`
