#!/usr/bin/env python3
from pathlib import Path
import re,shutil,subprocess,sys,tempfile
ROOT=Path(__file__).resolve().parents[1]; errors=[]
def check(x,msg):
    if not x: errors.append(msg)
service=(ROOT/'app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java').read_text(encoding='utf-8')
ui=(ROOT/'app/src/main/java/com/aleyon/geminibridge/automation/GeminiUi.java').read_text(encoding='utf-8')
html=(ROOT/'app/src/main/assets/index.html').read_text(encoding='utf-8')
main=(ROOT/'app/src/main/java/com/aleyon/geminibridge/MainActivity.java').read_text(encoding='utf-8')
prompt=(ROOT/'app/src/main/java/com/aleyon/geminibridge/automation/PromptRepository.java').read_text(encoding='utf-8')
capsule=(ROOT/'app/src/main/java/com/aleyon/geminibridge/core/ContextCapsuleBuilder.java').read_text(encoding='utf-8')
gradle=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
config=(ROOT/'app/src/main/res/xml/accessibility_service_config.xml').read_text(encoding='utf-8')
# Architecture invariants
check('Cuadernos' not in service and 'Notebooks' not in service,'Notebook navigation remains in runtime')
check('Agregar a cuaderno' not in service and 'Quitar del cuaderno' not in service,'Attach/detach remains in runtime')
check('REATTACH' not in service and 'DETACH' not in service and 'SYNCING' not in service,'Legacy notebook states remain in runtime')
check('LearningStore' in service and 'ContextCapsuleBuilder' in capsule,'Local memory/compaction layer missing')
check('START_LIVE_SESSION' in main and 'START_CHAT_SESSION' in main,'Live/Chat entry points missing')
check('SESSION_ID=' in capsule and 'PROFILE_ID=' in capsule,'Session-scoped capsule missing ids')
check('CONFIRMACION_REQUERIDA' in capsule,'Capsule ACK contract missing')
check('ALEYON_SESSION_READY SESSION_ID=' not in capsule,'Capsule self-contains exact ACK and could false-positive')
check('START_MARKER_FORMAT=' in prompt and '<SESSION_ID_ACTUAL>' in prompt,'Report prompt must use marker placeholders')
check('LearningStore' in service and 'learning.commit' in service,'Session report is not committed locally')
check('profile.chatName' in service and 'Buscar chats' in service,'Canonical chat resolution missing')
check('getWindows()' in service and 'interactive-window-active' in service and 'interactive-window-focused' in service,'Android 16 window-root fallback missing')
check('AccessibilityWindowInfo.TYPE_APPLICATION' in service,'Root resolver lost application-window restriction')
check('TYPE_WINDOWS_CHANGED' in service and 'typeWindowsChanged' in config,'Window-change wake-up path missing')
check('GeminiUi.isGeminiRoot(candidate)' in service,'Gemini package ownership check missing')
check('dispatchGesture' not in service+ui,'Coordinate/gesture automation introduced')
check('notebookName' not in html and 'Cuadernos' not in html and 'cuaderno' not in html.lower(),'Notebook UX remains visible')
check('Proveedor' not in html and 'provider' not in html.lower(),'Provider settings leaked into user UX')
check('Cerrar sesión' in (ROOT/'app/src/main/java/com/aleyon/geminibridge/automation/OverlayController.java').read_text(encoding='utf-8'),'Bubble close action missing')
check('versionName "0.4.0-alpha1"' in gradle,'Version is not 0.4.0-alpha1')
# No old prompt assets
names={p.name for p in (ROOT/'app/src/main/assets/prompts').glob('*.txt')}
check(names=={'README.txt'},f'Unexpected legacy prompt assets: {sorted(names)}')
# Java compatibility guards
alljava='\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'app/src/main/java').rglob('*.java'))
check('Map.of(' not in alljava and 'Set.of(' not in alljava,'Java APIs incompatible with minSdk policy detected')
# JS syntax
node=shutil.which('node')
if node:
    scripts=re.findall(r'<script>(.*?)</script>',html,re.S); fd,tmp=tempfile.mkstemp(suffix='.js')
    try:
        with open(fd,'w',encoding='utf-8') as f:f.write('\n'.join(scripts))
        p=subprocess.run([node,'--check',tmp],text=True,capture_output=True)
        if p.returncode:errors.append('JavaScript syntax: '+p.stderr)
    finally:Path(tmp).unlink(missing_ok=True)
if errors:
    print('FAIL static QA');[print(' -',e) for e in errors];sys.exit(1)
print('PASS static QA')
