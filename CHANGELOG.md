# Changelog

## 0.4.0-alpha1 — Memoria local + chat canónico + contexto compacto

Cambio de arquitectura deliberado a partir de los aprendizajes de Aleyon Bridge 0.3.0-alpha4 e Idioma-Tutor-App 0.1.0-alpha.3.

### Eliminado del runtime
- Gemini Notebooks como memoria del perfil.
- creación/búsqueda/reparación de notebook;
- add/remove chat from notebook;
- estados `SYNCING`, `SYNC_READY`, `DETACHING`, `DETACHED`, `REATTACHING`;
- borrado automático de contenido Gemini;
- configuración técnica/proveedores expuesta al usuario.

### Añadido
- un chat canónico de Gemini por perfil;
- rutas `START_LIVE_SESSION` y `START_CHAT_SESSION`;
- `LearningStore` local-first;
- `ContextCapsuleBuilder` con perfil + resumen + objetivo + evidencia reciente;
- `SESSION_ID` y `SCHEMA_VERSION=3`;
- ACK `ALEYON_SESSION_READY` limitado a sesión;
- reporte de cierre `ALEYON_REPORT_BEGIN/END` limitado a sesión;
- `SessionTextDelta` para separar best-effort texto visible previo/nuevo;
- gate de evidencia: los eventos sólo se promueven si la evidencia aparece en el delta accesible;
- guardar/editar perfil sin abrir Gemini;
- pantalla simplificada: Live, Chat, Editar, Progreso;
- migración tolerante de perfiles alpha4: campos de notebook se ignoran, chatName se conserva.

### Conservado de alpha4
- AccessibilityService restringido a `com.google.android.apps.bard`;
- automatización semántica por nodos accesibles;
- burbuja `TYPE_ACCESSIBILITY_OVERLAY`;
- resolución Android 16 mediante `getWindows()` con verificación de paquete;
- `TYPE_WINDOWS_CHANGED` como wake-up;
- journal crash-safe;
- ausencia de captura de audio propia.

### Decisiones
- modo temporal/efímero no se usa porque en la experiencia validada por el usuario no ofrece Live;
- el chat canónico se conserva por defecto y puede acumular contexto auxiliar;
- Aleyon sigue reinyectando una cápsula compacta porque la memoria entre chats de Gemini no es una dependencia válida para Live;
- contenido visual generado/compartido en Gemini no se duplica en Bridge.

### QA local
```text
PASS core tests: 8
PASS static QA
PASS full Java stub compile: 22 production files
```

Pendiente: build Gradle con Android SDK y QA físico de selectores/transcripción en la versión de Gemini instalada.

## 0.3.0-alpha4 — Referencia histórica

Se conserva como baseline histórico del fix Android 16, burbuja, perfiles y arranque Live. La arquitectura Notebook/attach-detach queda superseded por 0.4.
