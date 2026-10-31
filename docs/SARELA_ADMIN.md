# Panel administrador de Sarela
Entrega local del 30/10/2026 en feature/sarela-2.2-premium-ui.
Repositorio: C:/Users/Romario/secondMind/finora-sarela.

## Implementación
/admin reúne categorías, consumos, reglas e historial. El guard consulta /api/v1/account y todos los endpoints administrativos exigen ADMIN en backend. El rol se consulta en base de datos con cada petición autenticada; un JWT anterior no conserva permisos revocados. El registro siempre crea USER.

El catálogo persistente admite nombre, descripción, icono, color, grupo y estado activo, valida duplicados normalizados y muestra cantidades. No hay borrado: desactivación o reasignación con vista previa. El dashboard y catálogo habituales reflejan las categorías administradas.

Los consumos se buscan y filtran en servidor con paginación, fechas, categoría, cuenta y revisión. Incluye pendientes, selección individual/masiva, confirmaciones y totales por moneda. Se conserva la descripción original. La vista administrativa redacta correos y patrones sensibles; no expone tokens ni credenciales.

Corregir y crear regla son acciones separadas. La sugerencia de regla usa alcance por cuenta; el alcance global requiere elección y confirmación explícitas. Prioridad: EXACT antes de PREFIX, cuenta antes de global, prioridad mayor, patrón más largo e ID menor. Reutiliza MerchantNormalizer. No hay coincidencias automáticas por contiene, IA ni servicios de pago.

Las reglas nuevas actúan sobre nuevas clasificaciones automáticas (importaciones o entradas Otros), sin modificar historial. Una categoría elegida manualmente prevalece. La aplicación histórica requiere vista previa, ejemplos, alcance y confirmación; excluye correcciones manuales. Auditoría por administrador, fecha, valores anteriores/nuevos y lote. Aplicación y reversión atómicas con snapshots/versiones: un cambio posterior provoca rechazo completo.

Las monedas se mantienen separadas; dashboard permite seleccionarlas. Planificación conserva su referencia PEN. Los datos históricos sin moneda quedan UNK, sin inventar PEN/USD.

## Archivos nuevos
- backend/src/main/java/com/finora/app/admin/: entidades, repositorios, DTO, motor, servicio, controlador e inicializador.
- backend/src/main/java/com/finora/app/auth/AccountController.java.
- backend/sql/V2_3__admin_classification.sql.
- backend/sql/grant_admin.sql.
- backend/sql/verify_admin_classification.sql.
- backend/src/test/java/com/finora/app/admin/AdminIntegrationTest.java.
- frontend/src/app/admin/: componente, HTML/CSS, servicio, guard y pruebas.
- docs/SARELA_ADMIN.md.

## Archivos modificados para administración
Backend:
- auth/{AuthDtos,AuthService,User}.java.
- config/{JwtFilter,SecurityConfig}.java.
- dashboard/{DashboardController,DashboardResponse,DashboardService}.java.
- gmail/service/GmailSyncService.java e importer/ImportService.java.
- shared/ApiController.java y shared/error/GlobalExceptionHandler.java.
- transaction/{Transaction,TransactionRepository,TransactionRequest,TransactionResponse,TransactionService}.java.
- test: TransactionServiceTest.java.

Frontend:
- app.component.{ts,html,css}, app.routes.ts.
- core/dashboard.utils.ts, core/models/api.models.ts.
- core/services/{api.service,auth.service}.ts.
- Pruebas/fixtures del componente principal, landing y premium-layout para router/rol.

Se conservaron los cambios previos de landing, imágenes, planificación, diseño premium y .qa/. git status --short muestra el inventario completo. No se hicieron commits.

## Base local y habilitación ADMIN
La migración es manual y aditiva. Usa una base PostgreSQL LOCAL con el esquema anterior y migraciones anteriores, incluida V2_2 cuando corresponda. Haz copia de seguridad antes de probarla. Sustituye los valores de ejemplo; -W solicita contraseña sin incluirla en el comando.

