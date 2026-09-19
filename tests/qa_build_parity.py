#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def check(c,m):
    if not c: errors.append(m)
def read(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='replace')
sh=read('tests/run_core_tests.sh'); bat=read('tests/run_core_tests.bat')
ps=read('scripts/build_windows.ps1'); qawf=read('.github/workflows/qa.yml'); buildwf=read('.github/workflows/build-apk.yml')
verifier=read('tests/verify_package.py'); packager=read('scripts/package_release.py')

def java_names(text):
    return set(re.findall(r'core[\\/]([A-Za-z0-9_]+\.java)',text))
sh_java=java_names(sh); bat_java=java_names(bat)
check(sh_java==bat_java,f'Windows/Linux core javac source drift: only-sh={sorted(sh_java-bat_java)}, only-bat={sorted(bat_java-sh_java)}')
for required in ['CanonicalConversationPolicy.java','TransportState.java','SessionMaterial.java','MaterialHandoffPolicy.java']:
    check(required in sh_java and required in bat_java,f'new core class missing from one runner: {required}')

qa_steps=['simulate_matrix.py','qa_static.py','qa_interactions.py','qa_security.py','compile_all_java_with_stubs.py']
for step in qa_steps:
    check(step in sh,f'Linux runner missing {step}')
    check(step in bat,f'Windows runner missing {step}')
check('tests\\run_core_tests.bat' in ps,'Windows APK build does not invoke full BAT QA')
check('bash tests/run_core_tests.sh' in qawf,'QA workflow does not invoke Linux full QA')
check('bash tests/run_core_tests.sh' in buildwf,'APK workflow does not invoke Linux full QA')
check('develop/artemis-transport' in qawf and 'develop/artemis-transport' in buildwf,'CI branch drift')
check('AleyonBridge-v$packageVersion-debug.apk' in ps,'Windows APK name is not VERSION-derived')
for stale in ['v0.4.0-alpha8','0.4.0-alpha8 - COMPILACION','develop/bridge-clean']:
    check(stale not in ps+qawf+buildwf+read('COMPILAR_APK_WINDOWS.bat'),f'stale build token remains: {stale}')

# The app intentionally has no external runtime libraries; this reduces supply-chain surface.
gradle=read('app/build.gradle')
m=re.search(r'dependencies\s*\{(.*?)\}',gradle,re.S)
check(m is not None,'dependencies block missing')
if m: check(not m.group(1).strip(),'unexpected external Android dependency introduced')
root_gradle=read('build.gradle')
check("version '8.7.3'" in root_gradle,'Android Gradle Plugin must stay explicitly pinned for this candidate')
check('+' not in root_gradle,'floating Gradle plugin version detected')

# Integrity/preflight must be portable across Windows CRLF checkouts and Android Studio local files.
for token in ['canonical_bytes', "'.gradle'", "'.idea'", "'gradlew.bat'"]:
    check(token in verifier,f'Windows-safe package verifier missing {token}')
check('canonical_bytes' in packager and 'z.writestr(rel,canonical_bytes(p))' in packager,
      'source packager is not line-ending canonical across operating systems')

if errors:
    print('FAIL build parity QA'); [print(' -',e) for e in errors]; sys.exit(1)
print(f'PASS build parity QA: core_sources={len(sh_java)}, qa_steps={len(qa_steps)}')
