$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$GradleVersion = "8.9"
$CompileSdk = "35"
$BuildTools = "35.0.0"

function Title($t) {
  Write-Host ""
  Write-Host "============================================================" -ForegroundColor DarkBlue
  Write-Host " $t" -ForegroundColor Cyan
  Write-Host "============================================================" -ForegroundColor DarkBlue
}
function Fail($m) {
  Write-Host ""
  Write-Host "ERROR: $m" -ForegroundColor Red
  Write-Host ""
  exit 1
}

$versionFile=Join-Path $ProjectRoot "VERSION"
if(-not(Test-Path $versionFile)){Fail "Paquete incompleto: falta VERSION en $ProjectRoot"}
$packageVersion=(Get-Content $versionFile -Raw).Trim()
if([string]::IsNullOrWhiteSpace($packageVersion)){Fail "VERSION esta vacio."}
$OutputName = "AleyonBridge-v$packageVersion-debug.apk"

Title "ALEYON BRIDGE $packageVersion - COMPILACION APK"
Write-Host "PROJECT_ROOT: $ProjectRoot" -ForegroundColor Yellow
Write-Host "PACKAGE_VERSION: $packageVersion" -ForegroundColor Yellow

$required=@("app\build.gradle","app\src\main\AndroidManifest.xml","tests\verify_package.py","MANIFEST_SHA256.txt")
foreach($rel in $required){if(-not(Test-Path (Join-Path $ProjectRoot $rel))){Fail "Paquete incompleto: falta $rel"}}

$appGradleText=(Get-Content (Join-Path $ProjectRoot "app\build.gradle") -Raw)
if($appGradleText -notmatch ('versionName\s+"'+[regex]::Escape($packageVersion)+'"')){
  Fail "La version de app/build.gradle no coincide con VERSION ($packageVersion)."
}

Title "Preflight del paquete"
$pythonCmd=Get-Command python.exe -ErrorAction SilentlyContinue
if(-not $pythonCmd){$pythonCmd=Get-Command python -ErrorAction SilentlyContinue}
if(-not $pythonCmd){Fail "No encontre Python para verificar la integridad del paquete."}
& $pythonCmd.Source (Join-Path $ProjectRoot "tests\verify_package.py")
if($LASTEXITCODE -ne 0){Fail "El paquete no paso la verificacion estructural; no se ejecutara Gradle."}

