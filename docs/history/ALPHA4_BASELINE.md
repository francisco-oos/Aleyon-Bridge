# Baseline histórico — Aleyon Gemini Bridge 0.3.0-alpha4

Referencia de migración: `Aleyon_Gemini_Bridge_v0.3.0-alpha4_CHATGPT_FIX.zip`.

## Qué se conserva conceptualmente o en código
- burbuja Aleyon mediante `TYPE_ACCESSIBILITY_OVERLAY`;
- perfiles multiidioma;
- lanzamiento y verificación de Gemini Live;
- automatización semántica sin coordenadas;
- resolución Android 16/OEM mediante `getRootInActiveWindow()` + fallback `getWindows()` limitado al paquete de Gemini;
- `SessionJournal` y recuperación crash-safe.

## Qué queda superseded en 0.4
- Gemini Notebook como memoria autoritativa;
- attach/detach/reattach de conversaciones;
- sincronización y reparación de notebooks;
- prompts maestros cuyo protocolo dependía de notebooks;
- borrado automático del chat al finalizar.

## Baseline QA reproducido en esta migración

```text
PASS core tests: 9
PASS static QA
PASS full Java stub compile: 19 production files
```

La versión 0.4 no afirma que esos 9 tests sigan existiendo sin cambios: reemplaza pruebas ligadas a notebooks por pruebas de contexto compacto, reporte por `SESSION_ID`, delta textual y gate de evidencia.
