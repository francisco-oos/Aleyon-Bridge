# Plan QA físico 0.4.0-alpha1

Validar en teléfono real con la cuenta Gemini habitual:

1. upgrade sobre alpha4 y conservación de perfiles;
2. nuevo perfil: guardar no debe abrir Gemini;
3. primer Live: crear/renombrar chat canónico, ACK, iniciar Live;
4. Live existente: localizar el mismo chat y no crear duplicado;
5. finalizar Live manualmente: detectar salida y completar análisis;
6. cerrar desde burbuja;
7. Chat: cápsula + burbuja + cierre desde burbuja;
8. comprobar que aparece transcripción tras finalizar Live;
9. `LearningStore`: resumen, next, feedback, eventos con evidencia;
10. probar imagen generada, cámara y screen share: Bridge no debe interferir;
11. cambiar a otra app con Live en background: burbuja disponible;
12. crash/reinicio en `LIVE_ACTIVE` y `ANALYZING`;
13. duplicar manualmente el título del chat: Bridge debe fallar ambiguo, no elegir al azar;
14. cambiar idioma de la UI de Gemini español/inglés;
15. medir latencia desde toque Live hasta Live activo;
16. revisar consumo batería/Doze y kill de proceso;
17. confirmar Android 16 `getWindows()` cuando root activo sea nulo.

### Gate de promoción

No promover alpha1 si falla cualquiera de: perfil persistente, chat único, ACK session-scoped, Live activo, cierre con transcripción, commit local, o recovery sin acción destructiva.
