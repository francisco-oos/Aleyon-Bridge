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
check('ContextCapsuleBuilder' in capsule and 'Contexto actual de continuidad de Aleyon.' in capsule,'Fresh-session continuity capsule missing')
check('Reconstrucción de continuidad' not in capsule and 'reconstructConversation' not in capsule and 'reconstructConversation' not in prompt,'Obsolete reconstruction mode returned')
check('MAX_RECENT_EVENTS=6' in capsule and 'MAX_SUMMARY=760' in capsule and 'MAX_GOAL=720' in capsule and 'MAX_SPECIALIZATION=620' in capsule,'Capsule does not enforce compact context budgets')
check('SESSION_ID=' not in capsule and 'PROFILE_ID=' not in capsule and 'SCHEMA_VERSION=' not in capsule,'Internal session identifiers leaked into visible context')
check('No inventes recuerdos' in capsule,'Memory anti-fabrication rule missing')
check('Aleyon conserva la memoria y el progreso' in capsule,'Aleyon memory ownership rule missing')
check("No digas 'entendido'" in capsule and 'una sola pregunta principal por turno' in capsule,'Tutor prompt still encourages robotic acknowledgement/questionnaires')
check('Integra este bloque en silencio' in capsule and 'no lo confirmes' in capsule,'Context capsule is not silent/meta-free')
check('exactamente cuatro líneas, una por línea' in prompt and 'una frase breve por línea' in prompt,'Debrief prompt is not compact/deterministic')
check('sessionDebrief' in prompt and 'Resumen:' in prompt and 'Avance:' in prompt and 'A reforzar:' in prompt and 'Próximo paso:' in prompt,'Human-readable close debrief contract missing')
check('ALEYON_REPORT_BEGIN' not in production and 'ALEYON_REPORT_END' not in production and 'EVENT|' not in prompt,'Machine report protocol leaked into visible runtime')
check('lastSessionText' in store and 'sessionEvidenceText' in service,'Current-session transcript delta is not retained locally')
check('SessionTextDelta.delta' in service and 'sessionEvidenceText' in service,'Session-only evidence delta missing')
check('uniqueClickableDescendant(toolbar)' not in ui,'Ambiguous Robin-toolbar navigation fallback remains')
check(service.index('journal.baselineText(profile.id,transport.collectConversationText(r))') > service.index('boolean delivered=contextWriteIssued&&!prepared'),'Evidence baseline must be captured only after verified context delivery')
check('SessionReportParser.parseDebrief' in service and 'debriefBaselineText' in service,'Debrief delta parsing missing')
check('session-progress' in read('app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java') and 'session-reinforcement' in read('app/src/main/java/com/aleyon/geminibridge/core/SessionReportParser.java'),'Session close does not enrich learning evidence')
check('ConversationRegistry' not in production and 'canonicalTitle' not in production,'Provider conversation registry leaked back into runtime')
for obsolete in ['CanonicalConversationPolicy.java','CanonicalChatRoutingPolicy.java','RecoveryPlanner.java']:
    check(not (ROOT/'app/src/main/java/com/aleyon/geminibridge/core'/obsolete).exists(),f'Obsolete provider/recovery class remains: {obsolete}')
check(not (ROOT/'app/src/main/java/com/aleyon/geminibridge/transport/ConversationRegistry.java').exists(),'Obsolete ConversationRegistry remains')
check(not (ROOT/'app/src/main/java/com/aleyon/geminibridge/transport/SessionIntent.java').exists(),'Obsolete canonical SessionIntent remains')
check('recoverProfile' not in html and 'recoverProfile' not in main,'User-visible recovery API returned')
check('START_LIVE_SESSION' in main and 'START_CHAT_SESSION' in main,'Live/Chat native entry points missing')
check('startLiveSession' in html and 'startChatSession' in html,'Live/Chat UI entry points missing')
check('Eliminar perfil local' in html and 'Gemini no se modifica' in html,'Local-only deletion semantics are not explicit')

