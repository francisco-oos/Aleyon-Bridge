@echo off
setlocal
cd /d "%~dp0\.."
set OUT=.test-out
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
javac -d "%OUT%" ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionStage.java ^
 app\src\main\java\com\aleyon\geminibridge\core\ProtocolContract.java ^
 app\src\main\java\com\aleyon\geminibridge\core\RecoveryPlanner.java ^
 app\src\main\java\com\aleyon\geminibridge\core\ProfileNaming.java ^
 app\src\main\java\com\aleyon\geminibridge\core\LearningEvent.java ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionReportParser.java ^
 app\src\main\java\com\aleyon\geminibridge\core\SessionTextDelta.java ^
 app\src\main\java\com\aleyon\geminibridge\core\AutomationDiagnostics.java ^
 tests\java\com\aleyon\geminibridge\core\CoreTests.java
if errorlevel 1 exit /b 1
java -cp "%OUT%" com.aleyon.geminibridge.core.CoreTests
if errorlevel 1 exit /b 1
python tests\qa_static.py
if errorlevel 1 exit /b 1
python tests\compile_all_java_with_stubs.py
if errorlevel 1 exit /b 1
