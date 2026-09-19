# ADR-0001 — Aleyon owns continuity

**Status:** accepted  
**Date:** 2026-09-17  
**Updated:** 2026-09-19 for `0.5.0-alpha3`

## Decision

Aleyon Bridge owns learner profile, pedagogical memory, progress, evidence and continuity. Gemini is only the reasoning/voice/multimodal session engine.

Beginning with alpha3, provider conversations are deliberately **disposable**. Every explicit Bridge session starts from a fresh normal Gemini chat and receives the bounded local continuity needed for that session.

## Why

The canonical-chat/reuse/rebuild experiment made a simple product dependent on provider history, titles, search UI and recovery states. Physical testing showed that those extra responsibilities created more failure points without improving local continuity.

Local memory already contains the information required to continue learning, so provider chat identity is unnecessary.

## Invariants

- Local learner state is authoritative.
- Provider history is optional and never required.
- Every explicit start uses a fresh normal Gemini chat.
- Bridge does not search, rename or reconstruct provider conversations.
- There is no user-visible provider-chat recovery workflow.
- Every session has a local `SESSION_ID`.
- The continuity capsule is bounded and high-signal.
- Full history stays local.
- Chat and Live share the same local profile and learning state.
- A deleted Gemini chat changes nothing about the next Bridge session.
- Gemini cannot mutate the local learning ledger directly.
- Images/camera/screen remain native Gemini capabilities.
- Unknown UI variants fail closed rather than guessed navigation.

## Promotion rule

A refactor is not an improvement if it adds provider state that Bridge does not need. CI is necessary, but physical multi-device QA—including Nubia—remains a promotion gate.
