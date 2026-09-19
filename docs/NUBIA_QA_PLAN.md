# Physical QA plan — 0.5.0-alpha2 adaptive transport

## Goal

Validate that the user still gets the simple profile → Chat/Live experience while the transport survives different initial Gemini states and reuses/reconstructs one canonical conversation per profile.

## Required cases

### N1 — First Live session / canonical creation
1. Create/save a local language profile.
2. Tap `INICIAR LIVE`.
3. For a migrated/existing local profile with no canonical registry entry, Bridge must **not search**. It should observe Gemini, open the conversation list semantically, create a normal chat directly, inject a reconstruction capsule, rename it to `ALEYON — <idioma>`, then enter Gemini Live.
4. Context must never be injected into Temporary chat.
5. The Aleyon bubble appears without covering or stealing focus from Gemini.

### N2 — Canonical reuse
Start the same profile again twice: once as Chat and once as Live. Bridge must reopen the same canonical Gemini conversation instead of creating additional provider chats. Each session still receives a new local `SESSION_ID` and updated bounded context.

### N3 — Gemini already open on another chat
Before starting Bridge, leave Gemini open on an unrelated normal conversation. Start the profile. Bridge must ignore that initial chat, resolve the canonical conversation and continue without injecting learner context into the unrelated thread.

### N4 — Gemini Live already open
Before starting Bridge, manually enter an unrelated Gemini Live session. Start Bridge. The transport must treat Live as an initial state, return to chat chrome, resolve the canonical conversation, then start the requested session normally. No runaway BACK loop or context injection into the unrelated Live session is allowed.

### N5 — Canonical conversation deleted
1. Complete at least one session and confirm local memory has a summary/next objective.
2. Manually delete the `ALEYON — <idioma>` conversation in Gemini.
3. Start the profile again.
4. Because this chat was previously verified, Bridge may perform one bounded semantic search. If no actionable result appears, it must mark the provider cache missing, return to the list, create a new normal chat, send a reconstruction capsule derived from local memory, rename it to the same canonical title and continue.
5. Previously stored local learner progress must remain intact.

### N5b — Search surface isolation / real probe regression

1. Manually open Gemini's **Buscar chats** surface before starting Bridge.
2. Start an existing profile.
3. Bridge must classify this as `CONVERSATION_SEARCH`, never as `CHAT`.
4. The search `EditText` must never receive the learning context capsule.
5. Text typed as a search query must never count as a conversation result.
6. Bridge must back out/normalize safely. If it cannot progress within the bounded recovery budget, it must return to Bridge with a recoverable compatibility error instead of remaining frozen on `Preparando…`.

### N6 — Long conversation / search fallback
Make the canonical chat old enough that it is not in the immediately visible drawer list if possible. Bridge should use semantic conversation search before deciding to rebuild. If search cannot expose the conversation, the system must fail closed/rebuild rather than click an arbitrary row.

### N7 — Close and learn
Close from the bubble or from Gemini. Bridge must return to the canonical thread, wait for transcript settling, compute/retain the current-session delta locally, request only the short human-readable debrief, verify local commit and post a summary notification.

### N8 — Restart/recovery
Force-stop Aleyon during an active session, reopen it and recover without losing the local `LearningLedger` or corrupting the canonical-conversation registry.

### N9 — Consent
If Gemini shows a consent/extension decision, Bridge must pause for the user. It must never choose Accept/Cancel automatically.

### N10 — Google host path
If Gemini is hosted by `com.google.android.googlequicksearchbox`, actions are allowed only after Gemini/Robin verification. Record a passive probe if this path fails.

## Multi-device parity

Repeat N1–N7 on at least one second Android device or emulator with a different resolution/build. Device brand must not select a code path. Compare semantic evidence, not coordinates.

## Evidence

For each failure save: Bridge version, Gemini version, Android version, profile id, local `SESSION_ID`, stage, redacted passive probe, compatibility route/failure evidence, visible error and screenshot/video when useful.

## Promotion gate

N1–N7 must pass twice consecutively on the Nubia before replacing the 0.4 baseline. N1, N2, N3, N5 and N7 must also pass on a second device.


### N11 — Native document handoff
1. Reuse an existing canonical profile/chat.
2. Open Gemini's native attachment surface from the verified composer.
3. Select a small PDF with Android/Gemini's native picker.
4. Ask Gemini to study it and ask questions in the profile language.
5. Confirm the file stays in the canonical chat and that Bridge itself never requests storage/microphone authority.
6. Close the session and confirm summary, next objective, progress evidence and reinforcement evidence are persisted locally.

### N12 — Image handoff
Repeat N11 with a photo/image and a visual-language task. Confirm Gemini performs the visual analysis and Bridge only persists the resulting learning evidence.

### N13 — Provider UI version drift
After a Gemini app update (or on a second device with a visibly different Gemini UI), repeat N1, N2, N3, N5, N7, N11. The build/device must not select a brand-specific code path. If a control cannot be resolved semantically, Bridge must stop with `APP_UPDATE_REQUIRED` rather than guess.

### N14 — Persistence across process/device-app restart
Complete a session, force-stop Bridge, reopen it, then restart Gemini and start the same profile. Verify local summary/next objective/evidence and canonical-chat reconstruction state persist. Repeat after manually deleting the Gemini chat.
