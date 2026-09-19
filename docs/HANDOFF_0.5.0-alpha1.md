# Handoff — Aleyon Bridge 0.5.0-alpha1

## What changed

This branch is not another selector patch. It changes the session-start contract.

Old behavior:

`assume current Gemini shape → fresh chat every session → inject → Live/Chat`

New behavior:

`observe → normalize → resolve canonical chat → reuse or reconstruct → inject local continuity → verify Chat/Live`

## User-visible contract that must not regress

- open Bridge;
- choose the language/profile;
- tap **Iniciar Live** or **Chat**;
- no provider selector/settings choice;
- profile, progress and next objective are already carried into Gemini.

Do not add setup choreography to the daily path.

## Files to review first

- `docs/ADR-0002_ADAPTIVE_TRANSPORT_AND_CANONICAL_CHAT.md`
- `docs/ARCHITECTURE.md`
- `docs/NUBIA_QA_PLAN.md`
- `app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java`
- `app/src/main/java/com/aleyon/geminibridge/transport/`
- `app/src/main/java/com/aleyon/geminibridge/core/ContextCapsuleBuilder.java`

## Architectural rules

1. `LearningLedger` is authoritative learner memory.
2. The Gemini canonical conversation is only a useful cognitive cache.
3. A missing/deleted canonical conversation must be reconstructed from local state.
4. The service should reason in semantic transport states/actions; provider selectors belong behind the narrow adapter.
5. Do not add device-brand branches or absolute coordinates.
6. Do not add arbitrary shell, unrestricted ADB, arbitrary package control, public daemon access or automatic consent decisions to the APK.
7. Unknown Gemini UI variants must fail closed with evidence rather than guess.

## Important physical checks

The automated suite cannot prove current Gemini accessibility behavior for:

- conversation drawer search;
- chat-level options selection;
- rename dialog field focus;
- rename confirmation label;
- title visibility/truncation on different Gemini builds.

Run the full `docs/NUBIA_QA_PLAN.md` before promoting.

## Artemis role

For this alpha, Artemis is a development/QA/exploration companion and an architectural source for observe/act/verify/recover. It is not copied wholesale into the APK.

If a physical test exposes a new Gemini state, prefer using Artemis to explore and collect the actual accessibility/visual evidence, then promote the smallest safe semantic capability into `GeminiConversationTransport`/`GeminiUi` with tests. Do not hardcode a one-device sequence in `AleyonAccessibilityService`.

## Automated baseline

Expected:

```text
PASS package preflight
PASS core tests: 12
PASS static QA
PASS interaction QA: 19 JS->native calls, 35 JS handlers
PASS full Java stub compile: 32 production files
```
