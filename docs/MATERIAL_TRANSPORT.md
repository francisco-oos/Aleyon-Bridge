# Material transport — control plane vs data plane

**Date:** 2026-09-18  
**Applies to:** `0.5.0-alpha3`

## Decision

When the user wants Gemini to study a PDF, document, spreadsheet, image, audio, video or similar item, Bridge/Artemis-derived transport does **not** need to ingest the bytes.

- Bridge supplies learner context, study objective and continuity.
- The adaptive transport keeps the current fresh Gemini session verified and may open Gemini's native attachment surface.
- Android's native picker/user selection supplies the file reference.
- Gemini reads/analyzes the actual content.
- Bridge stores only the learning result/evidence needed for future continuity.

The same separation applies to Live: Gemini owns microphone, camera and screen-sharing. Bridge does not request those permissions.

## Why

This avoids duplicating Gemini's multimodal stack and prevents a general automation component from receiving unnecessary access to microphones, storage or arbitrary files.

## Current official Gemini capability snapshot

Google's Android help page, checked 2026-09-18, documents native upload/analysis of documents, spreadsheets, notebooks, photos, video and other supported files. It documents up to 10 items in one prompt (subject to availability), a 100 MB limit for most non-video file types and up to 2 GB per video. These are provider limits and must be rechecked over time rather than treated as permanent product invariants.

Gemini Live's Android help page documents that the Gemini app itself owns voice, camera and screen-sharing behavior.

Official references:
- https://support.google.com/gemini/answer/14903178?co=GENIE.Platform%3DAndroid&hl=es-MX
- https://support.google.com/gemini/answer/15274899?co=GENIE.Platform%3DAndroid&hl=es

## Production boundary in alpha3

Implemented:
- metadata-only `SessionMaterial`;
- `MaterialHandoffPolicy` requiring user-selected `content://` URIs;
- no `file://` handoff;
- maximum 10-item batch;
- conservative size ceilings;
- semantic `openNativeAttachmentSurface()` behind the narrow Gemini adapter.

Not claimed yet:
- automatic selection inside Android's document picker;
- automatic Drive authorization;
- proof that every current Gemini build returns attachment state through the same accessibility tree;
- automated validation of Gemini's actual semantic answer to a real file.

Those require physical/provider QA. The deliberate rule is: **do not gain broad System UI/filesystem authority merely to remove one user-owned file-selection step.**
