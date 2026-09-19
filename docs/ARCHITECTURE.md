# Architecture — Aleyon Bridge 0.5.0-alpha3

## Product definition

Aleyon Bridge is the local continuity layer that uses the official Gemini Android app as the cognitive, voice and multimodal session engine. The daily UX is intentionally small: choose a profile, then choose **Chat** or **Live**.

## Ownership boundary

Bridge owns learner profile/preferences, verified learning evidence, last session summary, progress/next objective, local session transaction state and transport compatibility diagnostics.

Gemini owns only the current provider interaction: reasoning, text conversation, Live voice, camera/screen and native file analysis.

**Provider history is disposable and is not part of the continuity model.**

## Session invariant

Every explicit Bridge start uses a **fresh normal Gemini chat**.

Bridge does not search old Gemini chats, assign deterministic titles, rename them, reconstruct them, or ask the user to recover them. The local continuity capsule is enough to start again after provider history is deleted or Android kills an unfinished run.

## Layering

```text
UI: profile → Chat / Live
        ↓
local learner continuity
  ContextCapsuleBuilder
  LearningLedger
        ↓
AleyonAccessibilityService
        ↓
embedded Artemis task runtime
  ArtemisRootResolver
  ArtemisFlashAgent
  ArtemisRoutineMemory
  GeminiStateObserver
        ↓
narrow Gemini transport
  GeminiConversationTransport
  GeminiUi
        ↓
official Gemini Android app
```

## Artemis task model

The runtime has only two product tasks.

### START_SESSION

Phases: `fresh-chat` → `context` → `live`.

The task observes the current Gemini surface, gets to a blank normal chat, delivers the local continuity capsule and either leaves Chat ready or verifies Gemini Live.

### CLOSE_SESSION

Phases: `end-live` → `debrief`.

After Live ends, Bridge waits for observable transcript stability, requests a short human-readable debrief in the same chat and commits only a verified parsed result.

`ArtemisRoutineMemory` stores `phase + semantic state + action`, never learner content. Phase-aware memory avoids confusing two identical `NORMAL_CHAT` observations that occur at different moments of the task.

## Observe → act → verify

For every provider interaction:

1. observe the current semantic state;
2. choose one allow-listed action;
3. execute it;
4. observe again;
5. advance only after the expected postcondition;
6. invalidate/relearn a mismatched routine;
7. fail closed when the surface cannot be verified.

Timing is used only for event debounce, backoff or polling of an external condition. No product action is executed merely because a fixed delay expired.

## Fresh-chat proof

`GeminiUi.isBlankNormalChat()` requires a verified normal composer and no visible user/assistant message containers. This is the postcondition after **Nuevo chat**.

A provider title is irrelevant to Bridge.

## Continuity capsule

A new chat receives a bounded local capsule with profile, correction/conversation preferences, last verified summary, next objective and recent verified evidence. Full history remains local.

Deleting Gemini chats has no effect on Bridge continuity.

## Session close

1. end Live if active;
2. return to the same normal chat;
3. observe transcript until it is stable;
4. compute current-session delta;
5. ask Gemini for four short lines: `Resumen`, `Avance`, `A reforzar`, `Próximo paso`;
6. parse only the new debrief;
7. commit verified summary/evidence/next objective locally;
8. notify the user.

## Material handoff

`SessionMaterial` is metadata-only. `MaterialHandoffPolicy` accepts user-selected `content://` references and conservative size/item limits. The transport may open Gemini's attachment surface but does not drive Android's document picker.

## Failure behavior

There is no recovery product mode.

If startup fails, the current attempt is marked with diagnostic evidence. The next explicit start discards unfinished provider transaction state and creates another fresh chat from local memory.

Legacy persisted `RECOVERING` values are migration data only and are normalized to idle state without exposing a recovery action.

## Security boundary

The APK has no unrestricted shell/ADB, general package control, coordinate gesture injection, automatic consent acceptance or remote WebView navigation. Accessibility is allow-listed to Gemini. Local learner memory remains the authoritative state.
