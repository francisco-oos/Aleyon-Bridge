# QA Report — Aleyon Bridge 0.4.0-alpha1

## Ejecutado

```text
PASS core tests: 8
PASS static QA
PASS full Java stub compile: 22 production files
```

## Cobertura de núcleo
- naming chat/perfil;
- recovery simplificado;
- ACK limitado por SESSION_ID/PROFILE_ID/schema;
- parser de reporte;
- aislamiento de reportes stale;
- evidence gate;
- delta textual;
- serialización de diagnósticos.

## QA estático
- ausencia de notebooks/attach/detach en runtime/UI;
- rutas Live + Chat;
- memoria local y capsule;
- no exact self-marker en prompts;
- root resolver Android 16;
- restricción al paquete Gemini;
- ausencia de gesture injection/coordenadas;
- versión 0.4.0-alpha1;
- sintaxis JavaScript.

## Limitación

La compilación con stubs no sustituye Android SDK/Gradle real. Tampoco prueba la jerarquía Accessibility de la versión instalada de Gemini. Ambos son gates físicos pendientes.
