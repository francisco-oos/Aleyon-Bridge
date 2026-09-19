# Physical QA plan — 0.5.0-alpha3 disposable Gemini sessions

## Goal

Validate the real product contract on physical phones:

```text
Bridge profile
  → fresh normal Gemini chat
  → local continuity capsule delivered once
  → Chat or Gemini Live
  → debrief in the same current chat
  → verified local commit
```

Provider chat history and Gemini-generated titles are disposable. No test may depend on a canonical provider conversation, provider search, rename or a recovery workflow.

## Before testing

Record:
- Bridge version;
- Gemini/Google app version;
- Android version;
- phone model;
- whether the Bridge Accessibility service is enabled.

Do **not** clear Bridge app data just to upgrade from alpha2. The alpha3 migration preserves profile/learning data and ignores obsolete provider-chat state.

## Required cases

### N1 — First Live session from Bridge

1. Open Bridge and choose an existing or new profile.
2. Tap **INICIAR LIVE**.
3. Bridge/Artemis must reach a fresh normal Gemini chat.
4. The continuity capsule must be inserted and **sent automatically exactly once**.
5. After the send is verified, Artemis must start Gemini Live.
6. The Aleyon bubble may remain available but must not steal focus or block Gemini.

Pass condition: no manual Send, no drawer/search/rename detour and Live becomes active.

### N2 — Repeated Live sessions

Run the same profile three times.

Each explicit start must use a fresh normal Gemini chat and the current local summary/next objective. It must not search for or reopen a previous Gemini conversation.

Pass condition: three successful sessions can create three disposable Gemini history entries without affecting Bridge continuity.

### N3 — Gemini already open on an unrelated chat

1. Leave Gemini open on any existing conversation.
2. Return to Bridge.
3. Start Live.

Artemis must create/verify a fresh normal chat before delivering learner context. It must never paste the capsule into the unrelated thread.

### N4 — Gemini already in Live

1. Manually start an unrelated Gemini Live session.
2. Start a Bridge session.

Artemis must observe the actual Live state, leave it safely, obtain a fresh normal chat, deliver context and start the requested Bridge Live session. There must be no blind repeated Back loop.

### N5 — Navigation drawer or conversation search already open

Start Bridge while Gemini is showing:
- the conversation drawer; and separately
- **Buscar chats**.

Artemis must treat these as semantic states, leave them and reach a fresh normal chat. The search EditText must never receive learner context.

### N6 — Temporary Chat open

Start Bridge while Gemini is in Temporary Chat.

Bridge must not inject the learning capsule there. Artemis must leave/replace it with a fresh normal chat or fail closed with clear diagnostic evidence.

### N7 — Context Send regression

This is the regression test for the user-reported alpha2 failure.

1. Start Live.
2. Watch the composer.
3. Verify the complete capsule appears.
4. Verify the Send action occurs automatically.
5. Verify the composer clears / conversation advances.
6. Verify Artemis does not reinsert the same capsule.
7. Verify Live begins without touching the phone.

Any manual Send required = FAIL.

### N8 — Live button variation

Test when Live is exposed through:
- the known Robin resource;
- a semantic accessibility label;
- structural composer evidence, if available on a second device/build.

Artemis must act from current semantic evidence rather than phone brand or coordinates.

### N9 — Close and learn

1. Have a short Live conversation.
2. End it normally or use **Cerrar sesión** from the Aleyon bubble.
3. Bridge must return to the same current provider chat.
4. Observe transcript until stable.
5. Send the four-line debrief request exactly once.
6. Parse the new debrief.
7. Persist summary, progress/reinforcement evidence and next objective locally.
8. Return Bridge to READY.

Open **Sesiones** / **Memoria** and verify the result is present.

### N10 — Process interruption, no recovery mode

1. Start a session.
2. Force-stop Bridge during preparation.
3. Reopen Bridge.
4. Start the same profile again.

Expected: there is no **Recuperar** action. The next explicit start discards the unfinished provider transaction and starts a clean Gemini chat from preserved local memory.

### N11 — Existing alpha2 profile upgrade

Upgrade over the currently installed build without deleting app data.

Verify:
- profile still exists;
- prior summary/evidence still exists;
- no old canonical-chat state is required;
- any legacy `RECOVERING` state is normalized to READY;
- first alpha3 start creates a fresh provider chat.

### N12 — Consent/security surface

If Gemini/Android shows a consent or security decision, Bridge must remove its overlay and wait. It must never select Accept/Cancel for the user.

### N13 — Native document handoff

During a verified current Chat session:
1. open Gemini's native attachment surface;
2. user selects a small PDF with Android/Gemini's picker;
3. ask Gemini to use it in the learning task;
4. close the session and verify only learning result/evidence is retained by Bridge.

Bridge itself must not request broad storage/microphone authority.

### N14 — Image handoff

Repeat N13 with an image and a visual-language task.

### N15 — Provider UI drift / relearning

After a Gemini update or on a second phone with a different Gemini build:
1. start the same profile;
2. observe whether the stored Artemis routine still matches;
3. if state/action evidence no longer matches, the routine must be invalidated;
4. Artemis must relearn using allowed semantic capabilities;
5. if the new UI is semantically opaque, Bridge must fail closed rather than guess.

A changed UI must not select a device-brand code path or absolute coordinates.

## Timing / anti-robot validation

During N1–N15, verify behavior from state transitions, not clock delays.

Allowed:
- accessibility-event debounce;
- short asynchronous re-observation when Android emits no useful event;
- overall anti-freeze watchdog;
- read-only diagnostic sampling.

Not allowed:
- “wait N seconds, then click Send”;
- “wait N seconds, then click Live”;
- coordinate taps;
- fixed screen-step sequences;
- synchronous sleep loops in provider transport.

## Multi-device gate

Run N1–N12 on the Nubia. Repeat at least N1, N3, N5, N7, N8, N9 and N15 on a second Android device/build.

Device brand must not select a code path. Compare semantic evidence and end-state verification, not pixel coordinates.

## Evidence on failure

Save:
- Bridge/Gemini/Android versions;
- local session id if available;
- stage;
- redacted passive probe;
- compatibility/routine evidence;
- visible error;
- screenshot/video when useful.

The passive probe is read-only. Its timed samples are diagnostic only and never drive a provider action.

## Promotion gate

Do not promote alpha3 to the stable baseline until:
- N1–N12 pass twice consecutively on Nubia;
- the multi-device subset passes on a second device;
- GitHub **Aleyon Bridge QA** is green;
- GitHub **Build Android APK** is green.


## 0.5.0-alpha5 — Live fuera del viewport y red lenta (2026-09-18)

- Dejar Gemini abierto en una conversación y desplazarla hasta que el control Live no esté visible. Desde Aleyon pulsar Iniciar Live sin tocar Gemini: Artemis debe explorar, reobservar y revelar/usar Live por sí mismo.
- Repetir con Live ya visible para comprobar que una rutina aprendida de scroll no obliga a desplazarse innecesariamente.
- Probar cierre desde Live, desde Chat y después de terminar Live manualmente. Ningún cierre puede crear otro chat ni buscar historial.
- Durante Esperando resumen, simular conexión lenta: no reenviar el prompt, mantener el mismo SESSION_ID local y aceptar una respuesta tardía si sigue habiendo progreso.
- Verificar el aviso Gemini tarda; sigo esperando… y que el fallback sólo ocurra tras un periodo prolongado sin progreso.
- Repetir en Nubia y Samsung antes de promoción.
