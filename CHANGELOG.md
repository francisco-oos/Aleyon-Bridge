# Changelog

## 0.5.0-alpha2 — exhaustive validation / build parity / safe material boundary

- Makes `VERSION` authoritative across Windows build output and CI; removes stale `0.4.0-alpha8` build pinning.
- Fixes Windows/Linux core-runner drift so both compile the same new core classes and execute the same QA stack.
- Adds `qa_build_parity.py`, security QA and a deterministic 60-profile / 360-session compatibility simulation.
- Adds metadata-only `SessionMaterial` + `MaterialHandoffPolicy`: Android/Gemini owns file bytes; Bridge/Artemis control-plane does not need storage or microphone authority.
- Adds a narrow semantic native-attachment action scoped to Gemini's composer.
- Encapsulates Live termination behind a verified Live-only semantic adapter rather than transport-level labels.
- Session debrief now promotes `Avance` and `A reforzar` into verified local learning evidence after a successful commit.
- Sanitizes canonical conversation titles and imported profile ids.
- Disables Android backup for local learner memory and hardens the local WebView against remote/mixed navigation.
- Documents current Gemini file/Live capability boundary and Artemis upstream command-injection issues that must remain outside the APK.
- Confirms the final `develop/artemis-transport` candidate with independent GitHub Actions QA plus real Android `clean assembleDebug` on SDK 35 / Gradle 8.9 / Java 17, producing a verified debug APK artifact.

- Field hardening on `develop/artemis-transport`: migrated profiles with no canonical registry entry now rebuild directly instead of searching blindly; search is reserved for a previously verified canonical chat.
- Adds `CONVERSATION_SEARCH` as a distinct transport state, removes the arbitrary-EditText composer fallback, rejects the search query itself as a conversation result, and adds bounded replan/runtime watchdogs to prevent frozen automation loops.
- Adds regression coverage derived from the real Gemini passive probe where `Buscar chats` was previously misclassified as `CHAT`.
- Replaces the interim custom navigation planner with a narrow embedded derivative of Google Artemis: multi-window root resolution from the Android helper, Flash-style observe→act→observe execution, and persistent routine replay/invalidation/relearning.
- Learned routines are keyed by installed Gemini/Google package-version signature and are discarded immediately when observed state or action execution stops matching.
- Explicitly excludes Artemis `CommandServer`, gesture injection, screenshots, unrestricted ADB/shell and package-control surfaces from the APK.

## 0.5.0-alpha1 — adaptive transport / canonical Gemini conversation

- Adds a clean `develop/artemis-transport` line over the 0.4.0-alpha8 baseline.
- Keeps the daily UX unchanged: profile/language → Chat or Live.
- Replaces the assumption of a fresh provider chat per session with one canonical Gemini conversation per profile.
- Resolves the canonical chat directly or through semantic conversation search.
- Reconstructs a deleted/missing canonical chat from the authoritative local learning state and renames it deterministically.
- Normalizes unexpected starting state, including Gemini/Live already open, before context injection.
- Adds `GeminiStateObserver`, `GeminiConversationTransport`, `ConversationRegistry`, `CompatibilityMemory`, `SessionIntent` and semantic transport state.
- Adopts Artemis-inspired observe/act/verify/recover boundaries without adding arbitrary shell, unrestricted ADB or broad app-control authority to the APK.
- Separates UI compatibility memory from learner memory.
- Updates continuity capsules so provider history may help while local state always prevails.

# 0.4.0-alpha8

- Integra evidencia física de Nubia y Samsung: ambos probes alpha7 mostraron compositor válido pero `liveButtonDetected=false` aunque el control Live era visible.
- Añade resolver Live estructural: resource Robin → semántica → patrón de dos acciones a la derecha del compositor vacío (micrófono + Live).
- El probe v8 informa `liveButtonEvidence` y `composerRightActionCount` para dejar de diagnosticar Live como un booleano opaco.
- INICIAR crea siempre estado de sesión Aleyon nuevo; Recuperar queda explícito y no se mezcla con un inicio normal.
- Envío robustecido usando el contenedor real `assistant_chat_add_reply_*`/`assistant_mode_convergence_chat_input_layout` observado en ambos teléfonos.
- El cierre no puede promover progreso sin evidencia de sesión útil ni parsear su propia solicitud de debrief como resultado.
- Context Capsule limita también los campos largos del perfil para no llenar innecesariamente la conversación de Gemini.
- UI compactada: estados humanos, diagnóstico resumido y sin modal automático de historial al volver.
- Burbuja estilo chat-head: tamaño independiente de densidad, arrastre, snap al borde y toque fuera para contraer el panel.

