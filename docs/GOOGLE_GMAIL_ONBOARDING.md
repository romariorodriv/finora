# Sarela: acceso Google e importación Gmail
Fecha: 31/10/2026. Rama de trabajo: feature/sarela-2.2-premium-ui.
Implementación local preparada para revisión. No se desplegó ni se ejecutó SQL en AWS.

## Diagnóstico y alcance
Antes existían login/registro por contraseña, JWT en localStorage, autorización Gmail separada y sincronización durante la petición HTTP, con hasta cinco páginas y sin checkpoint persistente. Los errores de renovación de Google podían terminar en un 500 genérico. El servidor real también mostró columnas y versiones históricas incompletas.

Ahora el acceso público es Google; las cuentas anteriores requieren vinculación con prueba de titularidad. Gmail se autoriza después, opcionalmente, y dispara un trabajo persistente. Se reutilizan Spring Boot/JPA, Angular, TokenCipher, parsers, catálogo y reglas administrativos. No se añade infraestructura AWS ni servicios de IA.

## Acceso y seguridad
- POST /api/v1/auth/google/start inicia OAuth/OIDC con state aleatorio de un solo uso, prueba del navegador, nonce y PKCE S256. Caducidad: diez minutos.
- GET /api/v1/auth/google/callback intercambia el código solo en backend. Nimbus valida firma RS256; además se comprueban emisor Google, aud/azp, fechas, nonce, sub y email verificado. No se entrega el ID token ni el access/refresh token a JavaScript.
- sub es único y estable. Un sub conocido mantiene el mismo usuario aunque cambie el correo presentado por Google; el correo guardado no se reescribe automáticamente.
- Si el correo coincide con una cuenta anterior sin sub, se exige la contraseña anterior mediante /auth/google/link. No se fusiona únicamente por correo. Se conservan ID, datos y rol ADMIN. Máximo cinco intentos por flujo y diez por cuenta en aproximadamente una hora. Recuperación sin contraseña: intervención del operador con comprobación adicional de titularidad, nunca un UPDATE basado solo en una coincidencia de correo.
- Una cuenta nueva siempre nace USER. Los endpoints públicos de contraseña /auth/login y /auth/register devuelven 410 GOOGLE_SIGN_IN_REQUIRED.
- Sesión opaca de ocho horas en cookie __Host-sarela_session, Secure, HttpOnly, SameSite=Lax, Path=/ y sin Domain en producción. En base de datos solo se guarda su hash. Logout revoca esa sesión. El rol se consulta en cada petición.
- Las mutaciones autenticadas por cookie requieren Origin exacto del frontend. CORS usa ese origen explícito y permite credenciales. Los callbacks GET se protegen por state y cookie de navegador; Gmail también usa PKCE.
- El frontend elimina los antiguos tokens de localStorage, restaura /account y utiliza withCredentials solo para su API. Los Bearer JWT anteriores siguen admitidos en backend para compatibilidad, sujetos a firma, expiración y rol actual; no se emiten mediante el acceso público nuevo.
- JWT_SECRET y TOKEN_ENCRYPTION_KEY son obligatorios fuera del perfil local. No usar valores de demostración. Mantener la clave de cifrado existente para poder leer los tokens guardados. Si el JWT anterior utilizaba una clave pública de ejemplo, reemplazarla por una fuerte e invalidar esas sesiones antiguas.
- Los flujos y sesiones vencidos se limpian periódicamente. Passwords y records internos de tokens tienen representación de log redactada. Los errores Google solo conservan códigos permitidos y estado HTTP, no cuerpos ni descripciones que puedan contener secretos.

## Gmail, consentimiento e importación
El acceso OIDC solicita openid email profile. La conexión Gmail solicita únicamente https://www.googleapis.com/auth/gmail.readonly, acceso offline y consentimiento explícito. No usa APIs de envío, modificación, borrado ni descarga de adjuntos. La cuenta Gmail seleccionada se muestra claramente; puede diferir de la identidad usada para iniciar sesión. Seleccionar otra retira las credenciales activas anteriores sin borrar sus movimientos.

Antes de salir a Google se explica qué lee el sistema, la ventana inicial, los datos guardados, los formatos limitados y la desconexión. Rechazar Gmail no cierra la sesión de Sarela ni vuelve a iniciar OAuth automáticamente.

