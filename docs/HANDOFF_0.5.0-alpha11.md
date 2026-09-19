# Aleyon Bridge 0.5.0-alpha11 — Gemini Tutor Protocol V1

## Alcance

Alpha11 no modifica el transporte validado de alpha10. Artemis, navegación Gemini, inicio Live, salida por X, CLOSE_SESSION, cross-chat guard, notificaciones y Pedagogical State v2 permanecen iguales.

## Cambio

Aleyon añade dos marcadores semánticos opcionales:

- `ALEYON TUTOR START V1`
- `ALEYON TUTOR CLOSE V1`

START es literalmente el primer contenido del primer mensaje de una sesión nueva.

CLOSE es literalmente el primer contenido tanto del debrief normal como del único retry completo.

## Compatibilidad

La integración con “Instrucciones para Gemini” es un plus, no una dependencia.

Si la cuenta de Gemini tiene configuradas las instrucciones opcionales, los marcadores activan/cancelan el comportamiento tutor nativo.

Si no las tiene, Gemini simplemente ve los marcadores como texto y el resto de la cápsula alpha10 sigue proporcionando toda la política pedagógica necesaria.

Aleyon continúa siendo dueño del perfil, memoria, evidencia, Pedagogical State v2 y próximo objetivo.

## Invariantes

- START debe seguir siendo el primer contenido de la cápsula.
- CLOSE debe seguir siendo el primer contenido de cierre y retry.
- La política pedagógica local de alpha10 no se elimina.
- No se añade búsqueda, renombrado, recuperación ni memoria del proveedor.
- No se modifica el flujo físico de Live.

## Prueba física

1. Con instrucciones nativas de Gemini configuradas:
   - iniciar una sesión y verificar que el primer mensaje visible comienza con `ALEYON TUTOR START V1`;
   - comprobar comportamiento tutor natural;
   - terminar Live con la X;
   - verificar que el mensaje de cierre comienza con `ALEYON TUTOR CLOSE V1`;
   - confirmar debrief y commit local.
2. Sin instrucciones nativas:
   - repetir la misma sesión;
   - confirmar que alpha11 sigue funcionando por la política pedagógica local.