# Java / Android Studio JBR
$javaHomeCandidates = @()
if ($env:JAVA_HOME) { $javaHomeCandidates += $env:JAVA_HOME }
if ($env:ProgramFiles) { $javaHomeCandidates += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr") }
$javaHome = $null
foreach ($c in $javaHomeCandidates) {
  if ($c -and (Test-Path (Join-Path $c "bin\java.exe"))) { $javaHome = $c; break }
}
if (-not $javaHome) {
  $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
  if ($javaCmd) {
    $javaBin = Split-Path $javaCmd.Source -Parent
    $javaHome = Split-Path $javaBin -Parent
  }
}
if (-not $javaHome) { Fail "No encontre Java/JBR. Instala Android Studio o JDK 17+." }
$env:JAVA_HOME=$javaHome
$env:Path="$(Join-Path $javaHome 'bin');$env:Path"
Write-Host "JAVA_HOME: $javaHome" -ForegroundColor Green

$javaExe=Join-Path $javaHome "bin\java.exe"
$psi=New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName=$javaExe
$psi.Arguments="-version"
$psi.RedirectStandardOutput=$true
$psi.RedirectStandardError=$true
$psi.UseShellExecute=$false
$psi.CreateNoWindow=$true
$proc=New-Object System.Diagnostics.Process
$proc.StartInfo=$psi
[void]$proc.Start()
$stdout=$proc.StandardOutput.ReadToEnd(); $stderr=$proc.StandardError.ReadToEnd(); $proc.WaitForExit()
$javaVersionText=(($stdout+"`n"+$stderr).Trim())
Write-Host $javaVersionText
if ($proc.ExitCode -ne 0) { Fail "Java no pudo ejecutarse." }
if($javaVersionText -notmatch 'version\s+"(\d+)') { Fail "No pude determinar la version de Java." }
if([int]$Matches[1] -lt 17) { Fail "Se requiere JDK 17 o superior; se detecto Java $($Matches[1])." }

# SDK
$sdkCandidates=@()
if ($env:ANDROID_SDK_ROOT) { $sdkCandidates += $env:ANDROID_SDK_ROOT }
if ($env:ANDROID_HOME) { $sdkCandidates += $env:ANDROID_HOME }
if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk") }
$sdk=$null
foreach($c in $sdkCandidates){if($c -and (Test-Path $c)){$sdk=$c;break}}
if(-not $sdk){Fail "No encontre Android SDK."}
$env:ANDROID_HOME=$sdk; $env:ANDROID_SDK_ROOT=$sdk
Write-Host "ANDROID_SDK_ROOT: $sdk" -ForegroundColor Green

function Find-SdkManager($sdkRoot) {
  $p=Join-Path $sdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
  if(Test-Path $p){return $p}
  $f=Get-ChildItem -Path (Join-Path $sdkRoot "cmdline-tools") -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
  if($f){return $f.FullName}
  return $null
}
$sdkManager=Find-SdkManager $sdk
$platformPath=Join-Path $sdk "platforms\android-$CompileSdk"
$buildToolsPath=Join-Path $sdk "build-tools\$BuildTools"
if((-not(Test-Path $platformPath))-or(-not(Test-Path $buildToolsPath))){
  if(-not $sdkManager){Fail "Falta API $CompileSdk/Build Tools y sdkmanager."}
  Title "Instalando componentes Android que falten"
  @("y","y","y","y","y","y","y","y","y","y") | & $sdkManager "platforms;android-$CompileSdk" "build-tools;$BuildTools" "platform-tools"
  if($LASTEXITCODE -ne 0){Fail "sdkmanager termino con error."}
}

# Shared Gradle cache across clean candidates.
if($env:LOCALAPPDATA){$cache=Join-Path $env:LOCALAPPDATA "AleyonBridge\build-cache"}
else{$cache=Join-Path $ProjectRoot ".build-cache"}
Write-Host "GRADLE_CACHE: $cache" -ForegroundColor Green
$gradleHome=Join-Path $cache "gradle-$GradleVersion"
$gradleExe=Join-Path $gradleHome "bin\gradle.bat"
if(-not(Test-Path $gradleExe)){
  Title "Descargando Gradle $GradleVersion"
  New-Item -ItemType Directory -Force -Path $cache | Out-Null
  $zip=Join-Path $cache "gradle-$GradleVersion-bin.zip"
  if(-not(Test-Path $zip)){Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $zip -UseBasicParsing}
  Expand-Archive -Path $zip -DestinationPath $cache -Force
}
if(-not(Test-Path $gradleExe)){Fail "No pude preparar Gradle $GradleVersion."}

$sdkProps=$sdk.Replace("\","/")
Set-Content -Path (Join-Path $ProjectRoot "local.properties") -Value "sdk.dir=$sdkProps" -Encoding ASCII

Title "Ejecutando QA local"
$testBat=Join-Path $ProjectRoot "tests\run_core_tests.bat"
if(-not(Test-Path $testBat)){Fail "Falta tests\run_core_tests.bat"}
& $testBat
if($LASTEXITCODE -ne 0){Fail "Los tests locales fallaron. No se compilara el APK."}

Title "Compilando APK debug"
Push-Location $ProjectRoot
try {
  & $gradleExe --no-daemon --stacktrace clean assembleDebug
  if($LASTEXITCODE -ne 0){Fail "Gradle termino con error."}
} finally { Pop-Location }

$built=Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
if(-not(Test-Path $built)){Fail "No encontre app-debug.apk."}
$dest=Join-Path $ProjectRoot $OutputName
Copy-Item $built $dest -Force
$hash=Get-FileHash -Algorithm SHA256 $dest
$size=[math]::Round((Get-Item $dest).Length/1MB,2)

Title "APK GENERADA"
Write-Host "Archivo: $dest" -ForegroundColor Green
Write-Host "Tamano : $size MB"
Write-Host "SHA256 : $($hash.Hash)"
Write-Host ""
Write-Host "Siguiente: instala en telefono de prueba y habilita una sola vez:"
Write-Host "Ajustes > Accesibilidad > Aleyon Bridge - Automatizacion Gemini"
