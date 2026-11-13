# Sarela — Onboarding Gmail y diseño visual unificado

## Cambios de esta rama
- Mantiene login Google (openid email profile) separado de la autorización Gmail (gmail.readonly).
- Después de login nuevo, si Gmail no está conectado, ofrece consentimiento previo opcional con «Conectar Gmail» y «Ahora no».
- Respeta la negativa por cuenta y navegador; la conexión manual continúa disponible.
- El permiso de Gmail se concede exclusivamente en la pantalla oficial de Google.
- Después del callback muestra estados reales de importación y cifras reales, nunca porcentaje inventado.
- Unifica Movimientos, Conexión Gmail, Tu opinión y Administración con el diseño del Home y la landing.
- Sin migraciones, cambios de backend ni eliminación de datos.

## Pruebas locales antes de publicar
```bash
git fetch origin
git switch --track origin/feature/sarela-unified-onboarding-ui
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
npm run qa:domain
npm run qa:legal
```

Si la rama local ya existe: git switch feature/sarela-unified-onboarding-ui && git pull --ff-only.

## QA manual crítico
1. Login nuevo sin conexión Gmail: modal informativo visible.
2. «Ahora no»: no autoriza Gmail; dashboard y movimientos manuales disponibles.
3. Reingreso con la misma cuenta tras rechazo: no reaparece modal; conexión manual disponible.
4. «Conectar Gmail»: visualizar pantalla oficial de Google con permiso real de lectura al buzón completo.
5. Cancelar desde Google: Gmail sigue desconectado, dashboard accesible.
6. Aprobar en Google: callback correcto, importación en estado real y sin porcentaje ficticio.
7. Fallos o correos incompatibles: estados y cifras honestas sin inventar movimientos.
8. Gmail conectado previamente: no mostrar modal.
9. Revisar filtros, edición, borrado, opinión, navegación móvil y todas las herramientas ADMIN.
10. Comprobar enlaces de privacidad, términos y eliminación de datos.

## Publicación
Publicar después de las pruebas. Este cambio de UI no requiere nueva compilación Java ni migración SQL.
Usar frontend/scripts/publish-google-frontend.ps1, primero sin -Apply y luego con -Apply al verificar la cuenta y distribución.
El video para Google debe mostrar consentimiento real y funcionalidades reales; las maquetas no sustituyen esa evidencia.