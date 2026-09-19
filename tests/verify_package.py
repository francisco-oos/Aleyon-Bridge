#!/usr/bin/env python3
from pathlib import Path
import argparse, hashlib, json, re, sys

ROOT = Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument("--source-package", action="store_true", help="also reject generated build/cache files")
args=parser.parse_args()
errors=[]

def fail(msg): errors.append(msg)
def read(rel): return (ROOT/rel).read_text(encoding='utf-8')

def canonical_bytes(path):
    """Stable content bytes across Git LF and Windows CRLF checkouts."""
    raw=path.read_bytes()
    try:
        text=raw.decode('utf-8')
    except UnicodeDecodeError:
        return raw
    return text.replace('\r\n','\n').replace('\r','\n').encode('utf-8')

version_path=ROOT/'VERSION'
if not version_path.is_file():
    fail('missing VERSION'); VERSION=''
else:
    VERSION=version_path.read_text(encoding='utf-8').strip()
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9._-]+)?',VERSION): fail(f'invalid VERSION format: {VERSION!r}')

required=[
 'VERSION','SOURCE_OF_TRUTH.json','settings.gradle','build.gradle','app/build.gradle',
 'app/src/main/AndroidManifest.xml','app/src/main/res/xml/accessibility_service_config.xml',
 'app/src/main/assets/index.html','app/src/main/assets/aleyon_logo.png',
 'app/src/main/java/com/aleyon/geminibridge/MainActivity.java',
 'app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java',
 'app/src/main/java/com/aleyon/geminibridge/automation/GeminiUi.java',
 'app/src/main/java/com/aleyon/geminibridge/automation/OverlayController.java',
 'app/src/main/java/com/aleyon/geminibridge/automation/LearningStore.java',
 'app/src/main/java/com/aleyon/geminibridge/core/ContextCapsuleBuilder.java',
 'app/src/main/java/com/aleyon/geminibridge/core/LearningLedger.java',
 'app/src/main/java/com/aleyon/geminibridge/core/TransportState.java',
 'app/src/main/java/com/aleyon/geminibridge/artemis/ArtemisFlashAgent.java',
 'app/src/main/java/com/aleyon/geminibridge/artemis/ArtemisRoutineMemory.java',
 'app/src/main/java/com/aleyon/geminibridge/core/SessionMaterial.java',
 'app/src/main/java/com/aleyon/geminibridge/core/MaterialHandoffPolicy.java',
 'app/src/main/java/com/aleyon/geminibridge/transport/CompatibilityMemory.java',
 'app/src/main/java/com/aleyon/geminibridge/transport/GeminiStateObserver.java',
 'app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java',
 'tests/run_core_tests.sh','tests/run_core_tests.bat','tests/qa_static.py','tests/qa_interactions.py',
 'tests/qa_security.py','tests/qa_build_parity.py','tests/simulate_matrix.py','tests/compile_all_java_with_stubs.py',
 'scripts/build_windows.ps1','COMPILAR_APK_WINDOWS.bat','INSTALAR_APK_POR_USB.bat',
 '.github/workflows/qa.yml','.github/workflows/build-apk.yml','MANIFEST_SHA256.txt'
]
for rel in required:
    if not (ROOT/rel).is_file(): fail(f'missing required project file: {rel}')

sot={}
try:
    sot=json.loads(read('SOURCE_OF_TRUTH.json'))
except Exception as e: fail(f'invalid SOURCE_OF_TRUTH.json: {e}')
if sot:
    if sot.get('version')!=VERSION: fail(f'SOURCE_OF_TRUTH version mismatch: {sot.get("version")!r} != {VERSION!r}')
    if sot.get('development_source_of_truth_branch')!='develop/artemis-transport': fail('SOURCE_OF_TRUTH branch must be develop/artemis-transport')

app_gradle=read('app/build.gradle') if (ROOT/'app/build.gradle').is_file() else ''
m_code=re.search(r'\bversionCode\s+(\d+)',app_gradle); m_name=re.search(r'\bversionName\s+"([^"]+)"',app_gradle)
if not m_code: fail('app/build.gradle missing versionCode')
if not m_name: fail('app/build.gradle missing versionName')
if m_name and m_name.group(1)!=VERSION: fail(f'app/build.gradle versionName mismatch: {m_name.group(1)!r} != {VERSION!r}')
if m_code and sot and sot.get('version_code')!=int(m_code.group(1)): fail('SOURCE_OF_TRUTH version_code mismatch')
for token in ['applicationId "com.aleyon.geminibridge"','compileSdk 35','minSdk 26','targetSdk 35']:
    if token not in app_gradle: fail(f'app/build.gradle missing required setting: {token}')

