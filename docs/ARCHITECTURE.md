# Architecture — Aleyon Bridge 0.5.0-alpha2

## Product definition

Aleyon Bridge is the local continuity layer that uses the official Gemini Android app as the cognitive, voice and multimodal session engine. The daily UX remains deliberately small: choose a language/profile, then choose **Chat** or **Live**.

## Ownership boundary

Aleyon Bridge owns learner identity, profile/preferences, learning memory/evidence, progress/next objective, canonical-conversation registry, reconstruction state, transport compatibility memory and recovery journal.

Gemini provides reasoning, conversational responses, Live voice, native file analysis and optional useful provider-side history. Provider history is never authoritative.

## Control plane vs data plane

The embedded Artemis transport is the **control plane**. Bridge vendors a narrow Android-safe subset adapted from Google Artemis (Apache-2.0): multi-window root recovery from the Artemis accessibility helper plus a Flash-style reactive action loop with bounded routine memory. Bridge gives this runtime an intent; Artemis observes the current Gemini surface, chooses one allow-listed action, executes it, observes again and either replays or relearns the route.

Android/Gemini is the **data plane** for media:

- Live microphone/camera/screen are owned by Gemini;
- selected document/image/audio/video bytes are handed through Android/Gemini native mechanisms;
- Bridge does not request microphone/camera/storage permissions and does not copy file bytes into the learner ledger.

This is why disabling microphone authority from Artemis/Bridge does not prevent Gemini Live or Gemini file analysis.

## Layering

```text
UI: profile → Chat / Live
        ↓
SessionIntent (+ optional material metadata)
        ↓
Learning continuity
  ContextCapsuleBuilder
  LearningLedger
        ↓
Embedded Artemis transport
  ArtemisRootResolver
  ArtemisFlashAgent
  ArtemisRoutineMemory
  GeminiStateObserver
  ConversationRegistry
  CompatibilityMemory
  GeminiConversationTransport
        ↓
Narrow provider adapter
  GeminiUi
        ↓
Official Gemini Android app
```

`GeminiUi` is the provider-specific capability adapter. `AleyonAccessibilityService` does not encode a navigation script: it delegates canonical-chat navigation to `ArtemisFlashAgent`, which re-observes after every action. `ArtemisRoutineMemory` caches successful semantic state/action sequences by Gemini/Google package-version signature. A matching routine is replayed; any state or action mismatch invalidates it and the Flash agent falls back to semantic exploration, learns the new successful sequence and stores it.

## Canonical conversation lifecycle

Each profile has a deterministic title: `ALEYON — <target language>`.

Start performs: observe → normalize → resolve canonical conversation → reuse or reconstruct → inject bounded local continuity → verify requested Chat/Live state.

Routing is deliberately asymmetric: an existing profile with no local canonical-conversation record is treated as a migration and **rebuilds directly without search**. Conversation search is used only when `ConversationRegistry` says that Aleyon previously verified that canonical chat and it is no longer immediately visible. Search is a separate `CONVERSATION_SEARCH` state; an arbitrary editable field can never be promoted to the normal chat composer. A bounded replan budget plus a start watchdog aborts safely instead of leaving `Preparando…` frozen indefinitely.

If the canonical conversation is deleted, reconstruction uses local learner truth. The provider chat is a cognitive cache, not memory ownership.

## Embedded Artemis execution scope

The embedded Artemis runtime owns the complete start transport, not only canonical-chat navigation. It keeps independent learned routines for:

- `canonical-nav`: reach/recover the correct Gemini conversation;
- `context-delivery`: write the continuity capsule, submit it and verify the postcondition;
- `live-start`: activate Live and verify `LIVE_ACTIVE`.

A context submit is successful only when a fresh observation proves that the prepared payload left the composer and the conversation advanced. If the user submits manually during recovery, Bridge accepts the observed postcondition but does not falsely record that manual action as an automated success. Three unverified submit attempts invalidate/fail the route quickly instead of consuming the generic UI retry budget.

Routine learning is intentionally narrower than full upstream Artemis Flash/Explorer. The APK stores and replays verified semantic state/action sequences and invalidates them on mismatch. Full novel-UI reasoning in upstream Artemis uses a host-side model/runtime; it is not silently claimed to exist inside the offline-safe Bridge APK.

## Observe → act → verify → recover

- **observe**: classify current Gemini state (`TransportState`);
- **act**: invoke one allow-listed semantic action;
- **verify**: require the expected postcondition before advancing;
- **recover**: normalize known unexpected states;
- **fail closed**: unknown variants preserve local state and record evidence instead of guessing.

## Material handoff

`SessionMaterial` is metadata-only. `MaterialHandoffPolicy` accepts only `content://` references chosen by the user, limits batches to 10 and enforces conservative size ceilings. The current production transport may open Gemini's native attachment surface but intentionally does not control the Android document picker.

The actual study flow is:

```text
profile/context → canonical Gemini chat → native attachment UI → user selects item → Gemini analyzes → session close → local learning evidence
```

Untrusted document content is never allowed to become an Artemis shell/tool argument because Bridge has no general command surface.

## Compatibility immune memory

`CompatibilityMemory` contains route/failure evidence only, never learner transcript or pedagogy. `ArtemisRoutineMemory` separately stores successful navigation routines only; it contains no learner content. When Gemini changes, a mismatched routine is discarded and the embedded Flash loop relearns from current semantic observations. A completely opaque/unknown surface still fails closed; host-side full Artemis remains the escalation path for variants that no longer expose enough accessibility semantics.

## Session close

1. end Live or finish Chat;
2. wait for text to settle;
3. capture current-session visible delta;
4. request a short debrief;
5. parse only the new debrief;
6. persist summary + next objective + progress evidence + reinforcement evidence;
7. read back the local commit;
8. notify the user.

## Security boundary

The APK has no arbitrary shell, unrestricted ADB, public daemon, arbitrary package control, coordinate gesture injection, automatic consent acceptance or remote WebView navigation. Accessibility is package-allow-listed to Gemini. Android backup is disabled for the local learner store.