Desde la raíz:
~~~powershell
psql -h 127.0.0.1 -p 5432 -U operador_local -d sarela_local -W -v ON_ERROR_STOP=1 -f backend/sql/V2_3__admin_classification.sql
psql -h 127.0.0.1 -p 5432 -U operador_local -d sarela_local -W -v ON_ERROR_STOP=1 -v admin_email='tu-correo-existente' -f backend/sql/grant_admin.sql
~~~

La cuenta debe existir. El script exige exactamente una coincidencia, bloquea la fila y registra la promoción y el operador. No cambia contraseñas. No existe endpoint público para promover cuentas. Recarga o inicia sesión para ver Administración.

Configura DB_URL, DB_USERNAME y DB_PASSWORD para esa base local, JPA_DDL_AUTO=validate después de migrar, DEMO_DATA=false y secretos propios. Estos comandos no deben apuntar a producción.

El backend abierto antes del trabajo mantiene bloqueado el JAR anterior y usa H2 en memoria. No se detuvo, porque perdería sus datos al cerrar. Conserva lo necesario antes de reiniciarlo. grant_admin.sql es para PostgreSQL, no H2.

## Arranque y recorrido local
Se deja backend/target/finora-admin-preview.jar, compilado desde una copia temporal con fuentes verificadas byte a byte. Después de preparar la base y liberar 8080:
~~~powershell
java -jar backend/target/finora-admin-preview.jar
~~~
Otra terminal:
~~~powershell
cd frontend
npm start -- --host 127.0.0.1
~~~
Abre http://localhost:4200/admin. apiBaseUrl apunta a localhost:8080/api/v1; cambiar PORT también requiere adaptar esa URL.

Revisa: crear categoría, abrir consumos, pendientes, corrección individual y masiva, creación explícita de regla por cuenta, nuevo consumo automático, vista previa histórica, cancelar/confirmar, auditoría y reversión. Comprueba rechazo con USER y revisa dashboard/planificación.

## Validación
- mvn clean package: 97 pruebas, cero fallos/errores/omitidas, incluidas 13 integraciones de administración.
- El JAR abierto bloqueó clean en el repositorio. Se ejecutó clean package en una copia temporal idéntica de pom.xml y src y se copió el nuevo artefacto con otro nombre.
- Angular/Karma con Edge headless: 51 pruebas correctas, incluidos guard, rol obsoleto, confirmaciones, reglas y layouts en cuatro anchos.
- npm run build -- --configuration production: correcto. 490.52 kB iniciales, módulo admin diferido 48.47 kB, sin comprimir.
- PostgreSQL 17 desechable local: verify_admin_classification.sql pasó, incluida repetición de migración, conservación de datos, asociaciones, claves foráneas, unicidad, alcance, rol por defecto, promoción y auditoría.
- git diff --check correcto.

Las integraciones cubren anónimo/USER sin acceso administrativo, registro sin autopromoción, revocación con JWT existente, categorías, aislamiento habitual, paginación, monedas, correcciones, reglas futuras/prioridades, protección manual, preview/aplicación/reversión, rollback completo por concurrencia y redacción sensible.

## Límites y pendientes
- Preview caduca en 15 minutos; examina hasta 100000 filas y afecta hasta 2000. Corrección hasta 200 seleccionadas. Páginas hasta 100 filas. Acotar filtros para volúmenes mayores.
- No se reconstruyen monedas históricas sin fuente verificable.
- Falta revisión visual humana de página completa; las pruebas responsive automatizadas no la sustituyen.
- Antes de producción: revisión humana, backup, migración ensayada sobre copia representativa, configuración/secretos propios y autorización explícita de despliegue/migración.
- No se cambiaron dependencias npm en esta tarea. Véase SARELA_2_2_NPM_AUDIT.md para riesgos anteriores aún pendientes.
- No se desplegó, no se migró AWS/producción, no se cambió main y no se promovió ninguna cuenta real.
