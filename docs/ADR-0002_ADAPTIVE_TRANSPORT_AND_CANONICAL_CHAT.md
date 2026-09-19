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
4. resolve the canonical conversation directly or through conversation search;
5. if it is missing, create a normal conversation and reconstruct continuity from local memory;
6. deliver the bounded local continuity capsule;
7. for rebuilt conversations, assign the deterministic canonical title;
8. enter Chat or Live and verify the requested state.

## Artemis boundary

This branch adopts the useful Artemis principles without importing unrestricted Artemis authority into the APK. The Bridge transport surface is allow-listed: conversation navigation, text delivery, Chat/Live transition, verification and recovery. It does **not** expose arbitrary shell commands, arbitrary package control, installation, public daemon endpoints or unrestricted ADB execution.

A host-side Artemis runtime remains useful for QA, unknown-UI exploration and future recovery escalation. Any future integration must preserve the same narrow transport contract.

## Compatibility memory

`CompatibilityMemory` stores only validated transport route/evidence and failure counts. It contains no learner content. This is the first local implementation of the "compatibility immune system": known variants use a cheap verified route; unknown variants fail closed with evidence suitable for exploration and later promotion.

## Reconstruction rule

If the canonical Gemini conversation is deleted, missing or cannot be found, Bridge creates a new normal conversation and sends a reconstruction capsule built from the authoritative local learner state. Losing provider history therefore reduces convenience/context richness but does not lose learner identity or progress.

## UX invariant

No new daily decision is added. The learner still sees only the profile/language and **Iniciar Live** / **Chat** actions.


## Alpha2 material boundary

Study-file bytes remain in Android/Gemini's native data plane. Bridge/Artemis-derived transport may open and verify the native attachment surface but does not gain microphone, storage, System UI or arbitrary-file authority. `SessionMaterial` is metadata-only and `MaterialHandoffPolicy` accepts only user-selected `content://` references.
