# QA report — 0.4.0-alpha8

## Field evidence incorporated

- Nubia alpha7 passive probe: verified Google Gemini host, Robin signature, CHAT surface, composer detected, historic Live detector false.
- Samsung alpha7 passive probe/video: same core mismatch on a different UI layout/device.
- Both expose the shared `assistant_chat_add_reply_*` / convergence input structure, motivating capability-based rather than device-name selectors.

## Changes under test

- structural Live fallback for empty composer + dual right actions;
- robust send action inside the same input container after text insertion;
- explicit fresh Aleyon session start and safe cancel/close;
- bounded profile fields in continuity capsule;
- passive probe v8 with detection evidence;
- chat-head overlay ergonomics and human-readable status;
- compact diagnostic UI.

## Promotion gate

Local preflight/QA and Android compilation are necessary but not sufficient. Physical Nubia and Samsung tests must each complete start → context send → Live → close → local commit before alpha8 can replace the development baseline.
