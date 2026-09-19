# Third-party notices

## Google Artemis

Aleyon Bridge includes modified source derived from Google Artemis:
- `packages/artemis-accessibility-helper/.../HierarchyDumper.java` → `ArtemisRootResolver.java`
- the reactive execution shape of `artemis/agents/flash/runner.py` → `ArtemisFlashAgent.java`
- bounded persistent step-memory concepts from `artemis/memory/step_memory.py` → `ArtemisRoutineMemory.java`

Copyright 2026 Google LLC.

Google Artemis is licensed under the Apache License, Version 2.0. The modified files in Aleyon Bridge carry source notices describing the changes. No trademark rights are granted.

The Bridge APK intentionally does **not** include Artemis CommandServer, GestureController, unrestricted ADB/shell execution, package installation, screenshot capture, token receiver, or public/loopback RPC services.
