# ADR-0002 — Adaptive transport and canonical Gemini conversation

**Status:** accepted
**Date:** 2026-09-18
**Applies to:** `0.5.0-alpha2`

## Context

The 0.4 line proved that profile continuity and local learning memory work, but the Android layer still encoded too much knowledge about one particular Gemini UI state. It also treated every explicit session as a new Gemini conversation, producing provider-side chat sprawl and throwing away useful model-side conversational continuity.

## Decision

Bridge keeps the simple product surface: choose a language/profile, then choose **Live** or **Chat**. Under that surface, session transport becomes state-driven and canonical-conversation-aware.

For each profile Bridge maintains one deterministic provider-side conversation title (`ALEYON — <idioma>`). Provider history is a useful cognitive cache only. The local `LearningLedger` remains authoritative.

On start Bridge performs **observe → normalize → resolve → act → verify**:

1. observe the real Gemini state;
2. normalize unexpected starting states (including Gemini already open or Live already active);
3. open the conversation list semantically;
4. if the profile has no verified canonical-chat registry entry (migration/upgrade case), rebuild directly without searching;
5. if Aleyon previously verified the canonical chat, reuse it when visible or perform at most one bounded semantic search before rebuilding;
6. deliver the bounded local continuity capsule;
7. for rebuilt conversations, assign the deterministic canonical title;
8. enter Chat or Live and verify the requested state.

## Artemis boundary

`CONVERSATION_SEARCH` is explicitly distinct from `NORMAL_CHAT`. Search-query text is never accepted as a conversation result, and `chatComposer()` never falls back to the first arbitrary `EditText`. A start-time watchdog and bounded semantic replans guarantee fail-closed recovery rather than indefinite automation loops.

This branch now embeds a narrow derivative of the real Google Artemis implementation instead of only reproducing its ideas. `ArtemisRootResolver` is adapted from the Android accessibility helper's `HierarchyDumper` multi-window/focused-root recovery. `ArtemisFlashAgent` ports the FlashRunner execution shape (one observation → one action → observe again), while `ArtemisRoutineMemory` applies the bounded persistent-memory pattern so a successful semantic route is replayed until it stops matching. On mismatch the routine is invalidated and relearned from current observations.

The unsafe/general Artemis surfaces are deliberately not ported: `CommandServer`, loopback HTTP/RPC, `GestureController`, coordinate gestures, screenshots, unrestricted ADB/shell, package installation and token receiver are outside the APK. The Bridge transport surface remains allow-listed: conversation navigation, text delivery, Chat/Live transition, verification and recovery.

Full host-side Artemis remains an escalation path for a future Gemini variant that becomes semantically opaque; the embedded subset cannot honestly guarantee autonomous recovery from a UI that exposes no usable accessibility evidence.

## Field correction — 2026-09-18

Physical video QA showed a continuity capsule remaining in Gemini's composer until the user manually pressed Send. The old START flow had ended Artemis ownership at canonical navigation and then returned to a fixed service sequence for context send and Live. It also retried a failed Send resolver under the generic 32-attempt UI budget; after a manual send it could reinsert the capsule because it had no delivery postcondition.

Decision: Artemis ownership now extends through `context-delivery` and `live-start`. Context writing and submission are separate observed actions. A submit is committed only after a new observation proves the composer no longer contains the payload and conversation text advanced. Manual completion is accepted without duplicating the payload but is not learned as an automated success. Send verification is bounded to three attempts.

This embedded routine learner is not the full host-side Artemis reasoning runtime. It can replay, invalidate and relearn among semantic capabilities exposed by the Bridge adapter; a genuinely novel/opaque UI that requires model-based visual exploration remains an escalation case.

## Compatibility memory

`CompatibilityMemory` stores route/failure evidence. `ArtemisRoutineMemory` stores the successful semantic state/action routine keyed by the installed Gemini/Google version signature. Neither contains learner content. Unchanged UI replays the learned routine; changed UI invalidates and relearns it. Unknown/opaque UI still fails closed with evidence instead of guessing.

## Reconstruction rule

If the canonical Gemini conversation is deleted, missing or cannot be found, Bridge creates a new normal conversation and sends a reconstruction capsule built from the authoritative local learner state. Losing provider history therefore reduces convenience/context richness but does not lose learner identity or progress.

## UX invariant

No new daily decision is added. The learner still sees only the profile/language and **Iniciar Live** / **Chat** actions.


## Alpha2 material boundary

Study-file bytes remain in Android/Gemini's native data plane. Bridge/Artemis-derived transport may open and verify the native attachment surface but does not gain microphone, storage, System UI or arbitrary-file authority. `SessionMaterial` is metadata-only and `MaterialHandoffPolicy` accepts only user-selected `content://` references.
