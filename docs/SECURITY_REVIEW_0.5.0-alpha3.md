# Security review — Aleyon Bridge 0.5.0-alpha3

**Date:** 2026-09-18  
**Branch:** `develop/artemis-transport`

## Scope

Reviewed risks include changed/malicious Gemini UI, over-broad Accessibility, stale learned routines, timer-driven automation, command injection, hostile study documents, remote WebView navigation, file-path abuse, local-memory backup leakage, automatic consent clicks and accidental learner-context injection into the wrong provider chat.

## Security and authority boundary

Bridge retains these invariants:

- no `INTERNET`, `RECORD_AUDIO`, camera, storage or package-install permission;
- Android backup disabled for learner memory;
- Accessibility allow-listed to official Gemini host packages;
- `canPerformGestures=false`;
- no `dispatchGesture`, coordinate taps or brand-specific pixel paths;
- no `Runtime.exec`, `ProcessBuilder`, unrestricted ADB/shell or arbitrary package control in the APK;
- local WebView only; no remote navigation/debugging;
- consent/security dialogs are never auto-accepted;
- learner memory stays local and is never stored in Artemis routine memory.

## Disposable provider sessions

Alpha3 deliberately removes persistent provider-chat identity.

There is no:
- canonical Gemini conversation;
- deterministic provider title;
- conversation registry;
- provider search/reuse/rebuild;
- automated rename;
- recovery planner or **Recuperar** command.

Every explicit start must prove a fresh normal Gemini chat before learner context is delivered. This reduces the chance of pasting private learner context into an unrelated existing thread.

## Artemis routine safety

The embedded Artemis-derived memory stores only:

`phase + semantic transport state + action`

A stored routine is replayed only while those observations still match. A mismatch invalidates the routine and forces re-observation/relearning.

The task model is narrow:
- `START_SESSION`: fresh chat → context → Chat/Live;
- `CLOSE_SESSION`: end Live if needed → debrief in the same current chat.

No learner profile, summary, evidence or next objective is part of routine memory.

## Timing safety

Provider actions are not authorized by elapsed time.

The transport uses:
- Accessibility events where available;
- non-blocking re-observation as fallback;
- bounded anti-freeze runtime watchdogs.

`ArtemisRootResolver` is single-pass and contains no `SystemClock.sleep` or internal retry backoff. Read-only passive diagnostic sampling may use timed captures because it does not execute provider actions.

## Study materials

User-selected study files stay in Android/Gemini's native data plane. Bridge may open Gemini's verified attachment surface but does not automate arbitrary filesystem access.

A hostile document may influence Gemini's study answer, but its content must never become a transport/device command.

## Remaining provider risk

A future Gemini build may stop exposing sufficient accessibility semantics. In that case Bridge must fail closed with diagnostic evidence rather than guess coordinates or click generic controls.

Physical multi-device QA remains required because static/CI tests cannot prove the behavior of the current official Gemini Android UI.
