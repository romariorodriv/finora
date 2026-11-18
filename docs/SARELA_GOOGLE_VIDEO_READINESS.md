# Correcciones previas al video OAuth — 18 de noviembre de 2026

## Base y orden de integración

Rama de correcciones: `fix/sarela-google-oauth-video-readiness`, basada en `4f12fed` (idiomas).
Al iniciar, `main` sigue en `32978b7`, restauración en `1a96602` y PR de idiomas #12 apunta a restauración.
Orden para revisión: #11 restauración -> #12 idiomas -> PR de correcciones. No se fusionó ni desplegó nada.
Las ramas de agentes #9/#10 no forman parte de esta base. No hay cambios de entidades ni migraciones.

## Correcciones verificables

- `AppComponent.financialInsightTitle`: deriva el resumen de `macroCategories` y `merchants`, no extrae categorías de frases del backend. Traduce etiquetas predeterminadas, preserva etiquetas personalizadas y no cambia cálculos. El contrato existente del backend permanece compatible para publicar primero el frontend.
- `GoogleGmailClient.getMessageMetadata`: `format=metadata`, cabeceras From y Subject, campos id/threadId/internalDate/payload(headers). No obtiene cuerpos.
- `GmailImportRunner.runPage`: conserva consulta, periodo, paginación, lease, reintentos y deduplicación; solicita metadatos antes del cuerpo. Solo el remitente que ya admitía el procesador (`notificaciones@notificacionesbcp.com.pe`) pasa a lectura completa. No se filtra por asunto adicional: los importes y operaciones pueden estar solo en el cuerpo.
- Remitentes desconocidos: revisión `UNSUPPORTED_SENDER_BODY_NOT_READ`, sin cuerpo ni movimiento. Se conservan id/hilo/remitente/asunto/recepción y estado. Puede aumentar la revisión de promociones desconocidas; no se declara que sean financieras ni incompatibles basándose solo en metadatos. Los registros terminales existentes mantienen su deduplicación/resultado anterior.
- El servicio síncrono antiguo, sin referencias desde controladores actuales, recibe la misma protección para evitar una ruta alternativa de lectura indiscriminada.
- Lectura completa: respuesta parcial de Gmail que mantiene el árbol MIME recursivo para no perder contenido anidado. No se usan snippet/raw/labels/history. No hay llamadas de descarga de adjuntos. El parser omite partes con filename; la respuesta full aún puede transportar datos de partes adicionales. No se promete eliminar todo byte de adjuntos del transporte.
- La coincidencia de From NO valida SPF/DKIM/DMARC ni demuestra autenticidad bancaria. No se cambian esas reglas ni se afirma resolver ese riesgo.

El paso metadata agrega una petición por candidato admitido (coste de cuota/latencia); para remitentes desconocidos evita la petición full. No se promete ahorro de llamadas total.

## OAuth, campos y permisos

Login: `GoogleIdentityClient.authorizationUrl`, `openid email profile`.
Gmail: `GmailOAuthService.authorizationUrl`, `https://www.googleapis.com/auth/gmail.readonly`, offline, consentimiento, PKCE y estado vinculado al navegador.
El callback intercambia el código, obtiene perfil y guarda tokens cifrados, iniciando el trabajo; renovación y revocación siguen sin cambios.
`include_granted_scopes=true` puede combinar permisos concedidos anteriormente. La lista solicitada por código no prueba los permisos efectivos de una cuenta o los registrados en Cloud.
`GoogleGmailClient.search`: `messages.list` con consulta y paginación.
`getMessageMetadata` y `getMessage`: `messages.get` metadata y full respectivamente.
`PeruBankParser`: asunto/cuerpo para importe PEN, comercio, tipo y rechazo. La fecha persistida es recepción Gmail en America/Lima, no fecha bancaria.
Sin cuerpo se pierde extracción de importes/comercios/tipos que no aparecen en las cabeceras. `gmail.metadata` no permite cuerpo y `messages.list` no admite q con ese scope. Seleccionar candidatos no reduce el alcance OAuth del buzón.

Fuentes oficiales consultadas:
- https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/get
- https://developers.google.com/workspace/gmail/api/reference/rest/v1/Format
- https://developers.google.com/workspace/gmail/api/guides/performance
- https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/list
- https://developers.google.com/workspace/gmail/api/auth/scopes

## Privacidad y confirmaciones pendientes

Se actualizan privacidad, seguridad y cómo funciona en ES/EN para explicar las dos etapas y revisión sin cuerpo. Términos y eliminación mantienen los límites reales y añaden pendientes explícitos. Las páginas EN se generan desde catálogos; no editar dist.
No se eliminan advertencias ni se inventan plazos. PENDIENTE DE CONFIRMACIÓN del operador:
1. Identidad legal del responsable y país; una marca y correo de contacto no bastan para determinarla.
2. Criterios reales de retención de inactividad, auditorías, logs y backups, y proceso de restauración/borrado.
3. Quién accede, con qué autorización y finalidad permitida; el rol ADMIN no constituye consentimiento para acceso humano a datos Gmail.
4. Inventario de terceros, regiones, finalidad y datos transmitidos. El operador indicó OpenAI; no se encontró cliente activo en este flujo, pero no se excluyen usos externos.
5. Correspondencia entre textos, operación y versión publicada antes de grabar.
Tokens cifrados en base mediante AES-GCM; secreto, permisos de infraestructura y logs reales no auditados. Desconectar cancela trabajos y elimina credenciales, no historial. Eliminar cuenta ordinaria borra datos asociados de base activa; no acredita borrado de copias externas.

## Fixtures ficticios y límites de preparación

