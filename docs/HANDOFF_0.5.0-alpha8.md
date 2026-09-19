# Aleyon Bridge 0.5.0-alpha8 — revisión de cierre basada en videos

## Evidencia

Se revisaron diez grabaciones. Live y multimodalidad funcionan de forma natural en varios recorridos, incluido cambio inglés/español y generación de imagen. La concentración de fallos está en la transición Live → Chat → debrief.

En varios cierres, el primer debrief aparece como mensaje del usuario pero Gemini no responde a ese cierre. El antiguo nudge `Responde ahora al mensaje anterior` provoca después respuestas a una pregunta pendiente de Live o cierres genéricos (`Sesión finalizada`) que no cumplen el contrato pedagógico.

## Correcciones

1. El envío se verifica dentro de `assistant_robin_user_message_*`, no por crecimiento arbitrario del texto de la pantalla.
2. El slot de acción reutilizado por Gemini no se trata como Send mientras hay respuesta activa.
3. `Stop` dicho por el alumno no se interpreta globalmente como un control.
4. La estabilización post-Live usa quietud observada y no scroll repetitivo.
5. El fallback ambiguo se elimina. El único retry repite el debrief completo y puede incluir evidencia acotada de la sesión.
6. Las rutas Artemis anteriores quedan fuera del namespace de política v4 sin borrar memoria pedagógica.
7. El script USB limpia el estado Android `Waiting For Debugger` visto en Samsung.

## Lo que no cambia

- Gemini sigue siendo el motor cognitivo/Live/multimodal.
- Modo imagen permanece nativo.
- No hay búsqueda/renombrado/ID persistente de chats Gemini.
- Aleyon conserva perfil, evidencia y aprendizaje local.
- Sólo existen START_SESSION y CLOSE_SESSION como tareas Artemis.
