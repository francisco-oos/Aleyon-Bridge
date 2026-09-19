# Architecture — Aleyon Bridge 0.5.0-alpha2

## Product definition

Aleyon Bridge is the local continuity layer that uses the official Gemini Android app as the cognitive, voice and multimodal session engine. The daily UX remains deliberately small: choose a language/profile, then choose **Chat** or **Live**.

## Ownership boundary

Aleyon Bridge owns learner identity, profile/preferences, learning memory/evidence, progress/next objective, canonical-conversation registry, reconstruction state, transport compatibility memory and recovery journal.

Gemini provides reasoning, conversational responses, Live voice, native file analysis and optional useful provider-side history. Provider history is never authoritative.

## Control plane vs data plane

Aleyon/Artemis-derived transport is a **control plane**. It decides which semantic state must be reached and verifies that state.

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
Adaptive transport
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

`GeminiUi` is the only provider-specific accessibility adapter. `AleyonAccessibilityService` consumes semantic states/actions and must not encode one device's selector sequence.

## Canonical conversation lifecycle

Each profile has a deterministic title: `ALEYON — <target language>`.

Start performs: observe → normalize → resolve canonical conversation → reuse or reconstruct → inject bounded local continuity → verify requested Chat/Live state.

Routing is deliberately asymmetric: an existing profile with no local canonical-conversation record is treated as a migration and **rebuilds directly without search**. Conversation search is used only when `ConversationRegistry` says that Aleyon previously verified that canonical chat and it is no longer immediately visible. Search is a separate `CONVERSATION_SEARCH` state; an arbitrary editable field can never be promoted to the normal chat composer. A bounded replan budget plus a start watchdog aborts safely instead of leaving `Preparando…` frozen indefinitely.

If the canonical conversation is deleted, reconstruction uses local learner truth. The provider chat is a cognitive cache, not memory ownership.

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

`CompatibilityMemory` contains route/failure evidence only, never learner transcript or pedagogy. Host-side Artemis can explore a changed Gemini build; only the smallest verified semantic capability is promoted to `GeminiUi`/`GeminiConversationTransport`.

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
