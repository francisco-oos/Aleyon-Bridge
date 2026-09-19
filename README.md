# Aleyon Bridge 0.5.0-alpha2

Aleyon Bridge is a local learning-continuity layer over the official Gemini Android app.

## Daily experience stays simple

The normal user still does only this:

```text
open Bridge
  ↓
choose language/profile
  ↓
Chat  or  Iniciar Live
  ↓
Gemini opens with the relevant profile, progress and next objective
```

The adaptive transport stays invisible unless Gemini needs a human decision or an unknown UI variant is detected.

## Responsibility split

```text
Aleyon Bridge      → stores profile, progress, evidence and reconstruction state
Adaptive transport → carries, brings back and adapts; observes/verifies/recoveries Gemini UI
Android / Gemini   → transports selected file bytes and provides reasoning, Chat, Live and multimodality
```

**Artemis carries, brings back and adapts. Gemini thinks. Bridge remembers.**

Artemis-derived transport does not need microphone access and does not read study-file bytes. Gemini owns Live audio/camera/screen. For documents/images/audio/video, Android's native picker and Gemini own the byte handoff; Bridge carries the session intent/context and verifies the surrounding flow.

## Canonical Gemini conversation

Each language/profile uses one deterministic Gemini conversation:

`ALEYON — <idioma>`

That conversation is a useful **cognitive cache**, not authoritative memory. Bridge still sends a bounded local continuity update every session. If the Gemini conversation disappears, Bridge reconstructs it from the local `LearningLedger` and continues under the same canonical title.

## Adaptive start flow

```text
observe current Gemini state
  ↓
normalize (Gemini already open / another chat / Live already active)
  ↓
resolve the canonical conversation
  ├─ found → reuse
  └─ missing → create + reconstruct + rename
  ↓
deliver bounded local context
  ↓
verify Chat or enter/verify Live
```

No device-brand branches, absolute screen coordinates or accessibility gesture injection are allowed in production.

## Study materials

0.5.0-alpha2 introduces the safe material-handoff contract:

```text
Bridge profile + learning goal
        ↓
Adaptive transport opens/verifies the correct Gemini chat
        ↓
Gemini native attachment surface
        ↓
User/Android selects the document, image, audio or video
        ↓
Gemini reads/analyzes the actual bytes
        ↓
Bridge stores only learning results/evidence needed for continuity
```

`SessionMaterial` stores metadata only. `MaterialHandoffPolicy` accepts user-selected `content://` references, rejects filesystem `file://` paths and applies conservative item/size guards. The current alpha does **not** automate the Android document picker; that is deliberate to avoid broad filesystem/system-UI authority.

## Compatibility immune memory

`CompatibilityMemory` is separate from `LearningLedger`. Known Gemini routes remain cheap; unknown UI states fail closed with evidence rather than guessing. Host-side Artemis can then explore a new variant and help promote the smallest verified semantic rule.

## Session close and profile enrichment

Every verified close now persists:

- a bounded session summary;
- next objective;
- session transcript delta;
- observed progress evidence;
- explicit reinforcement evidence;
- recent session history.

Deleting a provider conversation does not delete these facts.

## Security invariants

- no microphone, camera, Internet or storage permission in Bridge;
- no `MANAGE_EXTERNAL_STORAGE`, broad overlay or package-install permission;
- Android backup disabled for local learner memory;
- Accessibility limited to Gemini packages;
- `canPerformGestures=false`;
- no arbitrary shell / unrestricted ADB / package control in the APK;
- WebView is local-only, debugging disabled, external navigation blocked;
- human consent remains human-owned;
- unknown provider UI fails closed.

Artemis is not embedded wholesale. Known open Artemis command-injection surfaces involving arbitrary shell/package/notification hooks are explicitly outside the production Bridge capability set.

## Development source of truth

`develop/artemis-transport`

`develop/bridge-clean` remains the historical 0.4 baseline.

## QA

Run the same suite on Linux/macOS or Windows:

```bash
bash tests/run_core_tests.sh
```

The suite includes package/version parity, Java core tests, a 60-profile/360-session contract matrix, static architecture QA, WebView/native interaction QA, security QA, Windows/Linux build-runner parity and full Java stub compilation. The published `develop/artemis-transport` candidate also passed real Android `clean assembleDebug` in GitHub Actions with Android SDK 35, Gradle 8.9 and Java 17; the resulting APK artifact was verified and uploaded.

Physical Gemini behavior is still a separate promotion gate. See `docs/NUBIA_QA_PLAN.md`.
