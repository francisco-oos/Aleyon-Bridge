# ADR-0002 — Adaptive session transport with disposable Gemini chats

**Status:** accepted  
**Date:** 2026-09-18  
**Applies to:** `0.5.0-alpha3`

## Context

The 0.4/early 0.5 line proved that Aleyon Bridge can own learner profile, progress and continuity while the official Gemini Android app provides reasoning, Live and multimodal interaction.

Physical phone testing then exposed a design error: Bridge was spending most of its automation effort maintaining a provider-side "canonical" Gemini conversation — opening the drawer, finding/searching a chat, rebuilding it, renaming it and recovering it after interruption. That created long routines and device-specific failure modes for work that does not belong to the product.

Aleyon already owns continuity locally. Gemini therefore does not need to be a persistent learner database.

## Decision

Every explicit **Chat** or **Iniciar Live** action starts a fresh normal Gemini chat.

The product flow is:

```text
profile + local LearningLedger
        ↓
bounded continuity capsule
        ↓
fresh normal Gemini chat
        ↓
Chat or Gemini Live
        ↓
short debrief in the same current chat
        ↓
verified local commit
```

Gemini-generated chat titles and provider history are ignored by the runtime.

There is no provider conversation registry, deterministic provider title, conversation search/reuse/rebuild, automated rename, recovery planner or user-facing **Recuperar** mode.

## Artemis boundary

The embedded Artemis-derived layer owns Android transport, not learner memory.

It operates two product tasks:

### START_SESSION

Phases: `fresh-chat` → `context` → `live`.

For each phase Artemis performs **observe → choose one allow-listed action → act → observe again**. A phase-aware routine can be replayed while the observed state still matches. On mismatch, the routine is invalidated and relearned from current semantic evidence.

### CLOSE_SESSION

Phases: `end-live` → `debrief`.

The task ends Live when necessary, returns to the same current chat, observes transcript stability, sends the debrief request and lets Aleyon commit the parsed result locally.

## Timing rule

Product actions never occur merely because a fixed delay expired.

Accessibility events drive progress. Short scheduled re-observation remains only as a watchdog/backoff when Android or Gemini does not emit a usable event. Diagnostic sampling may use explicit sample timing because it is read-only and is not part of session control.

## Failure rule

Unknown or semantically opaque UI fails closed. Bridge never guesses coordinates, injects gestures or broadens authority to arbitrary apps.

If a start attempt is interrupted, the next explicit start discards unfinished runtime state and begins another clean Gemini chat from Aleyon's local continuity. There is nothing for the learner to "recover" inside Gemini.

## Memory ownership

Artemis routine memory stores only semantic transport state/action sequences keyed to provider version context.

It never stores learner progress, decides what to remember or mutates `LearningLedger`.

**Artemis transports. Gemini thinks. Bridge remembers.**
