# QA report — Aleyon Bridge 0.5.0-alpha2

**Branch:** `develop/artemis-transport`  
**Date:** 2026-09-18  
**Baseline:** `0.5.0-alpha1`

## Automated gate executed locally

- package/source preflight: **PASS**
- Java core tests: **PASS — 15**
- exhaustive contract simulation: **PASS — 60 profiles / 360 sessions**
- sessions with study-material metadata: **324**
- forced canonical-chat builds/rebuilds: **120**
- fail-closed unknown-UI probes: **60**
- simulated Gemini UI/version families: **10**
- static architecture QA: **PASS**
- JS ↔ native interaction contract: **PASS — 19 JS calls / 35 native handlers**
- security QA: **PASS**
- Windows/Linux/CI build-runner parity: **PASS — 15 core source files / 5 shared QA stages**
- full production Java compile against Android stubs: **PASS — 34 production Java files**
- source package final preflight: **PASS**

## What the matrix proves

The deterministic matrix verifies Bridge-owned invariants without pretending to be a live provider test:

- 60 isolated language profiles with different purposes;
- canonical title/profile identity uniqueness;
- Chat/Live session alternation;
- provider conversation reuse;
- provider conversation deletion followed by reconstruction from local state;
- summary/next-objective persistence across provider deletion;
- two learning-evidence events promoted per verified close (`Avance`, `A reforzar`);
- metadata policy for PDF/image/DOCX/XLSX/PPTX/text/CSV/audio/video scenarios;
- 10 initial/provider UI families including already-open Live, wrong chat, temporary chat, consent, renamed semantic controls and structure-only Live;
- unknown/unresolved UI leaves learner/provider model state unchanged (fail closed).

## Security changes verified

- no Internet/microphone/camera/storage/package-install/broad-overlay authority;
- Android backup disabled for local learner state;
- local-only WebView with external navigation rejected;
- no shell/ProcessBuilder/ADB-command surface in production;
- no coordinate gesture path;
- Accessibility package allow-list remains Gemini-only;
- material handoff requires user-selected `content://`, not filesystem paths;
- Live termination and native attachment entry are contained behind the narrow semantic Gemini adapter;
- no device-brand runtime branch.

## Build regression fixed

The 0.5 source and old build surfaces had drifted: Windows/CI still referenced `0.4.0-alpha8` / `develop/bridge-clean`, and the BAT core compiler omitted newer classes. Alpha2 derives the APK version/name from `VERSION`, aligns Windows/Linux test sources and adds a parity test so this class of regression fails before Gradle.

## What is not proven by simulation

The local environment has no Android SDK/Gradle network toolchain, so the real Android `assembleDebug` is delegated to GitHub Actions after branch upload. Physical provider behavior also remains a separate gate: current Gemini drawer/search/rename/attachment/Live controls, actual PDF/image analysis quality, account-specific limits and OEM accessibility behavior require device tests.

See `docs/NUBIA_QA_PLAN.md` for the physical gate.
