# ADR-0001 — Aleyon owns continuity

**Status:** accepted  
**Date:** 2026-09-17  
**Updated:** 2026-09-18 for `0.5.0-alpha1`

## Decision

Aleyon Bridge owns learner profile, pedagogical memory, progress, evidence and continuity. Gemini is the reasoning/voice/multimodal session engine.

0.5 adds a reusable canonical Gemini conversation, but this does **not** transfer memory ownership to Gemini. Provider history is a useful cognitive cache. If the canonical conversation disappears, Bridge reconstructs it from local state.

## Invariants

- Provider history may help, but is never authoritative memory.
- Local learner state prevails when provider history conflicts with the current continuity capsule.
- Every session has a local `SESSION_ID`.
- The capsule is bounded and high-signal; full history stays local.
- Chat and Live share the same local profile and learning state.
- A deleted/missing Gemini conversation must be reconstructable from local state.
- No machine-report protocol is rendered into the normal Gemini conversation.
- Images/camera/screen remain native Gemini capabilities unless/ until Bridge explicitly adds material transport.
- No full-screen masking layer is part of the runtime.

## Promotion rule

A refactor is not an improvement if it removes a capability already demonstrated on physical devices. CI is necessary but multi-device physical QA, including Nubia, remains a promotion gate.