# Build surfaces must follow VERSION and the current source-of-truth branch rather than an old release string.
ps=read('scripts/build_windows.ps1') if (ROOT/'scripts/build_windows.ps1').is_file() else ''
for stale in ['0.4.0-alpha8','0.5.0-alpha1']:
    if stale in ps: fail(f'build_windows.ps1 contains stale pinned version: {stale}')
if '$packageVersion' not in ps or 'AleyonBridge-v$packageVersion-debug.apk' not in ps: fail('Windows build output is not derived from VERSION')
for wf in ['.github/workflows/qa.yml','.github/workflows/build-apk.yml']:
    text=read(wf) if (ROOT/wf).is_file() else ''
    if 'develop/artemis-transport' not in text: fail(f'{wf} does not target develop/artemis-transport')
    if 'develop/bridge-clean' in text: fail(f'{wf} still targets obsolete develop/bridge-clean')

# Launcher resources used by production Java/manifest must physically exist.
for density in ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']:
    for name in ['ic_launcher.png','ic_launcher_round.png']:
        rel=f'app/src/main/res/mipmap-{density}/{name}'
        if not (ROOT/rel).is_file(): fail(f'missing launcher resource: {rel}')

# Clean architecture: removed product concepts may not return.
prod=''
for base in [ROOT/'app/src/main/java',ROOT/'app/src/main/assets/index.html']:
    if base.is_dir():
        for p in base.rglob('*'):
            if p.is_file() and p.suffix.lower() in {'.java','.html','.txt'}: prod += p.read_text(encoding='utf-8',errors='replace')+'\n'
    elif base.is_file(): prod += base.read_text(encoding='utf-8',errors='replace')+'\n'
for token in ['Notebook','notebook','Cuaderno','cuaderno','showCurtain','hideCurtain','DETACHING','REATTACHING','Remove from notebook','Add to notebook']:
    if token in prod: fail(f'forbidden legacy runtime token present: {token}')

prompt_dir=ROOT/'app/src/main/assets/prompts'
if prompt_dir.exists() and any(p.is_file() for p in prompt_dir.rglob('*')): fail('legacy prompt asset directory must be absent/empty')

excluded_dirs={'.git','.gradle','.idea','.kotlin','.cxx','.externalNativeBuild','gradle','.test-out','.full-java-stub-test','.build-cache','build'}
excluded_names={'local.properties','gradlew','gradlew.bat'}
def controlled_files():
    out=[]
    for p in ROOT.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(ROOT)
        if any(x in excluded_dirs for x in rel.parts) or p.name in excluded_names: continue
        if p.suffix.lower() in {'.apk','.iml'}: continue
        if p.name.startswith('Aleyon-Bridge-') and p.suffix.lower()=='.zip': continue
        if rel.as_posix()=='MANIFEST_SHA256.txt': continue
        out.append(rel.as_posix())
    return sorted(out)

manifest=ROOT/'MANIFEST_SHA256.txt'
if manifest.is_file():
    rows={}
    for line in manifest.read_text(encoding='utf-8').splitlines():
        if not line.strip(): continue
        try: expected,rel=line.split('  ',1)
        except ValueError: fail(f'invalid manifest row: {line!r}'); continue
        rows[rel]=expected; p=ROOT/rel
        if not p.is_file(): fail(f'manifest references missing file: {rel}')
        elif hashlib.sha256(canonical_bytes(p)).hexdigest()!=expected: fail(f'manifest hash mismatch: {rel}')
    actual=controlled_files(); listed=sorted(rows)
    for rel in sorted(set(actual)-set(listed)): fail(f'controlled file missing from manifest: {rel}')
    for rel in sorted(set(listed)-set(actual)): fail(f'manifest lists non-controlled/stale file: {rel}')

if args.source_package:
    for bad in ['.test-out','.full-java-stub-test','.build-cache','app/build','local.properties']:
        if (ROOT/bad).exists(): fail(f'build/cache artifact must not be present in source package: {bad}')

if errors:
    print('FAIL package preflight'); [print(' -',e) for e in errors]; sys.exit(1)
print(f'PASS package preflight ({"source-package" if args.source_package else "build-tree"}): {VERSION} at {ROOT}')
