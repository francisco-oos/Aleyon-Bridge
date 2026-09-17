# Arquitectura Aleyon Bridge 0.4

## Autoridad de estado

Aleyon es dueño de:
- perfil estable;
- journal transaccional;
- resumen de sesión;
- próximo objetivo;
- evidencia pedagógica;
- compactación de continuidad.

Gemini es dueño únicamente de la ejecución de la sesión en su app: Chat, Live y funciones multimodales.

## Flujo

```text
UI Aleyon
  -> SessionJournal
  -> LearningStore
  -> ContextCapsuleBuilder
  -> AccessibilityService
  -> chat canónico Gemini
  -> ACK SESSION_ID
  -> Chat o Live
  -> cierre/transcripción
  -> reporte estructurado
  -> SessionTextDelta
  -> evidence gate
  -> LearningStore commit
```

## Chat canónico

Cada `PROFILE_ID` tiene un `CHAT_NAME` estable. El nombre no prueba identidad por sí solo; Aleyon también conserva el `PROFILE_ID` local y usa `SESSION_ID` para cada operación. La 0.4 no modifica notebooks.

## Persistencia

`SessionJournal` usa `SharedPreferences` para el perfil y estado transaccional. `LearningStore` usa un namespace separado para el ledger pedagógico. Esta alpha prioriza continuidad y migración simple; cifrado/backup controlado queda para un hito posterior.

## Recuperación

Estados principales:

```text
READY
LOCATING_CHAT
CREATING_CHAT
CONTEXT_INJECTING
CONTEXT_READY
LIVE_STARTING
LIVE_ACTIVE | CHAT_ACTIVE
CLOSING_SESSION
WAITING_TRANSCRIPT
ANALYZING
COMMITTING
READY
```

Un crash durante Live intenta restaurar la burbuja si Live sigue realmente activo. Si Live terminó, completa cierre/análisis. Un crash antes de Live reintenta una preparación segura sobre el mismo chat canónico.
