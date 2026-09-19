# QA report — Aleyon Bridge 0.5.0-alpha1 (pre-device)

**Branch:** `develop/artemis-transport`
**Baseline:** `0.4.0-alpha8`
**Date:** 2026-09-18

## Scope

Architectural refactor from fresh-chat-per-session automation to adaptive semantic transport with one canonical Gemini conversation per profile and local reconstruction when provider history disappears.

## Automated results

- package preflight: PASS
- core tests: PASS (12)
- static architecture/security QA: PASS
- WebView/native interaction QA: PASS (19 JS→native calls, 35 JS handlers)
- full production Java syntax/internal reference compile against Android stubs: PASS (32 production Java files)

## Verified invariants

- daily UX remains profile/language → Chat or Live;
- local `LearningLedger` remains authoritative;
- canonical Gemini conversation is navigation/cognitive cache only;
- missing canonical chat enters a reconstruction path;
- arbitrary initial normal chat is normalized through canonical resolution before context injection;
- manually open Live is treated as an initial state and exited before canonical resolution;
- provider selectors are isolated behind `GeminiConversationTransport` rather than encoded directly in the service flow;
- compatibility route/evidence memory is separate from learner memory;
- no coordinate gestures introduced;
- no INTERNET, microphone or broad overlay permission introduced;
- no arbitrary shell/ADB/package-control surface added to the APK;
- human consent remains user-owned.

## Not yet verified

Physical Gemini behavior for drawer search, chat-level rename dialog and save labels must be confirmed on the Nubia and a second device. Stub compilation cannot prove OEM/Gemini accessibility behavior.

## Required next gate

Execute `docs/NUBIA_QA_PLAN.md`. Do not promote to `main` until canonical reuse, unrelated-chat start, already-open-Live start and deleted-chat reconstruction all pass on physical devices.
