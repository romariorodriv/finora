# Sarela — piloto de investigación automática (fase 0)

**Estado:** experimento técnico, NO producto listo para producción, NO agente que contrata ni recomienda planes.

## Objetivo verificable

Un proceso Python **sin acceso a Gmail ni datos bancarios**, consulta periódicamente dos páginas públicas de operadores (Claro y Entel), respeta `robots.txt`, registra huellas del texto visible y menciones de precios **sin verificar**. En otra ejecución distingue `baseline`, `unchanged`, `changed` y `error`. Los errores no sobrescriben la última evidencia válida.

NUNCA considera una cifra detectada como oferta equivalente, ahorro real, precio permanente, disponibilidad ni recomendación. Las páginas mezclan campañas promocionales, paquetes y condiciones.

## Probar localmente

Requiere Python 3.11+; usa solo biblioteca estándar.

```bash
python -m unittest discover -s agents -p "test_*.py" -v
python -m agents.monitor --state agents/state.json
# ejecutar nuevamente más adelante para observar los cambios
```

La segunda línea requiere acceso a internet. Si un sitio rechaza el acceso o su `robots.txt` lo impide, el resultado será `error`, **no se sortea el bloqueo**. Las fuentes están en `sources.json`, con dominios permitidos en el código. No agregar URLs arbitrarias de usuarios.

## Requisitos antes de ofrecerlo a usuarios

1. Validar explícitamente derechos de consulta y estabilidad de cada fuente, y preferir APIs/acuerdos autorizados.
2. Extraer **planes completos**: proveedor, precio regular, duración/promoción, beneficios, restricciones, fecha, enlace y disponibilidad.
3. Verificar igualdad de prestaciones; aplicar costo total de 12 meses. Rechazar comparaciones incompletas.
4. Añadir programación, almacenamiento, límites de tasa, notificaciones, revisión de calidad y trazabilidad.
5. Probar ahorro útil y demanda comercial antes de construir chat, Premium o rediseñar producción.
6. Integrar con Spring Boot mediante una API autenticada y autorización por usuario; no abrir este trabajador directamente a internet.

**No desplegar este prototipo directamente en AWS ni enviar alertas de ahorro con sus `price_mentions_unverified`.**
