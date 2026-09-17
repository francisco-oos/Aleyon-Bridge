# Aleyon Bridge 0.4.0-alpha1

Aleyon Bridge es la capa persistente de aprendizaje que utiliza la app oficial de **Gemini** como motor cognitivo y multimodal. Aleyon conserva el perfil, el progreso, la continuidad, la evidencia y el estado transaccional; Gemini aporta Chat, Live, cámara, pantalla, imágenes y demás capacidades disponibles para la cuenta del usuario.

## Cambio arquitectónico 0.4

La línea 0.3 administraba notebooks de Gemini, asociaba/separaba chats y sincronizaba memoria mediante una transacción larga. 0.4 elimina esa dependencia.

```text
PERFIL LOCAL ALEYON
      │
      ▼
LearningStore + Evidence Ledger
      │
      ▼
Context Capsule compacta
      │
      ▼
CHAT CANÓNICO GEMINI POR PERFIL
      │
      ├── Chat
      └── Live  ← ruta principal
             │
             └── burbuja Aleyon
      │
      ▼
transcripción / conversación de la sesión
      │
      ▼
reporte estructurado SESSION_ID
      │
      ▼
validación de evidencia + commit local
```

### Invariantes

1. **Gemini no es la fuente de verdad** del alumno.
2. Un perfil mantiene **un chat canónico**: `ALEYON LIVE — <Idioma> — Conversación principal`.
3. No existen notebooks, attach/detach ni reattach en el runtime 0.4.
4. Guardar/editar perfil es local e inmediato; Gemini sólo se toca al iniciar una sesión.
5. Live y Chat reciben la misma continuidad compacta.
6. El chat puede conservar contexto adicional, pero Aleyon reinyecta sólo estado pedagógico de alta señal.
7. Un `EVENT` pedagógico sólo se promueve si su evidencia textual aparece en el delta accesible de la sesión; si no puede demostrarse, se conserva el resumen pero no se inventa progreso.
8. Imágenes, cámara o pantalla usadas con Gemini son contexto del usuario/Gemini; Bridge no necesita analizarlas ni almacenarlas.

## Uso

En la pantalla principal sólo aparecen acciones de usuario:

- **Live** — ruta predeterminada para conversación natural.
- **Chat** — misma continuidad, modo texto.
- **Editar** — perfil pedagógico.
- **Progreso** — resumen, siguiente objetivo, consejo y evidencia reciente.

No se muestran proveedores, cuadernos, prompts, compactación ni operaciones internas.

### Inicio de sesión

Al tocar Live o Chat:

1. Aleyon genera `SESSION_ID`.
2. Localiza el chat canónico exacto del perfil; si no existe, crea uno y lo renombra una sola vez.
3. Toma una instantánea accesible previa del chat.
4. Construye una cápsula compacta con perfil + último resumen + próximo objetivo + evidencia reciente.
5. La envía a Gemini y espera `ALEYON_SESSION_READY` del `SESSION_ID` actual.
6. En Live pulsa el control Live y confirma estado activo; en Chat deja abierta la conversación.
7. Muestra la burbuja Aleyon para volver a Gemini o cerrar sesión.

### Cierre

1. Si Live sigue activo, lo finaliza y espera que Gemini materialice la conversación/transcripción.
2. Calcula un delta best-effort entre el texto accesible antes y después de la sesión.
3. Solicita un reporte estructurado limitado al `SESSION_ID` actual.
4. Guarda resumen, siguiente objetivo, feedback y eventos cuya evidencia sea demostrable en el delta.
5. Marca `READY` y vuelve a Aleyon.

El chat canónico **se conserva por defecto**. No se borra después de cada sesión: esto reduce automatización, permite reanudar Live en la misma conversación y conserva contexto útil de Gemini sin convertirlo en memoria autoritativa.

## Compactación

`ContextCapsuleBuilder` implementa compactación de continuidad del lado de Aleyon:

- perfil estable siempre presente;
- resumen de última sesión;
- próximo objetivo;
- máximo 8 evidencias recientes;
- límites de longitud por campo;
- ningún historial completo se reenvía.

El principio se inspira en estrategias modernas de context engineering/compaction: conservar estado exacto fuera del modelo y reemplazar historia de bajo valor por una continuación estructurada de alta señal.

## Android / Accessibility

Se conserva el fix probado de alpha4 para Android 16/OEM:

- `getRootInActiveWindow()` primero;
- fallback por `getWindows()`;
- sólo raíces de `com.google.android.apps.bard`;
- prioridad ventana Gemini activa → enfocada → única `TYPE_APPLICATION`;
- `TYPE_WINDOWS_CHANGED` sólo despierta la máquina de estados;
- no se usan coordenadas ni gesture injection;
- burbuja mediante `TYPE_ACCESSIBILITY_OVERLAY`.

Bridge no pide `RECORD_AUDIO`: Gemini Live controla micrófono/voz.

## Estado QA de esta revisión

Ejecutado en este entorno:

```text
PASS core tests: 8
PASS static QA
PASS full Java stub compile: 22 production files
```

La compilación APK real y los selectores de la versión de Gemini instalada requieren Android SDK/teléfono físico. Ver `docs/PHYSICAL_QA_PLAN.md`.

## Estructura relevante

```text
app/src/main/java/com/aleyon/geminibridge/
├── MainActivity.java
├── automation/
│   ├── AleyonAccessibilityService.java
│   ├── GeminiUi.java
│   ├── LearningStore.java
│   ├── OverlayController.java
│   ├── ProfileSpec.java
│   ├── PromptRepository.java
│   └── SessionJournal.java
└── core/
    ├── ContextCapsuleBuilder.java
    ├── LearningEvent.java
    ├── LearningLedger.java
    ├── ProtocolContract.java
    ├── RecoveryPlanner.java
    ├── SessionReportParser.java
    ├── SessionStage.java
    └── SessionTextDelta.java
```

## Documentación

- `docs/ARCHITECTURE.md`
- `docs/ADR-0001_LOCAL_MEMORY_CANONICAL_CHAT.md`
- `docs/CONTEXT_COMPACTION.md`
- `docs/GEMINI_AUTOMATION_CONTRACT.md`
- `docs/MIGRATION_ALPHA4_TO_0.4.md`
- `docs/PHYSICAL_QA_PLAN.md`
- `docs/QA_REPORT_0.4.0-alpha1.md`
- `docs/RESEARCH_2026-09-17.md`
- `docs/SECURITY.md`

La versión 0.3.0-alpha4 se usa como referencia histórica de la burbuja, perfiles, arranque Live y resolución Android 16, no como arquitectura de memoria vigente.
