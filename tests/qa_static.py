#!/usr/bin/env python3
from pathlib import Path
import re,shutil,subprocess,sys,tempfile
ROOT=Path(__file__).resolve().parents[1]; errors=[]
def strip_comments(text):
    # Inspect executable tokens only; comments may document forbidden APIs.
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    return text
def check(x,msg):
    if not x: errors.append(msg)
read=lambda p:(ROOT/p).read_text(encoding='utf-8')
service=read('app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java')
ui=read('app/src/main/java/com/aleyon/geminibridge/automation/GeminiUi.java')
overlay=read('app/src/main/java/com/aleyon/geminibridge/automation/OverlayController.java')
main=read('app/src/main/java/com/aleyon/geminibridge/MainActivity.java')
html=read('app/src/main/assets/index.html')
prompt=read('app/src/main/java/com/aleyon/geminibridge/automation/PromptRepository.java')
capsule=read('app/src/main/java/com/aleyon/geminibridge/core/ContextCapsuleBuilder.java')
store=read('app/src/main/java/com/aleyon/geminibridge/automation/LearningStore.java')
stages=read('app/src/main/java/com/aleyon/geminibridge/core/SessionStage.java')
gradle=read('app/build.gradle'); manifest=read('app/src/main/AndroidManifest.xml'); config=read('app/src/main/res/xml/accessibility_service_config.xml'); version=(ROOT/'VERSION').read_text().strip()

# Product architecture
production = service + ui + overlay + main + html + prompt + capsule + store + stages + read('app/src/main/java/com/aleyon/geminibridge/automation/PassiveGeminiProbe.java') + read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java') + read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiStateObserver.java')
forbidden = ['Notebook','notebook','Cuaderno','cuaderno','showCurtain','hideCurtain','DETACHING','REATTACHING','Remove from notebook','Add to notebook']
for token in forbidden:
    check(token not in production, f'Forbidden legacy provider-memory/curtain token returned: {token}')
check('LearningStore' in service and 'commitVerified' in service and 'commit()' in store,'Verified local learning commit missing')
check('ContextCapsuleBuilder' in capsule and 'Actualización de continuidad de Aleyon.' in capsule and 'Reconstrucción de continuidad de Aleyon.' in capsule,'Continuation/reconstruction capsule missing')
check('MAX_RECENT_EVENTS=6' in capsule and 'MAX_SUMMARY=760' in capsule and 'MAX_GOAL=720' in capsule and 'MAX_SPECIALIZATION=620' in capsule,'Capsule does not enforce compact context budgets')
check('SESSION_ID=' not in capsule and 'PROFILE_ID=' not in capsule and 'SCHEMA_VERSION=' not in capsule,'Internal session identifiers leaked into visible context')
check('No inventes recuerdos' in capsule,'Memory anti-fabrication rule missing')
check('Aleyon conserva la memoria y el progreso' in capsule,'Aleyon memory ownership rule missing')
check('sessionDebrief' in prompt and 'Resumen:' in prompt and 'Avance:' in prompt and 'A reforzar:' in prompt and 'Próximo paso:' in prompt,'Human-readable close debrief contract missing')
check('ALEYON_REPORT_BEGIN' not in production and 'ALEYON_REPORT_END' not in production and 'EVENT|' not in prompt,'Machine report protocol leaked into visible runtime')
check('lastSessionText' in store and 'sessionEvidenceText' in service,'Current-session transcript delta is not retained locally')
check('SessionTextDelta.delta' in service and 'sessionEvidenceText' in service,'Session-only evidence delta missing')
check('uniqueClickableDescendant(toolbar)' not in ui,'Ambiguous Robin-toolbar navigation fallback remains')
check(service.index('journal.baselineText(profile.id,all)') > service.index('transport.sendContext(r,intent.contextPayload)'),'Evidence baseline must be captured only after context send/settle')
check('SessionReportParser.parseDebrief' in service and 'debriefBaselineText' in service,'Debrief delta parsing missing')
check('session-progress' in read('app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java') and 'session-reinforcement' in read('app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java'),'Session close does not enrich learning evidence')
check('ConversationRegistry' in service and 'canonicalTitle=conversations.title(profile)' in service,'Canonical conversation registry missing')
check('rebuildingConversation=true' in service and 'conversations.markMissing(profile)' in service,'Canonical conversation reconstruction path missing')
check('START_LIVE_SESSION' in main and 'START_CHAT_SESSION' in main,'Live/Chat native entry points missing')
check('startLiveSession' in html and 'startChatSession' in html,'Live/Chat UI entry points missing')
check('Eliminar perfil local' in html and 'Gemini no se modifica' in html,'Local-only deletion semantics are not explicit')

