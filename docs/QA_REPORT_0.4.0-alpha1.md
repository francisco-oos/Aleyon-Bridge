# QA Report — Aleyon Bridge 0.4.0-alpha1

## Resultado verificable

```text
PASS core tests: 8
PASS static QA
PASS full Java stub compile: 21 production files
PASS Android assembleDebug (SDK 35 / Java 17 / Gradle 8.9)
PASS GitHub artifact upload
```

### Evidencia CI

- Build run: `35257840693`
- Commit de build: `e39d39ad8d2d31e229a045523554d6cd623e763a`
- Artefacto: `Aleyon-Bridge-v0.4.0-alpha1-debug`
- SHA-256 del ZIP de artefacto reportado por GitHub: `829747a38ab702d5b0195473c3670d3737ac5f6c4dabd8e0864c0d1050a6c80f`

El primer intento de build falló antes de compilar porque `android-actions/setup-android@v3` intentaba instalar el paquete SDK obsoleto `tools`. Se corrigió el workflow para usar el SDK preinstalado del runner e instalar explícitamente `platform-tools`, `platforms;android-35` y `build-tools;35.0.0`. Después de esa corrección, QA, `assembleDebug` y carga del artefacto finalizaron correctamente.

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

## Pendiente real

La compilación Android ya no es un gate pendiente. Lo que falta validar físicamente en teléfono es la interacción con la versión real de Gemini: selectores Accessibility, búsqueda/creación/renombre del chat canónico, ACK, transición a Live, materialización de la transcripción, cierre desde la burbuja, Live en background y recuperación ante cambios de ventana/proceso. Ver `PHYSICAL_QA_PLAN.md`.
