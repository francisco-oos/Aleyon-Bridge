#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT="$ROOT/.test-out"; rm -rf "$OUT"; mkdir -p "$OUT"
javac -d "$OUT" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionStage.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/ProtocolContract.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/RecoveryPlanner.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/ProfileNaming.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/LearningEvent.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionTextDelta.java" \
 "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/AutomationDiagnostics.java" \
 "$ROOT/tests/java/com/aleyon/geminibridge/core/CoreTests.java"
java -cp "$OUT" com.aleyon.geminibridge.core.CoreTests
python3 "$ROOT/tests/qa_static.py"
python3 "$ROOT/tests/compile_all_java_with_stubs.py"
