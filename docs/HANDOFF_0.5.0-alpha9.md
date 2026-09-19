# Aleyon Bridge 0.5.0-alpha9 — handoff

## Evidencia física

Las nuevas grabaciones Samsung/Nubia muestran que el flujo se siente mejor cuando Gemini conserva el control natural de Live. La burbuja de Aleyon duplicaba la acción de terminar Live con `Finalizar Live y guardar`, aunque el runtime ya podía observar `LIVE_ACTIVE -> NORMAL_CHAT`.

También se observó que la cápsula inicial inducía respuestas meta como `Got it! I'm ready whenever you are`, que funcionan técnicamente pero hacen que el tutor se sienta programado.

## Decisión de UX

- En Live, la X de Gemini es el único gesto de finalización.
- La burbuja no puede finalizar Live.
- Tras detectar chat normal durante 900 ms continuos, Aleyon inicia automáticamente el cierre.
- En Chat se conserva un cierre manual porque no existe una señal nativa equivalente.
- Durante CLOSE_SESSION la burbuja es sólo estado + acceso a Gemini.

## Prompt de tutoría

La cápsula sigue llevando perfil, memoria local acotada y próximo objetivo, pero ordena integrarlos silenciosamente. Gemini no debe confirmar el bloque ni describir el plan. Debe comenzar directamente en el idioma objetivo, reaccionar a lo que el alumno dice, hacer una sola pregunta principal por turno y corregir con reformulaciones breves.

## Debrief

El cierre pide inmediatamente cuatro líneas separadas (`Resumen`, `Avance`, `A reforzar`, `Próximo paso`), una frase breve por línea. Se mantienen las protecciones alpha8: verificación del mensaje publicado, quietud post-Live, mismo chat por evidencia, `Responder ahora`, retry completo único y fallback local.

## Gate físico

- 5 ciclos Samsung + 5 ciclos Nubia.
- Live corto y Live de varios minutos.
- Gemini cerrado y ya abierto.
- Fin de Live sólo mediante X nativa.
- Prompt inicial sin confirmación meta.
- Corrección natural con errores deliberados.
- Cierre + resumen + notificación + memoria local.
