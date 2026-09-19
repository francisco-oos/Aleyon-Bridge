# Handoff — Aleyon Bridge 0.5.0-alpha2

## User contract

Do not complicate the daily path: **open Bridge → choose language/profile → Chat or Iniciar Live**. Context, progress, canonical-chat reuse and reconstruction happen underneath.

## Architecture in one line

**Bridge remembers. Artemis-derived transport carries, brings back and adapts. Gemini thinks and owns native Live/media.**

## Must not regress

1. `LearningLedger` is authoritative learner memory.
2. One canonical Gemini conversation is reused per language/profile.
3. Deleted provider chat reconstructs from local state.
4. Provider selectors stay behind `GeminiUi` / `GeminiConversationTransport`.
5. No device-brand branch, absolute coordinate flow or accessibility gesture injection.
6. No arbitrary shell/ADB/package authority in the APK.
7. Unknown UI fails closed with evidence.
8. Session close must commit/read-back before cleanup.
9. `Avance` and `A reforzar` enrich local evidence after every verified close.
10. Study material bytes stay in Android/Gemini's native data plane; Bridge stores metadata/learning results only.

## Build issue fixed in alpha2

The old Windows build script and GitHub workflows were still pinned to `0.4.0-alpha8`/`develop/bridge-clean`, while the source was already 0.5. The Windows core runner also omitted new core classes. Alpha2 makes VERSION authoritative, aligns BAT/SH/CI source lists, and adds `qa_build_parity.py` so this drift fails automatically.

## Review first

- `docs/ARCHITECTURE.md`
- `docs/MATERIAL_TRANSPORT.md`
- `docs/SECURITY_REVIEW_0.5.0-alpha2.md`
- `docs/NUBIA_QA_PLAN.md`
- `app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java`
- `app/src/main/java/com/aleyon/geminibridge/transport/`
- `tests/run_core_tests.sh`

## Automated gate

Completed and green: package/version consistency, Java core policies, 60-profile/360-session simulation, static architecture QA, JS/native contract QA, security QA, Windows/Linux/CI build parity, full Java stub compilation, and real Android `clean assembleDebug` on GitHub Actions with SDK 35 / Gradle 8.9 / Java 17. The final branch commit `972205076a00cba1b244bc32eef258998a2b72b1` also passed the independent QA and Build APK workflows.

## Physical gate

Automated simulation does **not** claim that Gemini actually analyzed a real PDF/image or that current provider labels are unchanged. Run `docs/NUBIA_QA_PLAN.md`, including canonical deletion/rebuild, different initial Gemini states, native attachment surface and at least one real document/image session.
