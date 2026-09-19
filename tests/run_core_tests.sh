#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.test-out"

python3 "$ROOT/tests/verify_package.py"
rm -rf "$OUT"; mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionStage.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/ProtocolContract.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/ProfileNaming.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/TransportState.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/ScreenBoundsPolicy.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/LearningEvent.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/LearningLedger.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/PedagogicalState.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/PedagogicalStateBuilder.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionTextDelta.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/AutomationDiagnostics.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/SessionMaterial.java" \
  "$ROOT/app/src/main/java/com/aleyon/geminibridge/core/MaterialHandoffPolicy.java" \
  "$ROOT/tests/java/com/aleyon/geminibridge/core/CoreTests.java"

java -cp "$OUT" com.aleyon.geminibridge.core.CoreTests
python3 "$ROOT/tests/simulate_matrix.py"
python3 "$ROOT/tests/qa_static.py"
python3 "$ROOT/tests/qa_interactions.py"
python3 "$ROOT/tests/qa_security.py"
python3 "$ROOT/tests/qa_build_parity.py"
python3 "$ROOT/tests/compile_all_java_with_stubs.py"
