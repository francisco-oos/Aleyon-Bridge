# Handoff — Aleyon Bridge 0.5.0-alpha3

## User contract

Daily path stays deliberately small:

```text
open Bridge → choose profile → Chat or Iniciar Live
```

Bridge owns learner continuity. Gemini is a disposable cognitive/Live surface. Artemis owns only Android transport and verification.

## Runtime contract

### START_SESSION
1. Observe Gemini.
2. Reach or create a fresh normal chat.
3. Deliver the bounded local continuity capsule.
4. Verify the message was submitted.
5. Leave Chat ready or start/verify Gemini Live.

### CLOSE_SESSION
1. End Live if active.
2. Observe the same current chat until transcript state is stable.
3. Request the four-line debrief.
4. Parse only the new debrief.
5. Commit summary/evidence/next objective locally and read back the commit.

## Removed architecture

Do not reintroduce:

- canonical Gemini conversations;
- deterministic provider chat titles;
- provider conversation registry;
- search/reuse/rebuild/rename flows;
- `SessionIntent` fields tied to provider chat identity;
- `RecoveryPlanner`, `RECOVERING`, recovery command or **Recuperar** button;
- blind fixed-step or coordinate automation.

If an unfinished run dies, the next explicit start creates another fresh provider chat from local Aleyon state.

## Artemis invariant

Learned routines are phase-aware and contain only transport state/action evidence. They must never contain learner content.

A provider UI mismatch invalidates the learned routine. The runtime then re-observes and relearns from allow-listed semantic capabilities rather than replaying stale taps.

## Timing invariant

Session control is event/state driven. Scheduled re-observation is only watchdog/backoff; no user action or provider transition is considered complete because a timer elapsed.

## Promotion gate

Automated CI must pass both:
- **Aleyon Bridge QA**
- **Build Android APK**

Physical promotion still requires Nubia/Samsung phone testing because CI cannot prove the current Gemini Android UI.
