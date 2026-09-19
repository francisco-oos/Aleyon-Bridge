# Changelog

## 0.5.0-alpha9 — Live termina desde Gemini / tutor menos robótico

- Revisión de las nuevas grabaciones físicas Samsung y Nubia.
- Elimina de la burbuja la ruta duplicada **Finalizar Live y guardar**. En Live, la única señal de fin es ahora la X nativa de Gemini.
- Aleyon observa el regreso de `LIVE_ACTIVE` a `NORMAL_CHAT`, exige 900 ms de estabilidad y entonces inicia automáticamente `CLOSE_SESSION`.
- Una transición breve a chat normal no dispara el cierre; si Live reaparece, el detector se reinicia.
- Chat conserva `Guardar y cerrar chat` porque no dispone de una señal nativa equivalente de fin de sesión.
- Durante el cierre la burbuja queda informativa: permite volver a Gemini, pero no ofrece un segundo disparador de cierre.
- El contexto pedagógico deja de pedir una confirmación tipo “Got it / I'm ready”. Gemini debe integrar el perfil silenciosamente y comenzar directamente con una intervención natural en el idioma objetivo.
- La conversación prioriza significado y fluidez, una pregunta principal por turno, correcciones mediante reformulación breve y dificultad adaptada al desempeño real.
- El prompt de debrief se acorta: pide exactamente cuatro líneas separadas, una frase breve por línea, sin continuar preguntas pendientes ni explicar razonamiento.


## 0.5.0-alpha8 — cierre por evidencia / menos comportamiento robótico

- Revisión basada en diez grabaciones físicas Nubia/Samsung.
- El modo imagen y la conversación multimodal quedan intactos: la generación de imagen funcionó dentro de la sesión y no requiere una ruta especial de Bridge.
- Corrige una condición crítica: contexto y debrief ya no se consideran enviados porque “cambió suficiente texto” en Gemini. Bridge exige encontrar el payload dentro de un contenedor real de mensaje del usuario.
- El slot derecho reutilizado por Gemini (Live / Enviar / Stop) ya no puede pulsarse como Enviar mientras exista evidencia de respuesta activa.
- El detector global deja de interpretar una palabra del alumno como `Stop`/`Detener` como si fuera un control del sistema.
- Artemis invalida rutas antiguas mediante `ARTEMIS_POLICY_VERSION=4`; perfiles y memoria pedagógica no se borran.
- Tras Live, el transcript se observa hasta acumular una ventana real de quietud de 6 s; se elimina el scroll automático durante estabilización.
- Se elimina el ambiguo `Responde ahora al mensaje anterior`, que en videos reactivó preguntas pendientes de Live o produjo respuestas genéricas.
- Si el primer debrief no genera respuesta o genera una respuesta inválida, Bridge espera que Gemini quede ocioso y repite una sola vez el contrato completo; el retry incluye evidencia acotada de la práctica.
- `Responder ahora` sigue siendo una capacidad opcional cuando Gemini la expone durante razonamiento prolongado.
- El instalador USB limpia una selección Android de `Wait for debugger`, condición observada en una prueba Samsung.


## 0.5.0-alpha7 — convergencia física entre teléfonos / cierre recuperable

- Separa dos fallos de campo que no son el mismo problema: un intento donde Artemis agotó la exploración de Live aunque el control terminó visible, y el Samsung donde Live sí conversó correctamente pero el debrief quedó enviado sin que Gemini generara respuesta.
- START_SESSION distingue mejor una respuesta de Gemini todavía en curso de un Live realmente fuera del viewport. Mientras Gemini procesa, Artemis espera; sólo explora después.
- El fallback estructural de Live respeta `isShowingHintText()` para que un placeholder accesible no parezca texto escrito y oculte falsamente la capacidad Live.
- CLOSE_SESSION espera al menos 4 s de asentamiento tras salir de Live y no envía el debrief mientras Gemini siga mostrando una respuesta en curso.
- Si Gemini entra en pensamiento prolongado y ofrece `Responder ahora` / `Respond now`, Bridge puede activarlo semánticamente una sola vez.
- Si el globo del debrief fue aceptado pero durante 20 s no aparece respuesta ni señal de procesamiento, Bridge envía un único mensaje corto de recuperación en el mismo chat; no duplica el cierre indefinidamente.
- El cierre sigue ligado a la evidencia de la sesión actual: cambiar a otro chat no permite consumir un resumen viejo.
- ArtemisRoutineMemory continúa siendo local por instalación y sólo guarda secuencias semánticas exitosas. La consistencia entre teléfonos depende de percepción/adaptación semántica, no de compartir una ruta rígida entre dispositivos.


## 0.5.0-alpha6 — cierre ligado a la sesión / notificación detallada