# Changelog

## 0.4.0-alpha8 — 2026-09-17

- Simplifica el hot path de inicio: Aleyon ya no abre barra lateral, lista de conversaciones ni Nuevo chat. Usa la superficie normal que Gemini ya expone.
- Antes de pegar contexto, Live debe estar visible; Chat temporal queda rechazado por capacidad, no por nombre.
- Corrige Nubia: después de `ACTION_SET_TEXT`, el slot Robin de Live se convierte en la flecha azul de envío; `clickSendAction()` usa ese slot sólo cuando ya no se identifica como Live.
- El contexto visible deja de incluir `SESSION_ID`, `PROFILE_ID` y `SCHEMA_VERSION`; esos identificadores permanecen internos en Aleyon.
- Tras enviar contexto, Aleyon espera a que el compositor y Live reaparezcan antes de iniciar Live.
- `Cerrar sesión` durante preparación/error cancela de forma segura y vuelve a READY sin intentar generar un informe falso.
- Se conserva la capa de compatibilidad Samsung/Nubia del runtime alpha11: host Google/Gemini, ventanas focused/active, ScreenBoundsPolicy, Robin resources, burbuja y diagnóstico.

## 0.4.0-alpha6 — 2026-09-17

### Clean packaging / build audit

- Rebuilt the candidate as a complete Android project after detecting that an alpha3 source ZIP omitted `app/build.gradle`.
- Added `tests/verify_package.py`: independent required-file checks, version consistency, resource presence, architecture guard, bidirectional manifest coverage and cache/build-artifact rejection.
- Added `scripts/package_release.py` so release ZIPs are generated from the complete controlled tree rather than a hand-maintained list.
- Windows compilation now prints `PROJECT_ROOT` and `PACKAGE_VERSION` and refuses mixed/old/incomplete folders before Gradle.
- Removed obsolete prompt tombstone assets and upgrade-only alpha11 command aliases from production runtime.
- Kept the intended clean architecture: Aleyon owns continuity; Gemini is only the session engine; no Notebook lifecycle and no curtain.

## 0.4.0-alpha3 — 2026-09-17

Clean-line rebuild.

### Kept from the field-tested alpha11 runtime
- complete profile UI and Aleyon branding;
- verified Gemini host/Robin detection;
- semantic navigation and session-chat handling;
- physical Live launcher capability;
- Android window/root recovery;
- off-screen/virtualized-node protection;
- movable non-focusable Aleyon bubble;
- notifications, recovery journal and privacy-redacted passive diagnostics.

### Adapted from Idioma-Tutor architecture
- Aleyon-owned learner profile and learning state;
- bounded continuity capsule;
- local evidence ledger;
- structured session reflection;
- evidence validation before promotion;
- same pedagogical state for Chat and Live.

### Removed from production architecture
- provider-owned learning memory workflows;
- full-screen session masking;
- setup choreography unrelated to the current session-chat flow;
- obsolete prompt/template/deletion/repair subsystems from the previous product model.

### Windows build packaging fix
- `tests/run_core_tests.bat` now mirrors the current alpha3 core source set and no longer references removed alpha11 classes.
- `scripts/build_windows.ps1` now emits `AleyonBridge-v0.4.0-alpha3-debug.apk`.
- USB installer now prefers the alpha3 package name instead of obsolete alpha9 artifacts.

### Validation
- 11 core tests;
- static architecture/security QA;
- interaction-contract QA;
- 24 production Java files compile against Android stubs.

### Build-fix — Android resources
- Restaurados los launcher mipmaps de alpha11 (`ic_launcher` / `ic_launcher_round`) que usa el runtime recuperado.
- `AndroidManifest.xml` vuelve a referenciar los mipmaps reales del launcher.
- QA estático ahora valida referencias `R.mipmap`, `R.drawable`, `@mipmap` y `@drawable` contra recursos reales para evitar que los stubs oculten faltantes de AAPT.