El callback Gmail válido guarda credenciales cifradas y encola la primera importación:
- Primera ejecución: 90 días, también para conexiones antiguas sin un trabajo nuevo registrado.
- Incremental: consultas periódicas cada 60 minutos y solapamiento de 48 horas desde la última ventana completada. No se usa Pub/Sub ni una nueva infraestructura.
- Las fechas de búsqueda usan segundos Unix para evitar ambigüedad de zona horaria. [Búsqueda Gmail](https://developers.google.com/workspace/gmail/api/guides/filtering).
- Cada página, reserva y resultado se persiste. Los mensajes se procesan en transacciones independientes. Una caída no obliga a duplicar lo ya guardado.
- Una fila de trabajo por usuario, bloqueo de usuario al encolar y reserva de 120 segundos con heartbeat impiden superposición entre peticiones y procesos. Una reserva vencida se recupera; un trabajador sustituido ya no puede escribir.
- Página predeterminada: 50 mensajes. Límite de 1000 páginas por tramo. Alcanzarlo produce PARTIAL, conserva cursor y permite continuar el mismo trabajo, sin afirmar que se completó.
- HTTP 429/5xx y cuotas conocidas usan hasta cinco reintentos con espera creciente. Errores permanentes de configuración no generan una reconexión inútil; permisos revocados marcan REAUTHORIZATION_REQUIRED.
- Fallos locales de un mensaje se reintentan de forma limitada; tras tres intentos queda en revisión. Un mensaje ambiguo o sin fecha no genera una transacción inventada.
- Estado real: NOT_STARTED, PENDING, IMPORTING, COMPLETED, PARTIAL, ERROR o CANCELLED. El frontend consulta progreso, periodo, última actividad y conteos; una importación vacía pendiente o fallida no se muestra como ausencia de gastos.
- COMPLETED significa que se terminó de revisar el conjunto candidato, no que se haya obtenido todo el historial bancario. Los mensajes no compatibles y pendientes se cuentan aparte.
- La lectura usa términos de notificaciones financieras, no una conexión bancaria. No garantiza detectar mensajes que no cumplan la búsqueda o formatos no soportados, ni correos retrodatados que queden fuera de la ventana incremental.

POST /integrations/gmail/sync devuelve 202 y el trabajo actual; repetirlo mientras está activo devuelve el mismo trabajo. GET /integrations/gmail/sync/status devuelve progreso solo del usuario autenticado. GET /integrations/gmail/review?page=0 lista metadatos propios con paginación de 20. Los alias /api y /api/v1 se conservan.

### Duplicados y correcciones
Los mensajes se identifican por usuario, conexión/mailbox e ID Gmail; el ID de una transacción nueva es gmail:<connectionId>:<messageId>. Hay unicidad de origen por usuario y de resultado por trabajo/mensaje. La migración sustituye la unicidad antigua demasiado amplia. Se reconocen registros heredados; los ya importados no se sobrescriben, por lo que se conservan correcciones manuales y borrados deliberados. Las reglas del catálogo se aplican a nuevas clasificaciones automáticas.

No se deduplican dos mensajes distintos mediante una supuesta identidad bancaria inexistente en el formato: diferentes notificaciones de una misma operación pueden requerir revisión humana.

## Formatos realmente compatibles
El parser cubre patrones probados de BCP en PEN; no es un conector universal ni una validación criptográfica del remitente. Parte de los patrones se reconoce por texto y parte por remitente/formato.

| Formato probado | Tratamiento |
| --- | --- |
| Consumo con tarjeta BCP, comercio e importe reconocibles | EXPENSE |
| Compra rechazada / fondos insuficientes | REJECTED; sin gasto |
| Transferencia a otros bancos o entre cuentas propias | TRANSFER; fuera de gasto/ingreso del dashboard |
| Retiro de cajero | CASH_WITHDRAWAL; separado |
| Aporte/retiro de Wardadito | SAVINGS_DEPOSIT / SAVINGS_WITHDRAWAL; separados |
| Devolución BCP reconocible | REFUND; separada, no se suma como otra compra |
| Abono de sueldo / pago de haberes explícito del remitente BCP soportado | INCOME |
| Otro banco, plantilla desconocida o moneda no compatible | No interpretado o revisión; sin conversión inventada |
| Importes contradictorios, moneda mixta, fecha ausente o comercio ambiguo | Revisión |

Se corrige la lectura de miles: 1,234.56 no se interpreta como 1.23. Se prioriza el importe explícito de operación sobre un saldo mencionado aparte. No se convierten divisas. Los datos históricos UNK se conservan hasta una conciliación separada y verificable.

El parser no infiere la fecha bancaria: Gmail usa internalDate de Google, guardando date_source=EMAIL_RECEIVED; si falta, se revisa. El importador manual heredado exige date explícita y guarda USER_PROVIDED; su respuesta ya no anuncia una IA inexistente.

## Privacidad y eliminación
Responsable indicado: SARELA. Soporte/privacidad: plandeuda@gmail.com.
Páginas estáticas exportables: /privacidad.html, /terminos.html, /eliminar-datos.html.

Se guardan movimientos, IDs y metadatos necesarios de mensajes; no el cuerpo íntegro. Desconectar intenta revocar el permiso, borra las credenciales de todas las conexiones del usuario, incluidas las antiguas y detiene el trabajo; conserva datos ya importados.

POST /account/deletion-request con confirmed=true registra una solicitud autenticada, desconecta Gmail y bloquea nuevas importaciones mientras esté pendiente. La eliminación es MANUAL: la respuesta y las páginas no afirman que ya se hayan borrado los datos. El operador debe establecer procedimiento, plazo, retención de copias y revisión de datos/auditoría antes de la apertura pública. Solicitudes disponibles para un operador de base de datos mediante SELECT user_id,status,requested_at FROM data_deletion_requests WHERE status='PENDING'. No marcar COMPLETED sin haber tramitado la solicitud.

## Configuración Google Cloud: acciones del propietario
No se ha accedido a tu consola ni se han cambiado sus ajustes.
1. Usar un proyecto y cliente OAuth de tipo aplicación web adecuados para producción; habilitar Gmail API. Mantener un cliente/proyecto separado para desarrollo si corresponde.
2. En Google Auth Platform, completar Branding con SARELA, soporte/desarrollador plandeuda@gmail.com, página principal https://sarela.pgoapp.com/, privacidad https://sarela.pgoapp.com/privacidad.html y términos https://sarela.pgoapp.com/terminos.html. Publicar primero las páginas revisadas y verificar la propiedad de los dominios necesarios.
3. Audiencia externa para personas ajenas a tu organización. Mientras se prueba Gmail, mantener los usuarios de prueba necesarios.
4. Origen JavaScript si la consola lo solicita: https://sarela.pgoapp.com. Este flujo es de servidor y no utiliza un secreto en JavaScript.
5. Registrar exactamente DOS redirecciones:
   - https://api-sarela.pgoapp.com/api/v1/auth/google/callback
   - https://api-sarela.pgoapp.com/api/v1/integrations/gmail/callback
6. Declarar openid, email, profile y gmail.readonly, sin scopes de escritura. Configurar las credenciales únicamente en el backend.
7. Solicitar verificación de marca y de scopes restringidos cuando corresponda. Publicar In production NO sustituye esas revisiones. gmail.readonly es restringido; el acceso/procesamiento en el servidor exige la evaluación de seguridad aplicable, salvo una excepción confirmada por Google. Para este procesamiento de datos restringidos en el servidor, completar la evaluación CASA y su renovación anual, salvo exención confirmada por Google. No se ha realizado ni aprobado aquí.
8. Probar con cuentas de Gmail personales y las cuentas Workspace que se pretendan admitir; las políticas de administradores Workspace pueden impedir el consentimiento.

Referencias: [scopes Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes), [verificación restringida y evaluación](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification), [marca y dominios](https://developers.google.com/identity/verification/authentication-verification), [política Workspace](https://developers.google.com/workspace/workspace-api-user-data-developer-policy).

En Testing, los permisos Gmail externos pueden generar refresh tokens que vencen a los siete días; la excepción de scopes básicos de identidad no convierte Gmail en un permiso público aprobado. [Estado y expiración OAuth](https://developers.google.com/identity/protocols/oauth2).

## Base de datos y configuración
V2_4__google_onboarding_gmail_jobs.sql es aditiva, explícita y repetible. Añade identidad Google, sesiones, flujos, trabajos, resultados, solicitudes y origen de fecha; repara roles/metadatos nulos observados, conserva importes y monedas existentes y no vuelve a sembrar categorías. Debe aplicarse a la MISMA base del servicio antes del JAR nuevo.

Depende de las tablas existentes de las versiones previas. Si faltan, aborta y pide revisar el esquema. No ejecutar V2_3 a ciegas sobre un catálogo vaciado: contiene semillas. No se arreglan automáticamente referencias históricas de categorías borradas; se preservan para auditoría y se deben revisar por separado.

La migración aborta ante identidades/correos ambiguos o duplicados incompatibles; no los elimina ni fusiona. check_google_onboarding.sql comprueba tablas, columnas, roles, metadatos e índices/FK nuevos. Hibernate validate verifica el resto del modelo al arrancar; SchemaPreflight también rechaza metadatos históricos nulos.

Plantilla SIN secretos reales: google-onboarding.env.example. Fuera de local, usar perfil prod, JPA_DDL_AUTO=validate, cookies seguras, DEMO_DATA=false y CATEGORY_SEED_DEFAULTS=false. Mantener las claves existentes de cifrado y configurar GOOGLE_LOGIN_REDIRECT_URI además de GOOGLE_REDIRECT_URI. GMAIL_JOBS_ENABLED controla el worker y la consulta periódica.

## Ensayo y despliegue futuro en el AWS actual
Estos pasos NO se han ejecutado en producción. El cambio reemplaza el acceso público y el contrato de sync, por lo que backend y frontend deben publicarse coordinadamente después de las aprobaciones.

### Preparación
- Guardar/revisar los cambios en Git; obtener un commit concreto. No modificar main.
- Preparar staging aislado: desactivar workers y mantenimiento, retirar credenciales Google, sesiones y flujos copiados de producción y anonimizar datos antes de arrancar; usar clientes y cuentas Google de prueba propios. Crear una copia PostgreSQL con datos representativos, probar migración y arranque validate, y probar el flujo real de Google con las dos redirecciones de staging autorizadas.
- En el servidor actual, el servicio es sarela-backend.service y el JAR activo conocido es /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar.
- Comprobar rutas de EnvironmentFiles sin mostrar valores: sudo systemctl show sarela-backend.service --property=EnvironmentFiles. Editar los archivos privados existentes con sudoedit. No volcar secretos a terminal/chat ni sourcear archivos systemd como si fueran scripts.
- Conservar la configuración y el JAR anterior en /home/ubuntu/sarela-backups con permisos privados.
- No aplicar otra vez backend-logging.patch: el logger ya está incorporado en el código de esta entrega. El árbol original del servidor puede conservar su cambio local.

### Backend: comandos para una ventana futura autorizada
~~~bash
# Configurar COMMIT_REVISADO con el SHA completo revisado; preparar antes de detener el servicio.
git -C /home/ubuntu/finora fetch origin
git -C /home/ubuntu/finora worktree add --detach "/home/ubuntu/sarela-google-release-$COMMIT_REVISADO" "$COMMIT_REVISADO"
mvn -f "/home/ubuntu/sarela-google-release-$COMMIT_REVISADO/backend/pom.xml" clean package
~~~
Detenerse si la compilación o las pruebas fallan. Confirmar que el commit y la carpeta corresponden a la entrega.

~~~bash
umask 077
mkdir -p /home/ubuntu/sarela-backups
sudo -u postgres pg_dump -Fc finora > "/home/ubuntu/sarela-backups/finora-before-google-$(date +%Y%m%d-%H%M%S).dump"
cp /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar "/home/ubuntu/sarela-backups/backend-before-google-$(date +%Y%m%d-%H%M%S).jar"
sudo systemctl stop sarela-backend.service
sudo -u postgres psql -X -d finora -v ON_ERROR_STOP=1 -f "/home/ubuntu/sarela-google-release-$COMMIT_REVISADO/backend/sql/V2_4__google_onboarding_gmail_jobs.sql"
sudo -u postgres psql -X -d finora -v ON_ERROR_STOP=1 -f "/home/ubuntu/sarela-google-release-$COMMIT_REVISADO/backend/sql/check_google_onboarding.sql"
~~~
Si PostgreSQL no puede recorrer /home/ubuntu, copiar SOLO los SQL a un directorio temporal legible por postgres; no copiar allí credenciales. Confirmar que el backup pueda listarse con pg_restore -l y que ambos comandos SQL finalicen correctamente antes de seguir. Configurar el entorno privado antes de arrancar.

~~~bash
sudo cp -p /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar.next
sudo cp "/home/ubuntu/sarela-google-release-$COMMIT_REVISADO/backend/target/finora-mvp-0.1.0.jar" /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar.next
sudo mv /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar.next /home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar
sudo systemctl start sarela-backend.service
~~~
Esperar el arranque; comprobar is-active, /api/v1/health local y pública, y journalctl sin compartir secretos. El cambio no necesita reinicializar la base ni crear de nuevo un administrador.

Nginx/proxy debe conservar las rutas /api/v1/auth/google/* y /api/v1/integrations/gmail/*. Evitar registrar query strings de callbacks, cuerpos, Authorization o cookies. Los redirects usan las URLs configuradas; no requieren nuevos recursos AWS.

### Frontend y recuperación
Compilar npm run build -- --configuration production y publicar el contenido de frontend/dist/frontend/browser en el alojamiento estático existente. Conservar una copia/versionado del frontend anterior. No borrar de inmediato chunks antiguos que aún puedan usar sesiones abiertas; publicar assets primero e index.html al final. Las tres páginas legales deben publicarse también y estar disponibles sin sesión.

El bucket S3 y el ID de distribución CloudFront no aparecen en el repositorio. Se incluye frontend/scripts/publish-google-frontend.ps1 para descubrirlos mediante el alias exacto sarela.pgoapp.com, el perfil que indique el propietario y el origen por defecto. Respeta OriginPath, exige un único resultado y se detiene si no reconoce el origen S3. No adivina ni cambia recursos.

Desde PowerShell, después de compilar y con un perfil AWS expresamente elegido:
~~~powershell
# Solo lectura: identidad AWS, distribución, bucket/origen y plan.
./frontend/scripts/publish-google-frontend.ps1 -ProfileName PERFIL_SARELA
# Solo para una publicación futura revisada y autorizada:
./frontend/scripts/publish-google-frontend.ps1 -ProfileName PERFIL_SARELA -Apply
~~~
-Apply crea un respaldo local único, exige que contenga index.html, sube assets conservando chunks viejos, publica las páginas legales e índice al final e invalida los HTML de esa distribución. No se ha ejecutado ninguna de sus operaciones AWS en esta entrega. La ruta directa /admin sigue necesitando la reescritura SPA del alojamiento; el acceso por el menú usa navegación interna.

Rollback de aplicación: restaurar el JAR respaldado y el frontend anterior, mantener las columnas/tablas aditivas y reiniciar el servicio. No ejecutar DROP de las tablas nuevas ni restaurar con --clean sobre la base activa. Las cuentas creadas solo con Google no tendrán acceso mediante el frontend antiguo de contraseña hasta recuperar la versión Google. Si fuera necesario recuperar datos, ensayar pg_restore en una base NUEVA y revisar el cambio de conexión de forma explícita.

## Validación y límites antes de abrir al público
Resultados consolidados al cerrar la entrega:
- Suite backend completa y empaquetado Maven: 161 pruebas correctas, sin fallos, errores ni omisiones.
- Suite Angular headless: 66 pruebas correctas, incluidas cuatro anchuras de viewport (1440, 1024, 768 y 390).
- Build Angular production correcto; 503.90 kB iniciales (124.63 kB estimados de transferencia), aviso no fatal de 3.90 kB sobre el presupuesto de advertencia de 500 kB. No se ha subido ese presupuesto para ocultarlo.
- PostgreSQL 17 local desechable: tres variantes de esquema heredado (metadatos nulos, columna ausente e índice único antiguo), migración repetida y comprobación de roles, importes, monedas, catálogo vacío, propiedad y deduplicación.
- También se generó un esquema ORM completo en PostgreSQL, se introdujo una fila heredada con versión nula, se aplicó V2_4 y se arrancó la aplicación con ddl-auto=validate: /api/v1/health respondió 200 UP.
- El script de publicación se verificó sintácticamente y su modo de vista previa se probó con AWS simulado, conservando OriginPath y rechazando cualquier escritura. No se ha llamado a AWS desde el script.
- git diff --check correcto. No se probaron credenciales Google reales ni el consentimiento público; no se desplegó.

Docker compose sigue siendo exclusivamente de desarrollo, con puerto 8080 ligado a loopback y cookies HTTP locales. Una base local existente debe migrarse antes de usar validate; no es una receta de producción.

El flujo real con Google Cloud, las aprobaciones, la evaluación requerida, un ensayo con copia representativa de producción y la operativa de eliminación/backups siguen siendo tareas del responsable. Los tests usan Google simulado y firmas RSA de prueba; no equivalen a una aprobación de Google. Las vulnerabilidades npm anteriores documentadas en SARELA_2_2_NPM_AUDIT.md no se han resuelto mediante este cambio; no se añadieron dependencias npm ni se ejecutó audit fix --force.

Artefacto local del backend verificado: backend/target/finora-google-onboarding-preview.jar. Sus fuentes y pom.xml se compararon byte a byte con la compilación de 161 pruebas antes de copiarlo; no reemplaza el JAR usado por ningún servicio. Inventario completo: GOOGLE_GMAIL_CHANGESET.md.
