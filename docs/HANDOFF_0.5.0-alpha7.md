# Aleyon Bridge 0.5.0-alpha7 — handoff

## Lectura correcta de la evidencia física

El Samsung sí completó Live y Gemini respondió durante la conversación. Su fallo fue posterior: al cerrar, el mensaje de debrief quedó visible como mensaje del usuario y Gemini no inició ninguna respuesta observable.

En otro intento distinto, Artemis agotó la exploración de START_SESSION aun cuando Live terminó visualmente disponible. Son dos regresiones diferentes y alpha7 las trata por separado.

## Qué aprende Artemis realmente

`ArtemisRoutineMemory` no es un modelo que entrene entre teléfonos. Guarda en SharedPreferences del dispositivo una lista pequeña de pasos semánticos exitosos por fase y por versión del proveedor. Nubia y Samsung aprenden cada uno su propia ruta. Los intentos fallidos no se guardan.

Por eso el objetivo no es que el Samsung copie la ruta del Nubia, sino que ambos interpreten correctamente los mismos estados semánticos aunque Android/Compose y la latencia los expongan distinto.

## START_SESSION

- Si Gemini está respondiendo/pensando, esperar.
- Si la respuesta terminó y Live está visible, iniciar Live.
- Si la respuesta terminó y Live está fuera del viewport, explorar y reobservar.
- Un placeholder accesible ya no invalida el detector estructural de Live.

## CLOSE_SESSION

- Al terminar Live, esperar un mínimo de 4 s además de estabilidad de transcript.
- No enviar el debrief mientras Gemini siga en respuesta activa.
- Si aparece `Responder ahora`, usarlo como capacidad semántica una vez.
- Si el debrief fue aceptado pero no hay ningún progreso durante 20 s, mandar un único nudge: `Responde ahora al mensaje anterior...`.
- Nunca consumir otro chat como cierre de esta sesión.
- Si aun así no llega un cierre verificable, conservar la evidencia local mediante el fallback existente.

## Gate físico

- 5 ciclos Nubia + 5 ciclos Samsung.
- Inicio con Gemini cerrado y ya abierto/desplazado.
- Cierre desde Live, desde Chat y Live terminado manualmente.
- Samsung: reproducir específicamente `debrief aceptado / respuesta no iniciada`.
- Abrir la notificación y comprobar el detalle completo.