Fuente exacta ejecutable: `backend/src/test/resources/gmail-video-fixtures.json`; `GmailVideoFixturesTest` valida el parser actual sin cambiar sus reglas.
Todos los mensajes son inventados, no documentos bancarios reales. La dirección declarada es una condición del código, no evidencia de procedencia.

| ID | Asunto / cuerpo | Importe / moneda / comercio | Resultado esperado |
|---|---|---|---|
| fictional-purchase | Realizaste un consumo / Realizaste un consumo de S/ 32.90 con tu Tarjeta de Débito BCP en DLC*Spotify. | 32.90 / PEN / Spotify | EXPENSE; parser Suscripciones; categoría final depende del catálogo/reglas activos o revisión pendiente |
| fictional-rejected | Se rechazó tu compra / Monto: S/ 32.90; Nombre del comercio: DLC*Spotify; Motivo: Fondos insuficientes | 32.90 / PEN / Spotify | REJECTED, no movimiento |
| fictional-ambiguous | Constancia de transferencia entre mis cuentas / Monto no disponible | Sin importe confirmado | REVIEW_REQUIRED |
| fictional-promotion | Oferta de compra / Compra tu SOAT y participa por S/ 1000 | Premio no es movimiento | IGNORED para remitente admitido |
| fictional-forward | BCP consumo / mismo texto de compra, From demo@example.test | No se lee cuerpo | REVIEW_REQUIRED, UNSUPPORTED_SENDER_BODY_NOT_READ |

Recepción de fixtures: 2026-11-18T15:00:00Z; fecha esperada de movimiento 2026-11-18 (Lima). Importe y comercio están solo en cuerpo, demostrando necesidad técnica de lectura, no E2E real.
NO enviar correo suplantando al banco. Un envío manual/reenvío desde dirección personal no prueba importación bancaria: quedará por revisar. Para la demostración real se necesita una fuente de prueba legítimamente autorizada/compatible o evidencia real permitida por su titular, con datos privados protegidos. No presentar fixtures o pruebas mock como Gmail real.

## Guía manual E2E pendiente (no ejecutada)

1. Confirmar dominio real autorizado; abrir landing, EN y acceso Google con cuenta de prueba. Confirmar dashboard en inglés y Gmail no conectado. No usar login demo como evidencia OAuth.
2. Abrir Gmail Connection, revisar divulgación y Connect Gmail. Mostrar consentimiento Google completo en inglés, nombre y permisos. Solo el titular autoriza. Cambiar idioma en Google por separado si hace falta.
3. Mostrar previamente un mensaje compatible autorizado en Gmail y sus campos, sin exponer información privada. No basta con enviar un fixture desde cualquier dirección.
4. Regresar del consentimiento: comprobar EN, trabajo de importación, periodo y estado reales hasta finalizar; si falla, registrar el error sin publicar URLs con code/state/tokens.
5. Abrir Transactions: cotejar importe, PEN, comercio, tipo, fecha de recepción y origen. Un rechazo no debe generar gasto; ambiguos/desconocidos van a revisión.
6. Comprobar categoría con reglas/catálogo efectivos; si falta, mostrar pendiente sin atribuir clasificación inexistente. Dashboard: mismo mes y moneda, importes coincidentes; no sumar transferencias o retiros como EXPENSE.
7. Actualizar nuevamente y comprobar que el mismo mensaje no crea otra transacción ni modifica una corrección manual. Recargar/cerrar sesión y comprobar persistencia EN.
8. Casos negativos únicamente con cuenta de prueba: sin compatibles, desconocido, ambiguo y cancelación. Vencimientos/reintentos se prueban automatizadamente; no interrumpir cuentas reales para provocarlos.

## Qué puede demostrarse y qué falta

| Función | Código y pruebas automatizadas | Evidencia real pendiente |
|---|---|---|
| ES/EN y resumen categórico | Regresión específica añadida | Front publicado, recarga y retorno OAuth |
| OAuth separado / scopes | Código y pruebas existentes | Consentimiento y grants reales |
| Metadata antes del cuerpo | Pruebas de cliente/cola | Gmail API real y tiempos/cuota |
| Parser / rechazo / revisión | Fixtures y pruebas de cola | Mensaje autorizado -> resultado visible |
| Duplicados / paginación / reintentos | Regresión existente y nueva | Segunda sincronización con Gmail real |
| Dashboard y clasificación | Pruebas financieras existentes | Conciliación visual con correo |
| Privacidad | Textos reflejan implementación | Decisiones operativas pendientes |

## Producción: sin cambios externos

No hay evidencia nueva de los commits efectivos del frontend/backend. El health aportado por el operador prueba servicio activo, no versión/Google. Dominio canónico del código: https://sarelaapp.com; el contexto anterior utiliza https://sarela.pgoapp.com. No se cambian dominios ni DNS: confirmar cuál es el publicado y autorizado.
Comparar client_id completo y redirect_uri de login/Gmail de forma privada con Cloud; no compartir authorizationUrl completa, códigos ni secretos. Confirmar perfil prod, ddl validate, demo false, jobs enabled e intervalo por mecanismos del servidor ya autorizados.
Alternativa segura para trazabilidad: guardar con cada despliegue un manifiesto privado con commit y sha256 del JAR/artefactos. No hace falta un endpoint público ni exponer variables. Comparar ese manifiesto y hashes con los artefactos de CI. Es una recomendación, no infraestructura implementada.

## Validación

Consultar CI de esta PR para el commit exacto. No confundir pruebas Google mock con E2E.
Comandos: `cd frontend && npm run build`, `npm test -- --watch=false --browsers=ChromeHeadless`, todos los `qa:*` del package; `cd backend && mvn -B -ntp verify`.
El entorno local carece de Maven y limita Chromium; se documentará por separado compilación estática local y ejecución CI real. Un CI verde no cierra los pendientes de operador ni acredita producción.
