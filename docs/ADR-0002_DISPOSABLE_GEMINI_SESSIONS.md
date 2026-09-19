# ADR-0002 — Disposable Gemini sessions

**Status:** accepted  
**Date:** 2026-09-19  
**Applies from:** `0.5.0-alpha3`

## Context

Bridge previously maintained one deterministic Gemini conversation per profile and implemented provider-side search, reuse, reconstruction, rename and recovery.

Physical testing on the Nubia showed that the extra provider-history choreography could fail even when the actual capabilities needed by the product—normal composer, Send and Gemini Live—were all correctly exposed.

## Decision

Each explicit Bridge session uses a new normal Gemini chat.

Artemis has only two product tasks:

- **START_SESSION:** get to a fresh chat → deliver local continuity → start Live/Chat.
- **CLOSE_SESSION:** end Live → obtain debrief → commit locally.

Gemini-generated chat titles are ignored by the runtime.

## Consequences

Positive:

- fewer provider UI dependencies;
- no title/search/rename coupling;
- no canonical registry;
- no user recovery action;
- provider chat deletion becomes irrelevant;
- Android/OEM variation has fewer surfaces to break.

Trade-off:

- Gemini history may contain multiple independent Bridge chats.
- Continuity depends entirely on the quality of Bridge local memory/capsule, which is intentional.

## Non-goals

This does not remove diagnostics, safe retry/backoff, consent handling or fail-closed behavior. Those are transport safety, not provider-memory choreography.
