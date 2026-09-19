# Security review — Aleyon Bridge 0.5.0-alpha2

**Date:** 2026-09-18  
**Branch:** `develop/artemis-transport`

## Threat model

Reviewed risks include: changed/malicious Gemini UI, over-broad Accessibility, command injection, hostile study documents, remote WebView navigation, file-path abuse, local-memory backup leakage, stale build scripts, automatic consent clicks and accidental context injection into the wrong provider conversation.

## Controls present

- no `INTERNET`, `RECORD_AUDIO`, `CAMERA`, storage, package-install or broad overlay permission;
- `android:allowBackup="false"` for learner memory;
- Accessibility limited to the two known official Gemini host packages;
- `canPerformGestures=false` and no `dispatchGesture` production path;
- no `Runtime.exec`, `ProcessBuilder`, shell, unrestricted ADB or arbitrary app/package tool in the APK;
- local WebView only; file/content/universal file access disabled, mixed content blocked, debugging disabled and non-asset navigation rejected;
- provider selectors isolated behind `GeminiUi`; the service requests semantic operations;
- Live close is scoped to an already-verified Live surface;
- attachment action is scoped to the verified composer container;
- canonical title/profile id inputs are normalized/sanitized;
- user-selected study material must use `content://`, never raw filesystem paths;
- unknown Gemini states fail closed and preserve local learner state;
- human consent dialogs pause automation.

## Artemis-specific upstream findings

As of 2026-09-18, Google Artemis still has open security reports relevant to a **general** runtime:

- issue #55: shell command injection through unsanitized package/url values in ADB/MCP paths;
- issue #99: shell command injection in optional ScriptNotifier command-template substitution.

Bridge does not import those capabilities. Host-side Artemis QA must remain isolated and should not expose arbitrary shell/package/notifier actions to untrusted content. Keep its daemon local/tunneled and avoid ScriptNotifier shell hooks until upstream fixes are verified.

References:
- https://github.com/google/artemis/issues/55
- https://github.com/google/artemis/issues/99

## Untrusted document rule

A PDF/image/web-derived study item may contain prompt-injection text. That content is allowed to influence Gemini's *study answer* but must never be interpreted as a transport/device command. Artemis/Bridge therefore does not ingest material bytes into a command-capable runtime; Gemini receives them through its native data plane.

## Remaining risk / physical gate

Static QA cannot prove that a future Gemini build exposes the same semantic controls. Conversation search, rename, attachment UI and Live end controls still require physical QA. Failure must remain closed (`APP_UPDATE_REQUIRED`) rather than fall back to coordinates or generic buttons.
