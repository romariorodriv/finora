# Sarela — eliminación de cuenta en autoservicio (NO DESPLEGADO)

## Comportamiento diseñado
- La opción «Eliminar mi cuenta y mis datos» está en Conexión Gmail > Tus datos.
- Se exige sesión cookie propia, mismo Origin que el frontend y la confirmación explícita exacta `ELIMINAR`. No basta con un JWT ni un POST externo.
- En una única transacción de base de datos, se revoca/desconecta Gmail (revocación externa best effort), se cancelan importaciones y se borran mensajes importados, cursores, tokens, trabajos, movimientos, planificación, feedback, reglas privadas, auditorías pertinentes, sesiones y finalmente el usuario.
- Solo se devuelve `DELETED` cuando la transacción termina sin errores; ante errores en BD se revierte la eliminación local y el navegador mantiene la cuenta. La revocación remota de Gmail no puede deshacerse si la transacción local falla.
- Al concluir se limpia la cookie HttpOnly y el frontend recarga en la landing, mostrando confirmación de eliminación.
- Si el mismo Google subject/correo inicia sesión después, se registra con **nuevo ID y cuenta vacía**, sin restaurar movimientos ni permisos anteriores. Deberá volver a autorizar Gmail si lo desea.
- Las cuentas con rol ADMIN se rechazan con código `ADMIN_OWNERSHIP_TRANSFER_REQUIRED` para proteger acciones y registros compartidos; hay que transferir responsabilidades antes de usar el flujo normal.
- **No se eliminan mensajes en Gmail.** Las copias de seguridad de PostgreSQL, logs e integraciones externas requieren políticas de retención y borrado separadas. No se promete borrado instantáneo de respaldos.

## Archivos principales
- `backend/src/main/java/com/finora/app/privacy/AccountDeletionService.java`: limpieza transaccional de datos por propietario.
- `backend/src/main/java/com/finora/app/privacy/PrivacyController.java`: prueba de origen/cookie y respuesta de cuenta eliminada.
- `backend/src/main/java/com/finora/app/gmail/jobs/GmailMessageProcessor.java`: bloqueo consistente del usuario para evitar nuevas escrituras cuando se elimina.
- `backend/sql/V2_6__self_service_deletion_integrity.sql`: FKs de pertenencia a usuarios para que nuevas escrituras huérfanas sean rechazadas. Requiere revisión manual de esquema y respaldo antes de aplicar.
- `frontend/src/app/app.component.html`, `.ts`, `core/services/api.service.ts`: aviso irreversible, confirmación exacta, respuesta y vuelta a landing.
- `frontend/public/privacidad.html`, `terminos.html`, `eliminar-datos.html`: contenido actualizado para la funcionalidad **cuando sea efectivamente desplegada**.

## QA obligatorio antes de producción
1. Ejecutar en Windows `cd backend && mvn -B clean verify` y `cd frontend && npm ci && npm test -- --watch=false --browsers=ChromeHeadless && npm run build && npm run qa:domain && npm run qa:legal && npm run qa:brand`.
2. Restaurar copia de base de datos productiva con información anonimizada a ambiente aislado de ensayo; no usar la BD real para eliminación de prueba.
3. Ejecutar V2_6 en staging y revisar todas las FKs existentes, bloqueos y posibles filas huérfanas.
4. Crear dos usuarios USER distintos con transacciones, planificación, conexión Gmail, mensajes y feedback de ensayo. Eliminar solo uno. El otro debe permanecer idéntico.
5. Probar rechazo por falta de confirmación, cookie ausente, origen ajeno y rol ADMIN.
6. Verificar que con el token/cookie antiguo la API responde 401, que no queda información propia en tablas activas y que un nuevo Google Login recrea cuenta vacía.
7. Ejecutar eliminación mientras Gmail importa; no deben reaparecer movimientos huérfanos. Revisar archivos de logs y servicios externos.
8. Comprobar rollback en caso de FK no prevista y que nada se borra parcialmente; tomar snapshots y validar proceso de recuperación.
9. Revisar la redacción legal de eliminación y conservación de backups y comprobar sus URLs ya publicadas.

## Liberación
No ejecutar los scripts contra producción hasta pasar las pruebas con PostgreSQL real. Crear backup verificado; aplicar la migración por separado; publicar backend y frontend de la misma rama; ejecutar QA de eliminación con una cuenta de prueba sin datos personales. Confirmar el comportamiento en la app y actualizar CloudFront al final.

## Estado actual
Implementación en rama de GitHub, sin compilar ni ejecutar tests en la máquina de desarrollo en esta operación, no desplegada. La aplicación pública sigue conservando el flujo anterior hasta que se publique esta versión.