- Corrige el fallo físico donde, mientras Gemini tardaba en responder, cambiar a un chat anterior permitía que Aleyon interpretara un cierre viejo como el de la sesión actual.
- CLOSE_SESSION conserva una huella efímera basada en evidencia real de la práctica actual y exige continuidad de esa evidencia antes de enviar o aceptar el debrief.
- Si el usuario cambia de conversación, Aleyon pausa el cierre con “Vuelve al chat de esta sesión…” y no parsea ni hace commit del otro chat.
- No se reintroducen títulos, búsqueda, renombrado ni recuperación de conversaciones del proveedor.
- Al regresar al chat correcto, continúa la espera sensible a progreso de alpha5.
- Al abrir la notificación, Aleyon muestra directamente el cierre completo más reciente y permite consultar sesiones anteriores.
- Añade regresiones para el caso exacto de dos chats con el mismo prompt de cierre.


## 0.5.0-alpha5 — Artemis explora el viewport / cierre tolerante a red lenta

- Añade exploración semántica aprendible a START_SESSION cuando el launcher Live queda fuera del viewport porque Gemini ya estaba abierto o desplazado.
- Artemis aprende SCROLL_FORWARD / SCROLL_BACKWARD, reobserva tras cada acción e invalida un replay de scroll si Live ya está visible.
- GeminiStateObserver reconoce una conversación Robin desplazada aunque temporalmente el compositor no esté visible.
- Mantiene sólo START_SESSION y CLOSE_SESSION: no reintroduce nombre/ID de chat, búsqueda, renombrado ni recuperación.
- El cierre conserva el mismo chat observado y sustituye el timeout rígido de 45 s por espera sensible a progreso.
- Tras 15 s la burbuja informa que Gemini tarda; mientras el texto siga cambiando se renueva la espera. Sólo tras 180 s sin progreso se usa el fallback local.
- Un segundo cierre no puede reiniciar la transacción ni reenviar el debrief.
- La simulación amplía aislamiento a 200 casos de perfil y cruza Live/Chat/manual/background con red rápida/lenta/streaming/timeout, además de Live fuera de viewport.


## 0.5.0-alpha4 — cierre físico sobre la misma sesión Gemini

- Corrige el fallo reproducido físicamente en Nubia y Samsung donde el cierre podía relanzar Gemini antes de observar la superficie Live/chat activa.
- El cierre ahora se guía por el estado observado: termina Live si sigue activo, continúa directamente si ya está en Chat y sólo relanza Gemini cuando no hay una superficie verificada disponible.
- Artemis guarda la rutina CLOSE_SESSION cuando Gemini acepta la petición de debrief; ya no depende de que el modelo llegue a responder para conservar lo aprendido del transporte Android.
- Añade espera acotada de 45 s para el debrief. Si Gemini no responde, Aleyon conserva evidencia real de la sesión y cierra localmente sin inventar una evaluación pedagógica.
- La burbuja distingue Live, Chat y cierre en curso, y evita reiniciar accidentalmente un cierre ya iniciado.
- El parser acepta variantes inocuas con Markdown/viñetas.
- La simulación sube a 100 perfiles, 8 intentos por perfil y 600 casos específicos de cierre.
- No se reintroducen títulos, búsqueda, renombrado ni recuperación de chats de Gemini; la continuidad sigue siendo propiedad local de Aleyon.

## 0.5.0-alpha3 — disposable Gemini sessions / simplified Artemis lifecycle

- Removes canonical Gemini conversations, provider chat search/reuse/rebuild and automated rename.
- Removes `ConversationRegistry`, `CanonicalConversationPolicy`, `CanonicalChatRoutingPolicy` and `RecoveryPlanner` from production.
- Removes the user-facing **Recuperar** action and recovery command/state.
- Every explicit Bridge start now creates/verifies a fresh normal Gemini chat, sends the locally-owned profile + continuity capsule, then enters Chat or Live.
- Gemini-generated conversation titles are ignored by the runtime.
- Collapses embedded Artemis behavior to two phase-aware tasks: `START_SESSION` and `CLOSE_SESSION`.
- `ArtemisRoutineMemory` now stores phase + semantic state + action so identical `NORMAL_CHAT` observations cannot replay the wrong step.
- Close flow still verifies transcript stability, asks Gemini for the short debrief in the same chat and commits summary/evidence/next objective locally.
- Adds static/interaction guards so canonical-chat, rename and recovery logic cannot silently return.
- Updates architecture/ADR documentation to make provider history explicitly disposable.

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
- Physical-video fix: Artemis now owns `context-delivery` and `live-start`, not only canonical navigation; context write/send are separately observed and verified.
- Fixes current Google-host Gemini keeping stale Live semantics on the compose action slot after text insertion; a non-empty verified composer may use that known slot as Send, with a mandatory postcondition check.
- A manual Send during recovery is detected as successful delivery and no longer causes the continuity capsule to be inserted a second time.
- Replaces the generic 32-retry Send failure path with at most three verified submit attempts and reduces normal reactive settling delays.

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