# Field-tested alpha11/Nubia invariants
check('GEMINI_GOOGLE_HOST_PACKAGE' in ui and 'com.google.android.googlequicksearchbox' in ui,'Official Google Gemini host support missing')
check('isVerifiedGeminiSurface' in ui and 'hasRobinResourceSignature' in ui,'Google-host semantic verification missing')
check('assistant_robin_input_voice_chat_button_compose' in ui,'Field-proven Gemini Live launcher resource missing')
check('artemisStartAgent.nextLive' in service and 'transport.startLive(r)' in service,'Live start is not governed by embedded Artemis')
check('ScreenBoundsPolicy.isActionableRect' in ui and 'isActionablyVisible' in ui,'Off-screen/virtualized node protection missing')
artemis_root=read('app/src/main/java/com/aleyon/geminibridge/artemis/ArtemisRootResolver.java')
check('artemis-window-focused' in artemis_root and 'artemis-window-active' in artemis_root,'Artemis focused/active window resolver missing')
check(artemis_root.index('artemis-window-focused') < artemis_root.index('artemis-window-active'),'Artemis focused Gemini window no longer outranks active fallback')
check('GOOGLE_HOST_VERIFICATION_LEASE_MS' in service,'Short verified-host lease missing')
check('isBlockingConsentDialog' in service and 'overlay.hide();' in service,'Human consent pause policy must remove overlays')
check('GeminiUi.clickNavigationToggle' not in service and 'GeminiUi.clickNormalNewChat' not in service,'Provider selectors leaked back into service')
check('artemisStartAgent.nextFreshChat' in service and 'transport.createNormalConversation' in service,'Fresh-chat startup is not governed by Artemis')
check('transport.isBlankConversation' in service,'Fresh-chat postcondition missing')
check('CONVERSATION_SEARCH' in read('app/src/main/java/com/aleyon/geminibridge/core/TransportState.java'),'Conversation search is not a first-class transport state')
composer_block=ui[ui.find('public static AccessibilityNodeInfo chatComposer'):ui.find('public static boolean sendMessage')]
check('return firstEditable(root);' not in composer_block,'Arbitrary EditText can still masquerade as Gemini composer')
check('isConversationSearchOpen' in ui,'Search-surface isolation missing')
start_flow=service[service.find('private void pumpStart()'):service.find('private void pumpWait()')]
artemis_flash=read('app/src/main/java/com/aleyon/geminibridge/artemis/ArtemisFlashAgent.java')
artemis_root=read('app/src/main/java/com/aleyon/geminibridge/artemis/ArtemisRootResolver.java')
check('ArtemisFlashAgent' in service and 'artemisStartAgent.nextFreshChat' in start_flow,'Embedded Artemis runtime missing from START flow')
check('ArtemisRootResolver.resolve' in service,'Artemis multi-window root resolver missing')
check('CanonicalChatRoutingPolicy' not in service and 'openConversationSearch' not in service,'Canonical/search routing leaked back into Android orchestration')
check('switch(step)' not in service and not re.search(r'case\s+\d+\s*->',service),'Numeric fixed-step UI state machine returned')
check('TRANSCRIPT_SETTLE_MS' not in service and 'transcriptSettlePasses' not in service,'Blind fixed transcript wait returned')
check('StartPhase' in service and 'ClosePhase' in service,'Goal-oriented lifecycle phases missing')
check('AdaptiveNavigationPlanner' not in service and not (ROOT/'app/src/main/java/com/aleyon/geminibridge/core/AdaptiveNavigationPlanner.java').exists(),'Superseded custom navigation planner still exists')
check('memory.load' in artemis_flash and 'memory.invalidate' in artemis_flash and 'memory.save' in artemis_flash,'Artemis routine learn/replay/invalidate loop incomplete')
routine_keys=re.findall(r'artemisRoutineKey\("([^"]+)"\)',service)
check(sorted(routine_keys)==['close-session','start-session'],f'Artemis runtime expanded beyond START_SESSION/CLOSE_SESSION: {routine_keys}')
check('FOCUS_INPUT' in artemis_root and 'FOCUS_ACCESSIBILITY' in artemis_root,'Artemis focused-root recovery tier missing')
check('SystemClock.sleep' not in artemis_root and 'RETRY_BACKOFF_MS' not in artemis_root,'Blocking programmed waits returned to Artemis root resolution')
check('MAX_START_RUNTIME_MS' in service and 'MAX_CLOSE_RUNTIME_MS' in service,'Anti-freeze transport watchdog missing')
check('artemisStartAgent.nextContext' in service and 'artemisStartAgent.nextLive' in service,'Artemis does not govern start-session transport end-to-end')
check('SCROLL_FORWARD' in artemis_flash and 'SCROLL_BACKWARD' in artemis_flash,'Artemis cannot learn viewport exploration')
check('ARTEMIS_POLICY_VERSION=4' in service and '"policy-"+ARTEMIS_POLICY_VERSION' in service,'Artemis learned routines are not namespaced by transport policy')
check('hasPostedUserMessage' in ui and service.count('transport.hasPostedUserMessage')>=2,'Context/debrief delivery still relies on generic text deltas')
check('sessionDebriefNudge' not in prompt and 'Responde ahora al mensaje anterior' not in prompt,'Ambiguous previous-message nudge returned')
check('sessionDebriefRetry' in prompt and 'Segundo intento de cierre' in prompt,'Self-contained debrief retry missing')
check('isShowingHintText' in ui and 'structuralLiveCandidate' in ui,'Live structural fallback treats placeholder text as typed content')
check('isResponseInProgress' in ui and 'hasRespondNow' in ui and 'clickRespondNow' in ui,'Gemini response-progress recovery capability missing')
response_block=ui[ui.find('public static boolean isResponseInProgress'):ui.find('public static boolean hasRespondNow')]
check('"Stop")' not in response_block and '"Detener")' not in response_block,'Generic learner utterance can still masquerade as response-progress control')
send_block=ui[ui.find('public static boolean clickSendAction'):ui.find('private static AccessibilityNodeInfo rightmostSendCandidate')]
check('isResponseInProgress(root)' in send_block,'Reused Gemini action slot can still be clicked as Send while a response is active')
check('replayLiveOr' in artemis_flash and 'o.liveAvailable&&scroll' in artemis_flash,'Learned Live exploration is not guarded by current capability evidence')
check('exploreConversationForward' in service and 'exploreConversationBackward' in service,'START_SESSION cannot explore a scrolled Gemini conversation')
check('hasConversationViewport' in ui and 'normal-chat-scrolled' in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiStateObserver.java'),'Scrolled Gemini chat is not observable as a normal conversation')
check('transport.writeContext' in service and 'transport.submitPreparedContext' in service,'Verified two-phase context delivery missing')
check('contextSubmitAttempts>=3' in service,'Context submit must fail/relearn quickly instead of burning the generic 32-retry budget')
check('transport.sendContext(' not in service,'Legacy monolithic context sender returned')
check(service.index('artemisStartAgent.nextContext') < service.index('artemisStartAgent.nextLive'),'Context delivery must complete before Artemis starts Live')
check('clickSendAction' in ui and 'assistant_robin_input_voice_chat_button_compose' in ui,'Nubia send-arrow resolver missing')
check('current == null || current.toString().trim().isEmpty()' in ui,'Send resolver is not gated by a non-empty verified composer')
check('cancelToReady' in service and 'isCloseableActiveStage' in service,'Universal cancel/close path missing')
check('GeminiUi.clickMoreOptionsMenu' not in service,'Provider More-options selector leaked into service')
check('artemisRenameAgent' not in service and 'setCanonicalTitle' not in service,'Canonical rename leaked back into runtime')
check('artemisCloseAgent.nextEndLive' in service and 'transport.endLive(r)' in service,'Live close is not governed by Artemis')
check('artemisCloseAgent.nextContext' in service,'Debrief delivery is not governed by close-session Artemis task')
close_flow=service[service.find('private void pumpClose()'):service.find('private void finishReady()')]
check('transport.createNormalConversation' not in close_flow,'Close flow must never create a provider chat')
check('launchGemini();moveClose(ClosePhase.END_LIVE);' not in close_flow,'Close flow still blindly relaunches Gemini')
check('if(transport.isGeminiSurface(r))' in close_flow and 'if(!closeLaunchIssued){closeLaunchIssued=true;launchGemini();}' in close_flow,'Close flow does not preserve current Gemini surface before relaunching')
check('if("CHAT".equals(sessionMode) && o.state==TransportState.NORMAL_CHAT)' not in close_flow and 'if(o.state==TransportState.NORMAL_CHAT)' in close_flow,'Close still trusts stale mode instead of observed Live/Chat state')
check('DEBRIEF_IDLE_TIMEOUT_MS=180_000L' in service and 'debriefLastProgressAtMs' in close_flow and 'commitTranscriptFallback' in close_flow and 'DEBRIEF_TIMEOUT' in close_flow,'Missing progress-sensitive debrief fallback')
check('POST_LIVE_QUIET_MS=6_000L' in service and 'stableTranscriptSinceMs' in close_flow,'Post-Live close is not based on a quiet transcript window')
check('transport.scrollConversation(r)' not in close_flow[close_flow.find('case STABILIZE_TRANSCRIPT'):close_flow.find('case DELIVER_DEBRIEF')],'Transcript stabilization still scrolls Gemini robotically')
check('DEBRIEF_RESPOND_NOW_AFTER_MS=8_000L' in service and 'transport.respondNow' in close_flow,'Respond-now recovery is not bounded into CLOSE_SESSION')
check('DEBRIEF_SILENT_RETRY_MS=30_000L' in service and 'DEBRIEF_INVALID_RESPONSE_QUIET_MS=4_000L' in service,'Evidence-driven debrief retry thresholds missing')
check('debriefRetryIssued' in close_flow and '!debriefRetryIssued' in close_flow,'Debrief retry can repeat without a one-shot guard')
check('debriefConversationAnchor=sessionEvidenceText' in close_flow,'Close is not bound to current-session evidence')
check(close_flow.count('SessionTextDelta.containsConversationEvidence')>=3,'Cross-chat contamination guards are incomplete')
check('Vuelve al chat de esta sesión' in close_flow,'Conversation drift does not surface a safe user action')
check('DEBRIEF_RESPONSE_TIMEOUT_MS' not in service,'Old fixed 45-second debrief timeout returned')
check('if(r.isClosing())return;' in service and 'if(mode==Mode.CLOSE)return;' in service,'Repeated close can restart an in-flight close')
check(close_flow.index('artemisCloseAgent.complete()') < close_flow.index('moveClose(ClosePhase.WAIT_DEBRIEF)'),'Artemis close routine is not saved before waiting for Gemini cognition')
check('Finalizar Live y guardar' not in overlay,'Live still exposes a duplicate manual close path in the bubble')
check('cierra Live con la X de Gemini' in overlay and 'Guardar y cerrar chat' in overlay,'Bubble does not explain native Live end / manual Chat close split')
check('LIVE_EXIT_CONFIRM_MS=900L' in service and 'liveExitObservedAtMs' in service and 'Detectando fin de Live' in service,'Automatic Live-end confirmation gate missing')
check('transport.sendContext(' not in service,'Legacy monolithic message sender still used by runtime')
check('stableTranscriptSinceMs' in service and 'current.equals(stableTranscriptSnapshot)' in service and 'POST_LIVE_QUIET_MS' in service,'Transcript close does not use observed stability')
check('TRANSCRIPT_SETTLE_MS' not in service,'Fixed transcript settling delay returned')
check('GeminiUi.clickEndLive(root)' in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Live close is not encapsulated by semantic adapter')
check('clickAny(root,"Cerrar"' not in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Provider close labels leaked into transport')
check('openNativeAttachmentSurface' in read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java'),'Native material attachment surface missing')
check('CompatibilityMemory' in service and 'compatibility.recordFailure' in service,'Compatibility diagnostics missing')
check('FLAG_NOT_FOCUSABLE' in overlay,'Overlay focus guard missing')
check('markNeedsAttention' in service and 'onRecoverRequested' not in service,'Attention state must not expose a recovery workflow')
check('PassiveGeminiProbe.capture' in service and 'privacyRedacted' in read('app/src/main/java/com/aleyon/geminibridge/automation/PassiveGeminiProbe.java'),'Privacy-redacted passive probe missing')
check('aleyon-gemini-passive-probe-v10' in service and version in service,'Passive probe version/schema stale')
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
check('showLatestSummary(id)' in read('app/src/main/assets/index.html'),'Notification resume does not open the latest detailed summary')
notification=read('app/src/main/java/com/aleyon/geminibridge/automation/NotificationHelper.java')
check('EXTRA_OPEN_SUMMARY_PROFILE' in notification and 'tapIntent.putExtra' in notification and 'FLAG_ACTIVITY_SINGLE_TOP' in notification,'Notification tap is not bound to a profile summary')
check('captureSummaryIntent' in main and 'onNewIntent' in main and 'NotificationHelper.EXTRA_OPEN_SUMMARY_PROFILE' in main,'MainActivity does not consume notification summary intents')
check('openPendingSummary()' in html,'Cold/resumed app cannot open a pending detailed summary')
check('versionCode 31' in gradle and f'versionName "{version}"' in gradle,'Android version does not match VERSION')
check(version=='0.5.0-alpha9','VERSION file mismatch')

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

check('am clear-debug-app' in read('INSTALAR_APK_POR_USB.bat'),'USB installer does not clear stale Android wait-for-debugger selection')