# Field-tested alpha11/Nubia invariants
check('GEMINI_GOOGLE_HOST_PACKAGE' in ui and 'com.google.android.googlequicksearchbox' in ui,'Official Google Gemini host support missing')
check('isVerifiedGeminiSurface' in ui and 'hasRobinResourceSignature' in ui,'Google-host semantic verification missing')
check('assistant_robin_input_voice_chat_button_compose' in ui,'Field-proven Gemini Live launcher resource missing')
check('transport.startLive(root())' in service,'START does not use the narrow transport resolver for Live')
check('ScreenBoundsPolicy.isActionableRect' in ui and 'isActionablyVisible' in ui,'Off-screen/virtualized node protection missing')
check('interactive-window-focused' in service and 'interactive-window-active' in service,'Focused/active interactive-window root resolver missing')
check(service.index('interactive-window-focused') < service.index('interactive-window-active'),'Focused Gemini window no longer outranks active fallback')
check('GOOGLE_HOST_VERIFICATION_LEASE_MS' in service,'Short verified-host lease missing')
check('isBlockingConsentDialog' in service and 'overlay.hide();' in service,'Human consent pause policy must remove overlays')
check('GeminiUi.clickNavigationToggle' not in service and 'GeminiUi.clickNormalNewChat' not in service,'Provider selectors leaked back into service')
check('transport.openConversationList' in service and 'transport.openCanonicalConversation' in service,'Semantic canonical-chat navigation missing')
check('CONVERSATION_SEARCH' in read('app/src/main/java/com/aleyon/geminibridge/core/TransportState.java'),'Conversation search is not a first-class transport state')
composer_block=ui[ui.find('public static AccessibilityNodeInfo chatComposer'):ui.find('public static boolean sendMessage')]
check('return firstEditable(root);' not in composer_block,'Arbitrary EditText can still masquerade as Gemini composer')
check('isConversationSearchOpen' in ui and 'conversationTitleNode' in ui,'Search-surface isolation missing')
start_flow=service[service.find('private void pumpStart()'):service.find('private void pumpWait()')]
check('AdaptiveNavigationPlanner.next' in start_flow and 'conversations.isKnown(profile.id)' in start_flow,'Reactive navigation planner missing from START flow')
check('CanonicalChatRoutingPolicy.decide' not in service,'Canonical routing policy leaked back into Android orchestration')
check(start_flow.find('AdaptiveNavigationPlanner.next') < start_flow.find('transport.openConversationSearch'),'Search is not gated by a fresh navigation decision')
check('case 3 ->' not in start_flow and 'case 4 ->' not in start_flow and 'case 5 ->' not in start_flow,'Fixed list/search/query route returned to START flow')
check('MAX_START_RUNTIME_MS' in service and 'MAX_ROUTE_REPLANS' in service,'Anti-freeze transport watchdog missing')
check('o.liveAvailable' in service and 'GeminiStateObserver.observe' in service,'Live capability gate missing')
check(service.index('o.liveAvailable') < service.index('transition(profile,SessionStage.CONTEXT_INJECTING)'),'Live capability must be proven before context injection')
check('clickSendAction' in ui and 'assistant_robin_input_voice_chat_button_compose' in ui,'Nubia send-arrow resolver missing')
check('subtreeLooksLikeLive' in ui,'Send action must refuse the Live launcher')
check('cancelToReady' in service and 'isCloseableActiveStage' in service,'Universal cancel/close path missing')
check('GeminiUi.clickMoreOptionsMenu' not in service,'Provider More-options selector leaked into service')
check('transport.openChatOptions' in service and 'transport.setCanonicalTitle' in service,'Verified canonical rename transport missing')
check('GeminiUi.clickEndLive(root)' in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Live close is not encapsulated by semantic adapter')
check('clickAny(root,"Cerrar"' not in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Provider close labels leaked into transport')
check('openNativeAttachmentSurface' in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Native material attachment surface missing')
check('CompatibilityMemory' in service and 'compatibility.recordSuccess' in service and 'compatibility.recordFailure' in service,'Compatibility immune memory missing')
check('FLAG_NOT_FOCUSABLE' in overlay,'Overlay focus guard missing')
check('markNeedsAttention' in service and 'onRecoverRequested' in service,'Recoverable attention bubble behavior missing')
check('PassiveGeminiProbe.capture' in service and 'privacyRedacted' in read('app/src/main/java/com/aleyon/geminibridge/automation/PassiveGeminiProbe.java'),'Privacy-redacted passive probe missing')
check('aleyon-gemini-passive-probe-v9' in service and version in service,'Passive probe version/schema stale')
check('liveLauncherEvidence' in ui and 'structural-dual-action' in ui,'Samsung/Nubia structural Live fallback missing')
check('visibleComposerRightActionCount' in ui and 'composerRightActionCount' in read('app/src/main/java/com/aleyon/geminibridge/automation/PassiveGeminiProbe.java'),'Passive probe does not expose structural composer evidence')
check('FLAG_WATCH_OUTSIDE_TOUCH' in overlay and 'ACTION_OUTSIDE' in overlay,'Bubble panel does not collapse on outside touch')
check('snapBubbleToEdge' in overlay and 'isBubbleOnLeft' in overlay,'Messenger-style edge snapping missing')
check('humanStage' in service and 's.name()' not in service[service.find('currentStatus'):service.find('activeOverlayProfile')],'Overlay still exposes raw internal stage names')

# Android/security invariants
check('com.google.android.apps.bard,com.google.android.googlequicksearchbox' in config,'Accessibility package allow-list incomplete')
check('android:canPerformGestures="false"' in config,'Gesture capability must remain disabled')
check(not re.search(r'\bdispatchGesture\s*\(', strip_comments(service+ui)), 'Coordinate gesture automation introduced')
check('Build.MODEL' not in production and 'Build.MANUFACTURER' not in production,'Device-brand branching introduced')
check('android:allowBackup="false"' in manifest,'Local learner memory backup hardening missing')
check('android.permission.RECORD_AUDIO' not in manifest,'Bridge must not request microphone permission')
check('android.permission.INTERNET' not in manifest,'Bridge unexpectedly requests INTERNET')
check('SYSTEM_ALERT_WINDOW' not in manifest,'Broad overlay permission introduced')
check('POST_NOTIFICATIONS' in manifest and 'NotificationHelper.postSessionClosed' in service,'Session close notification regression')
check('versionCode 24' in gradle and f'versionName "{version}"' in gradle,'Android version does not match VERSION')
check(version=='0.5.0-alpha2','VERSION file mismatch')

# No obsolete prompt pipeline or upgrade-only command aliases
prompt_dir=ROOT/'app/src/main/assets/prompts'
prompt_files=sorted(p.name for p in prompt_dir.rglob('*') if p.is_file()) if prompt_dir.exists() else []
check(not prompt_files,f'Legacy prompt assets remain: {prompt_files}')
for obsolete in ['MarkerParser.java','DeletionPolicy.java','RepairPolicy.java','PromptTemplateEngine.java']:
    check(not (ROOT/'app/src/main/java/com/aleyon/geminibridge/core'/obsolete).exists(),f'Obsolete alpha11 core file remains: {obsolete}')

# UI/logo and user-decision load
check((ROOT/'app/src/main/assets/aleyon_logo.png').is_file(),'Recovered Aleyon logo is missing')
check('aleyon_logo.png' in html,'Logo is not rendered in the UI')
check('INICIAR' in html and 'Chat' in html,'Primary Live + secondary Chat controls missing')
check('proveedor' not in html.lower() and 'provider' not in html.lower(),'Provider settings leaked into UX')

# Android resource-reference integrity. Stub compilation cannot catch missing generated R entries.
java_root = ROOT/'app/src/main/java'
java_text = '\n'.join(p.read_text(encoding='utf-8') for p in java_root.rglob('*.java'))
manifest_refs = manifest
for kind in ('mipmap','drawable'):
    refs = set(re.findall(r'R\\.%s\\.([A-Za-z0-9_]+)' % kind, java_text))
    refs.update(re.findall(r'@%s/([A-Za-z0-9_]+)' % kind, manifest_refs))
    for name in sorted(refs):
        if kind == 'mipmap':
            matches = list((ROOT/'app/src/main/res').glob(f'mipmap*/{name}.*'))
        else:
            matches = list((ROOT/'app/src/main/res').glob(f'drawable*/{name}.*'))
        check(bool(matches), f'Missing Android resource for {kind}/{name}')

# Java/minSdk compatibility
alljava='\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'app/src/main/java').rglob('*.java'))
check('Map.of(' not in alljava and 'Set.of(' not in alljava,'Java APIs incompatible with minSdk policy detected')

# JavaScript syntax
node=shutil.which('node')
if node:
    scripts=re.findall(r'<script>(.*?)</script>',html,re.S); fd,tmp=tempfile.mkstemp(suffix='.js')
    try:
        with open(fd,'w',encoding='utf-8') as f:f.write('\n'.join(scripts))
        p=subprocess.run([node,'--check',tmp],text=True,capture_output=True)
        if p.returncode:errors.append('JavaScript syntax: '+p.stderr.strip())
    finally:Path(tmp).unlink(missing_ok=True)

if errors:
    print('FAIL static QA');[print(' -',e) for e in errors];sys.exit(1)
print('PASS static QA')
