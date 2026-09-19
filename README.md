# Aleyon Bridge 0.5.0-alpha3

Aleyon Bridge is a local learning-continuity layer over the official Gemini Android app.

## Daily flow

The product deliberately does very little:

```text
open Bridge
  ↓
choose profile
  ↓
Chat or Iniciar Live
  ↓
open a fresh normal Gemini chat
  ↓
send profile + local continuity + current objective
  ↓
Chat / Gemini Live
  ↓
close session
  ↓
Gemini gives a short debrief in the same chat
  ↓
Bridge stores the verified result locally
```

There is no canonical Gemini conversation, provider-side learner memory, notebook lifecycle or user-visible recovery workflow.

## Responsibility split

```text
Aleyon Bridge  → profile, progress, evidence, summaries, next objective
Artemis layer  → observes Android, transports context, verifies actions
Gemini         → reasons, converses, Live, voice/camera/screen, file analysis
```

**Artemis transports. Gemini thinks. Bridge remembers.**

Provider chat history is disposable. A session can disappear from Gemini without damaging the learner profile because the next session is reconstructed from local Bridge state automatically.

## Two Artemis tasks

The embedded Android-safe Artemis derivative now learns only two product tasks:

### START_SESSION

```text
observe Gemini
  ↓
reach/create a fresh normal chat
  ↓
write continuity capsule
  ↓
verify Send
  ↓
verify Live capability
  ↓
start Live (or leave Chat ready)
```

### CLOSE_SESSION

```text
end Live if needed
  ↓
observe transcript until stable
  ↓
request short debrief in the same chat
  ↓
parse result
  ↓
verified local commit
```

Routine memory is phase-aware, so the same Gemini screen can legitimately require a different action during fresh-chat creation, context delivery, Live startup or debrief. A mismatched learned step is invalidated and relearned instead of blindly replayed.

## Intentionally removed in alpha3

- deterministic `ALEYON — <idioma>` Gemini chat names;
- provider conversation registry;
- conversation search/reuse/rebuild logic;
- automated chat renaming;
- canonical-chat recovery;
- the **Recuperar** button and recovery command;
- recovery planner and recovery session stage.

If Android kills an unfinished run, the next explicit **Iniciar Live** or **Chat** starts a clean provider session from the authoritative local profile. The user never has to repair a Gemini conversation.

## Continuity capsule

Each new provider chat receives a bounded high-signal capsule containing the current profile, preferences, last verified summary, next objective and recent verified evidence. Full learning history stays local.

Gemini is instructed not to invent memories or claim progress that Bridge did not provide.

## Study materials

Bridge keeps only metadata and learning intent. Android/Gemini owns the actual selected bytes. The current alpha does not automate the Android document picker.

## Security invariants

- no microphone, camera, Internet or storage permission in Bridge;
- no unrestricted ADB/shell/package control inside the APK;
- no coordinate gesture injection;
- Accessibility is allow-listed to Gemini packages;
- `canPerformGestures=false`;
- human consent dialogs are never auto-accepted;
- Android backup is disabled for learner memory;
- local WebView only; remote navigation/debugging disabled;
- unknown UI variants fail closed instead of guessing.

## Development source of truth

`develop/artemis-transport`

`develop/bridge-clean` remains the historical 0.4 baseline.

## QA

Run:

```bash
bash tests/run_core_tests.sh
```

The suite covers package/version parity, Java core contracts, a 60-profile multi-session simulation, static architecture QA, WebView/native interaction QA, security QA, Windows/Linux build parity and full Java stub compilation.

Physical Gemini behavior remains a separate promotion gate. See `docs/NUBIA_QA_PLAN.md`.
