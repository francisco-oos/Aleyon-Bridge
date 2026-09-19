@echo off
setlocal
cd /d "%~dp0\.."
python tests\verify_package.py
if errorlevel 1 exit /b 1
set OUT=.test-out
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
javac -d "%OUT%" ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionStage.java ^
 app\src\main\java\com\aleyon\geminibridge\core\ProtocolContract.java ^
 app\src\main\java\com\aleyon\geminibridge\core\RecoveryPlanner.java ^
 app\src\main\java\com\aleyon\geminibridge\core\ProfileNaming.java ^
 app\src\main\java\com\aleyon\geminibridge\core\CanonicalConversationPolicy.java ^
 app\src\main\java\com\aleyon\geminibridge\core\CanonicalChatRoutingPolicy.java ^
 app\src\main\java\com\aleyon\geminibridge\core\AdaptiveNavigationPlanner.java ^
 app\src\main\java\com\aleyon\geminibridge\core\TransportState.java ^
 app\src\main\java\com\aleyon\geminibridge\core\ScreenBoundsPolicy.java ^
 app\src\main\java\com\aleyon\geminibridge\core\LearningEvent.java ^
 app\src\main\java\com\aleyon\geminibridge\core\LearningLedger.java ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionReportParser.java ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionTextDelta.java ^
 app\src\main\java\com\aleyon\geminibridge\core\AutomationDiagnostics.java ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionMaterial.java ^
 app\src\main\java\com\aleyon\geminibridge\core\MaterialHandoffPolicy.java ^
 tests\java\com\aleyon\geminibridge\core\CoreTests.java
if errorlevel 1 exit /b 1
java -cp "%OUT%" com.aleyon.geminibridge.core.CoreTests
if errorlevel 1 exit /b 1
python tests\simulate_matrix.py
if errorlevel 1 exit /b 1
python tests\qa_static.py
if errorlevel 1 exit /b 1
python tests\qa_interactions.py
if errorlevel 1 exit /b 1
python tests\qa_security.py
if errorlevel 1 exit /b 1
python tests\qa_build_parity.py
if errorlevel 1 exit /b 1
python tests\compile_all_java_with_stubs.py
if errorlevel 1 exit /b 1
