# Aleyon Bridge 0.5.0-alpha5 — handoff

## Evidencia física

Gemini ya abierto puede conservar una conversación desplazada. En esa condición Aleyon veía un chat normal pero no encontraba el launcher Live hasta que el usuario hacía scroll manualmente. Las pruebas de cierre también confirmaron que Gemini sí vuelve al mismo chat y puede generar el resumen; con Internet lento simplemente tarda.

## Decisión

No se reintroduce identidad de conversación del proveedor. El SESSION_ID sigue siendo sólo la transacción local de Aleyon y Gemini continúa siendo desechable entre sesiones.

Artemis mantiene dos tareas:
- START_SESSION: chat nuevo -> contexto -> exploración semántica si hace falta -> Live/Chat listo.
- CLOSE_SESSION: observar Live/Chat actual -> terminar Live si corresponde -> mismo chat -> debrief -> commit local.

## Cambios

- SCROLL_FORWARD y SCROLL_BACKWARD forman parte de la rutina aprendible START_SESSION.
- El replay de scroll se invalida si Live ya está visible.
- Un chat Robin desplazado se sigue clasificando como NORMAL_CHAT aunque temporalmente no muestre el compositor.
- La espera del debrief se basa en progreso observado, no en 45 s rígidos.
- Un segundo cierre es idempotente y no reinicia el cierre en curso.

## Gate físico pendiente

- Nubia con Live visible y fuera de viewport.
- Samsung con ambos escenarios.
- Cierre desde Live, Chat y Live terminado manualmente.
- Respuesta Gemini lenta/streaming y timeout real.
- Confirmar resumen, siguiente objetivo y evidencia en memoria local.
