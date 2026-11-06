# Finora — Correcciones previas al video OAuth / Gmail
Fecha: 2026-11-05. Base: `fix/finora-production-readiness` (commit `d40d537`).

## Implementadas en esta rama
- **Backend / autenticación:** el entry point 401 declara `application/json;charset=UTF-8` y utiliza UTF-8. Anteriormente el JSON se servía como ISO-8859-1 y mostraba `Inicia sesin`.
- **Backend / Gmail:** los mensajes con texto bancario reconocible, pero remitente distinto de la dirección BCP admitida, se conservan para revisión (`SENDER_NOT_TRUSTED`) en lugar de generar movimientos automáticamente. Se agregó test de regresión. **No es una verificación criptográfica de autenticidad.** Un remitente `From` legítimo aparente puede falsificarse; falta comprobar resultados de autenticación confiables aportados por Gmail y construir política de procedencia de extremo a extremo antes de usar estos datos como fuente confiable de gastos.
- **Frontend:** se unifican mensajes visibles de Sarela a Finora (incluida la navegación móvil, feedback y errores), preservando los nombres técnicos legados. Se indica explícitamente que `gmail.readonly` permite leer el buzón completo, no exclusivamente mensajes bancarios, y que los patrones soportados son limitados, principalmente BCP/PEN.
- **Pruebas añadidas o ampliadas:** MockMvc para el JSON 401 en UTF-8, GmailQueueIntegrationTest para contenido bancario proveniente de remitente desconocido, y prueba Angular de la advertencia sobre el alcance de Gmail.

## Validación
Estas correcciones se realizaron por el conector GitHub. **No se han ejecutado compilaciones, JUnit, Karma, PostgreSQL ni pruebas de navegador en esta rama.** No inferir que pasan por resultados previos de otra versión.

Ejecutar en una copia local limpia:

```bash
git fetch origin
git switch fix/finora-google-verification-qa
cd backend
mvn -B clean verify
cd ../frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
node scripts/verify-legal-pages.mjs
```

No desplegar si hay fallos. Los tests unitarios no acreditan autenticidad criptográfica de correo ni requisitos Google.

## Bloqueantes antes de enviar la verificación pública de Gmail
1. `frontend/public/privacidad.html`, `terminos.html`, `eliminar-datos.html` **siguen marcadas como borradores**, explícitamente `No publicar todavía`. Confirmar la identidad legal del responsable y el país, plazos y política real de conservación, tratamiento del uso de OpenAI, acceso humano y eliminación. Publicar versiones revisadas por el responsable. Verificar URLs públicas HTTP 200 con el mismo contenido aprobado.
2. La solicitud de eliminación actual es `PENDING`: detiene trabajos y revoca credenciales, pero **no realiza borrado completo**. Debe instrumentarse un procedimiento administrativo probado con datos ficticios y política de backups.
3. Las comprobaciones de `From` no validan SPF/DKIM/DMARC de manera confiable. Falta pipeline de autenticidad y fixtures de MIME/HTML/multipart, para mensajes importados automáticamente.
4. Confirmar alcance de soporte bancario en UI y documentación: principalmente patrones BCP/PEN. No prometer acceso directo a banca ni cobertura total de movimientos.
5. Verificar OAuth con cuenta de prueba, rechazo de Gmail, renovación, importación/duplicados/desconexión, aislamiento entre usuarios y recuperación. Confirmar que el proyecto OAuth de producción coincide con el backend.
6. Google requiere verificación de `gmail.readonly` y puede exigir evaluación de seguridad para datos restringidos procesados en AWS. No afirmar aprobación.
7. Revisar IAM, logs, copias/restore, cabeceras Nginx/CSP y rutas legales y SPA.

## Despliegue de aplicación (solo tras pruebas y autorización)
- Backend actual en producción: `/home/ubuntu/finora/backend/target/finora-mvp-0.1.0.jar`; servicio `sarela-backend`, configuración en `/etc/sarela/backend.env`. No imprimir secretos.
- Código de despliegue: `/home/ubuntu/finora-google` (Git); preparar un checkout limpio a la rama QA, construir JAR con pruebas y conservar copia del JAR activo.
- **No se necesita migración nueva en esta rama**; la migración V2_5 ya se aplicó en el despliegue previo. No volver a aplicar SQL sin motivo.
- Reiniciar con parada controlada, comprobar status + health + respuesta UTF-8; mantener JAR anterior para rollback de aplicación.
- Para el frontend identificar antes la distribución actual: podría ser S3/CloudFront y no Nginx. Verificar destino real, hacer copia de la versión publicada, publicar build y comprobar URLs, caché y rutas. No adivinar bucket ni ruta.
- **El frontend nuevo y la documentación legal no deben declararse públicos hasta verificar su despliegue real.**

## Nota de producto
La promesa debe seguir siendo `¿En qué se va mi plata?`, con mínimo esfuerzo del usuario. No añadir un dashboard nuevo ni ocultar importaciones parciales.
