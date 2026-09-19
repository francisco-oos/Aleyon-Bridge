# QA report — 0.4.0-alpha7

Date: 2026-09-17

## Source selection

The branch is rebuilt from two intentional sources only:

1. field-tested alpha11 Android/Gemini interaction capabilities;
2. local-memory/evidence patterns already established in `Idioma-Tutor-App`.

No legacy provider-owned memory workflow is carried into production code.

## Local POST validation

- PASS core tests: 11
- PASS static QA
- PASS interaction QA: 19 JS→native calls / 32 JS handlers
- PASS full Java stub compile: 24 production files

## Automated architecture guard

Static QA fails if production code/assets reintroduce any retired provider-memory lifecycle or a full-screen masking layer. It also verifies:

- local verified learning commit;
- bounded context capsule;
- `SESSION_ID` / `PROFILE_ID` / schema tagging;
- anti-fabrication instruction;
- session text delta and evidence gate;
- session-chat resolution;
- Chat + Live entry points;
- Robin Live launcher;
- Google-host verification;
- focused-window preference;
- off-screen protection;
- human consent pause;
- non-focusable bubble;
- redacted diagnostic probe;
- no coordinate gestures, microphone, Internet or broad overlay permission.

## Remaining gate

GitHub Actions must run the same QA and `assembleDebug` on the clean remote branch. Physical compatibility with the Nubia is not claimed until `docs/NUBIA_QA_PLAN.md` passes on the device.

## Packaging audit added in alpha4

The previous alpha3 packaging path was rejected after audit because a source ZIP could match its own manifest while still omitting an essential project file (`app/build.gradle`) and could carry `.test-out`. Alpha4 changes the acceptance rule:

- required structure is checked independently of the manifest;
- manifest coverage is bidirectional (listed ↔ actual controlled tree);
- caches/build outputs are forbidden in source packages;
- Windows build prints and verifies the exact project root/version;
- release ZIP generation is scripted and reproducible.

A package is not considered buildable until the structural preflight and the normal QA suite both pass from a freshly extracted directory.


## Fresh-session navigation audit

Alpha7 removes canonical-chat search, rename, and close-time history recovery. Start opens a fresh normal Gemini chat, injects context, records the evidence baseline only after the context ACK, then enters Chat/Live. Navigation to the conversation list is semantic-only and fails closed if the expected control is absent.


## Alpha7 field-correction invariants

- Live start is rejected before context injection unless Gemini exposes a normal composer **and** the real Live launcher.
- Temporary/ephemeral chat is never accepted as a Live session surface.
- Normal `Nuevo chat` selection is isolated from the adjacent Temporary-chat affordance and fails closed when ambiguous.
- The visible close flow contains no `ALEYON_REPORT_BEGIN`, `ALEYON_REPORT_END`, `EVENT|...`, session IDs or machine schema.
- Gemini's visible close response is limited to `Resumen / Avance / A reforzar / Próximo paso`.
- The bounded session text delta is retained in Aleyon's local learning store for future evidence-safe analysis.
