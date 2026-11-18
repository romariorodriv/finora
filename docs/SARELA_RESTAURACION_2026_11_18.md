# Restauración financiera de Sarela — 18 de noviembre de 2026

## Referencias inspeccionadas

| Referencia | Commit | Resultado |
| --- | --- | --- |
| main | 32978b723d5609a35d5227907bca9c4886ddcb4a | Aplicación financiera sin los nuevos agentes |
| feature/sarela-financial-agent-pilot | 73aaed0569fdc719ede28d56f187f9ea4694eef1 | Piloto de monitorización de fuentes, separado de main |
| feature/sarela-agent-hub-v1 | 22209afe4f4968d180f6ab1275d24226f1165fb4 | Catálogo, agente inmobiliario y cambios adicionales |

El piloto comienza en 257624c (16 de noviembre); el servicio inmobiliario en 43bc614 (17 de noviembre), seguido del gateway y del catálogo. Las propuestas de agentes permanecían sin fusionar en los PR #9 y #10 al inspeccionarlas. El backend financiero real es Java 21/Spring Boot; Python era exclusivo del servicio nuevo de agentes.

Esta versión parte directamente de main y recupera únicamente las correcciones útiles independientes del catálogo. No incorpora la historia de agentes ni altera las ramas existentes. No se ha desplegado ni modificado producción.

## Resultado respecto a la rama del catálogo

Se excluyen agent_service (Python y sus dependencias), docker-compose.agents.yml, HousingAgentController, las pantallas de catálogo y vivienda, el cliente analyzeHousing, sus estilos y las instrucciones y CI exclusivos del servicio de agentes. El piloto de fuentes de la otra rama tampoco se incorpora. No quedan procesos Python que arrancar ni claves de búsqueda de agentes que configurar para esta versión.

Se recuperan la landing financiera y el dashboard como destino inicial, con Inicio, Movimientos, Conexión Gmail y Tu opinión. Se conserva la administración existente. La sección de planificación «Mi dinero disponible» ya había sido retirada antes de los agentes; no se reintroduce.

Se conservan Google login, vinculación de cuentas, invitación opcional a Gmail tras Google, importación/deduplicación/reintentos, movimientos/categorías, borrado de cuenta, autenticación y seguridad, dominio sarelaapp.com, páginas legales y contenido SEO/calculadoras. AssistantService proporciona el análisis financiero determinista previo y compartido; no es el nuevo orquestador de agentes.

Se mantienen las advertencias veraces de importación incompleta y movimientos pendientes/no reconocidos, el arreglo de desbordamiento móvil del historial y el acceso demo local explícito. Su uso está documentado en README. No se cambian dependencias Java/Angular ni el esquema de datos.

## Validación y operación

Las pruebas de frontend verifican el dashboard inicial, la navegación financiera, el consentimiento opcional Gmail, la importación, los movimientos y el acceso demo. Una prueba de integración verifica que las rutas inmobiliarias retiradas devuelven 404 para usuarios autenticados y que el dashboard sigue accesible. El manejador de recursos inexistentes devuelve 404 en lugar de 500.

CI ejecuta Maven verify con Java 21 y, para frontend, compilación/prerender, auditorías públicas y SEO, navegador real y regresiones Angular. Las pruebas Angular dejan de estar marcadas como errores tolerados. Consultar el estado de los checks del PR antes de fusionar; no basta con compilar TypeScript.

En el entorno de edición compilan Angular y los proyectos TypeScript de aplicación/pruebas, y pasan las comprobaciones estáticas de dominio, marca, legales y SEO público. Chromium no puede abrir sockets en el sandbox local y Maven no resuelve el repositorio remoto; por ello la ejecución completa depende de CI. No se ha probado OAuth real ni el entorno de producción.

No se requiere migración de base de datos por esta restauración. Antes de desplegar, confirmar qué rama y servicios ejecuta realmente el servidor: el historial Git no acredita el estado de producción. Si existe un servicio de agentes desplegado, retirarlo de su orquestación y eliminar únicamente sus variables exclusivas después de comprobar consumidores; esta propuesta no ha parado servicios ni borrado secretos remotos. Conservar variables Google/Gmail, base de datos y cifrado.
