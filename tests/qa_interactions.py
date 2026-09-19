#!/usr/bin/env python3
from pathlib import Path
import re,sys
ROOT=Path(__file__).resolve().parents[1]; errors=[]
def check(x,msg):
    if not x:errors.append(msg)
html=(ROOT/'app/src/main/assets/index.html').read_text(encoding='utf-8')
main=(ROOT/'app/src/main/java/com/aleyon/geminibridge/MainActivity.java').read_text(encoding='utf-8')
service=(ROOT/'app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java').read_text(encoding='utf-8')

def js_funcs(text):return set(re.findall(r'function\s+([A-Za-z_$][\w$]*)\s*\(',text))
funcs=js_funcs(html)
# onclick handlers: ignore inline if/expressions and native AndroidBridge calls.
for raw in re.findall(r'onclick="([^"]+)"',html):
    m=re.match(r"\s*([A-Za-z_$][\w$]*)\s*\(",raw)
    if m and m.group(1) not in {'AndroidBridge','if'}:check(m.group(1) in funcs,f'onclick references missing JS handler: {m.group(1)}')

# Every AndroidBridge method called by the SPA must exist natively.
bridge_calls=set(re.findall(r'AndroidBridge\.([A-Za-z_$][\w$]*)\s*\(',html))
native_methods=set(re.findall(r'public\s+(?:boolean|void|String|long)\s+([A-Za-z_$][\w$]*)\s*\(',main))
for name in sorted(bridge_calls):check(name in native_methods,f'AndroidBridge method missing: {name}')

for field in ['targetLang','nativeLang','goal','level','correction','career','interests','situations','extra','conversationStyle','nativeUse']:
    check(f"document.getElementById('{field}')" in html,f'Profile field not read: {field}')
check('chatName' not in html,'Provider-side implementation state leaked into profile UI')
check('startLiveSession' in html and 'function startLive' in html,'Primary Live interaction missing')
check('startChatSession' in html and 'function startChat' in html,'Chat interaction missing')
check("['LIVE_ACTIVE','CHAT_ACTIVE']" in html,'UI does not recognize both active session modes')
check('getSessionSummaryHistory' in html and 'getProfileMemory' in html,'History/memory review interactions missing')
check('startDiagnosticProbe' in html and 'getProbeId' in html,'Correlated passive probe interaction missing')
check('getPendingSummaryProfileId' in html and 'acknowledgePendingSummary' in html,'Notification-to-summary handoff missing')
check('removeNativeLocalProfile' in html and 'deleteProfileEverywhere' not in html,'Deletion is not local-only')

# Native state-machine contracts.
check('AutomationRequest.Type.START_LIVE_SESSION' in main,'Live request not emitted natively')
check('AutomationRequest.Type.START_CHAT_SESSION' in main,'Chat request not emitted natively')
check('artemisStartAgent.nextLive' in service and 'transport.startLive(r)' in service,'Live request is not governed through Artemis + narrow transport capability')
check('GeminiStateObserver.observe' in service and 'journal.baselineText' in service,'Context settle verifier missing')
check('artemisStartAgent.nextLive(o,liveExploreForwardExhausted)' in service
      and 'exploreConversationForward' in service and 'exploreConversationBackward' in service,
      'Off-screen Live exploration missing')
check('transport.responseInProgress(r)' in service
      and service.index('transport.responseInProgress(r)') < service.index('artemisStartAgent.nextLive(o,liveExploreForwardExhausted)'),
      'START_SESSION does not separate Gemini generating from off-screen Live')
check('DEBRIEF_IDLE_TIMEOUT_MS' in service and 'debriefLastProgressAtMs' in service,
      'Slow-network debrief progress handling missing')
check('LIVE_EXIT_CONFIRM_MS' in service and 'liveExitObservedAtMs' in service
      and 'Detectando fin de Live' in service,
      'Live end is not automatically confirmed before close')
check('POST_LIVE_QUIET_MS' in service and 'stableTranscriptSinceMs' in service,
      'Post-Live quiet-state gate missing')
check('transport.hasRespondNow(r)' in service and 'transport.respondNow(r)' in service,
      'Gemini thinking recovery missing')
check('sessionDebriefRetry' in service and 'debriefRetryIssued' in service,
      'One-shot self-contained debrief retry missing')
check(service.count('transport.hasPostedUserMessage')>=2,
      'Message delivery can still be inferred from unrelated conversation text')
check('SessionReportParser.parse' in service,'Close report verifier missing')
check('learning.commitVerified' in service,'Local commit verification missing')
check('NotificationHelper.postSessionClosed' in service,'Close notification missing')
check('journal.appendCloseSummary' in service,'Close summary history persistence missing')
check('SessionTextDelta.delta' in service,'Session evidence delta missing')
check('debriefConversationAnchor=sessionEvidenceText' in service
      and service.count('SessionTextDelta.containsConversationEvidence')>=3,
      'Close can consume a debrief from a different Gemini chat')
check('showLatestSummary(id)' in html and 'AndroidBridge.acknowledgePendingSummary()' in html
      and 'openPendingSummary()' in html,
      'Notification does not open the detailed latest close')
check('captureSummaryIntent' in main and 'NotificationHelper.EXTRA_OPEN_SUMMARY_PROFILE' in main,
      'Notification tap is not routed to the requested profile summary')
for token in ['Notebook','notebook','Cuaderno','cuaderno','showCurtain','hideCurtain']:
    check(token not in service, f'Forbidden legacy runtime token in service: {token}')
check('CompatibilityMemory' in service,'Compatibility diagnostics missing')
check('artemisStartAgent.nextFreshChat' in service and 'transport.createNormalConversation' in service,'Fresh provider chat is not Artemis-governed')
check('transport.isBlankConversation' in service,'Fresh-chat postcondition missing')
check('prompts.contextCapsule(profile,ledger,sessionId' in service and '"LIVE".equals(sessionMode))' in service,'Fresh-chat continuity capsule missing')
check('ConversationRegistry' not in service and 'canonicalTitle' not in service,'Provider conversation registry leaked back into runtime')
check('recoverProfile' not in html and 'recoverProfile' not in main,'User-visible recovery API returned')

if errors:
    print('FAIL interaction QA');[print(' -',e) for e in errors];sys.exit(1)
print(f'PASS interaction QA: {len(bridge_calls)} JS->native calls, {len(funcs)} JS handlers')
