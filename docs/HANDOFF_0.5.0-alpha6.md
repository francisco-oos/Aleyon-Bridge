# Aleyon Bridge 0.5.0-alpha6 — handoff

## Evidencia física

Durante una espera lenta del debrief, se abrió un chat anterior que ya contenía un cierre válido. Alpha5 podía clasificarlo como NORMAL_CHAT y parsear esas cuatro líneas como si fueran de la sesión actual.

## Corrección

CLOSE_SESSION queda ligado a `debriefConversationAnchor`, derivado de `sessionEvidenceText` observado antes del debrief. Antes de enviar el cierre y mientras espera su respuesta, Bridge comprueba continuidad de contenido con `SessionTextDelta.containsConversationEvidence`.

Si la conversación visible no coincide, no parsea, no hace commit y muestra `Vuelve al chat de esta sesión…`. Al regresar al chat correcto, continúa.

Esta huella es efímera y local. No es un ID persistente del chat de Gemini y no habilita búsqueda, recuperación ni continuidad basada en historial del proveedor.

## Notificación

`pending_summary_profile` abre ahora directamente el cierre completo más reciente. La notificación incluye `EXTRA_OPEN_SUMMARY_PROFILE`; al tocarla, incluso si Aleyon ya estaba abierto, MainActivity vuelve a marcar ese perfil como pendiente y abre el detalle completo. El historial anterior sigue disponible desde ese diálogo.

## Gate físico

- Nubia y Samsung: cambiar a un chat anterior durante WAIT_DEBRIEF y comprobar rechazo.
- Regresar al chat actual y comprobar commit correcto.
- Abrir la notificación y leer el cierre detallado.
