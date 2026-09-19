package com.aleyon.geminibridge.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.aleyon.geminibridge.MainActivity;
import com.aleyon.geminibridge.artemis.ArtemisFlashAgent;
import com.aleyon.geminibridge.artemis.ArtemisRootResolver;
import com.aleyon.geminibridge.core.AutomationDiagnostics;
import com.aleyon.geminibridge.core.LearningEvent;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.SessionReportParser;
import com.aleyon.geminibridge.core.SessionStage;
import com.aleyon.geminibridge.core.SessionTextDelta;
import com.aleyon.geminibridge.core.TransportState;
import com.aleyon.geminibridge.transport.CompatibilityMemory;
import com.aleyon.geminibridge.transport.GeminiConversationTransport;
import com.aleyon.geminibridge.transport.GeminiStateObserver;
import com.aleyon.geminibridge.transport.TransportObservation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Aleyon Bridge runtime on the field-tested alpha11 Android/Gemini layer.
 *
 * Aleyon owns profile, continuity and learning evidence. Gemini remains the
 * native cognitive/Live/multimodal surface. Each explicit session starts in
 * a fresh normal Gemini chat, injects bounded local continuity, runs Chat/Live,
 * then commits learning back to Aleyon. Provider chat history is disposable:
 * local Aleyon memory is the only continuity authority.
 */
public final class AleyonAccessibilityService extends AccessibilityService
        implements OverlayController.Listener {

    public static final String GEMINI_PACKAGE=GeminiUi.GEMINI_APP_PACKAGE;
    public static final String GEMINI_HOST_PACKAGE=GeminiUi.GEMINI_GOOGLE_HOST_PACKAGE;
    private static final long GOOGLE_HOST_VERIFICATION_LEASE_MS=8_000L;
    private static final long OBSERVE_FALLBACK_MS=350L;
    private static final long RESPONSE_OBSERVE_FALLBACK_MS=650L;
    private static final int MAX_UI_RETRIES=8;
    private static final int MAX_LIVE_EXPLORE_STALLS=6;
    private static final int MAX_LIVE_EXPLORE_MOVES=16;
    private static final int ARTEMIS_POLICY_VERSION=4;
    private static final long LIVE_EXIT_CONFIRM_MS=900L;
    private static final long MAX_START_RUNTIME_MS=180_000L;
    private static final long MAX_CLOSE_RUNTIME_MS=360_000L;
    private static final long DEBRIEF_IDLE_TIMEOUT_MS=180_000L;
    private static final long DEBRIEF_SLOW_NOTICE_MS=15_000L;
    private static final long POST_LIVE_QUIET_MS=6_000L;
    private static final long DEBRIEF_RESPOND_NOW_AFTER_MS=8_000L;
    private static final long DEBRIEF_SILENT_RETRY_MS=30_000L;
    private static final long DEBRIEF_INVALID_RESPONSE_QUIET_MS=4_000L;

    private static AleyonAccessibilityService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SessionJournal journal;
    private LearningStore learning;
    private PromptRepository prompts;
    private OverlayController overlay;
    private DiagnosticsRecorder diagnostics;
    private CompatibilityMemory compatibility;
    private GeminiConversationTransport transport;
    private Runner runner;
    private boolean passiveProbeInProgress;

    private String lastRootSource="none",lastWindowsSummary="";
    private int lastInteractiveWindowCount;
    private long googleHostVerifiedUntilMs;

    public static AleyonAccessibilityService instance(){return instance;}

    public static boolean submit(android.content.Context context,AutomationRequest request){
        try{
            AutomationCommandBus.enqueue(context,request);
            AleyonAccessibilityService s=instance;
            if(s!=null)s.kick();
            // Durable queue acceptance is success even if Android is rebinding the service.
            return true;
        }catch(Exception e){return false;}
    }

    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;
        journal=new SessionJournal(this);learning=new LearningStore(this);
        prompts=new PromptRepository(this);overlay=new OverlayController(this,this);
        diagnostics=new DiagnosticsRecorder(this);
        compatibility=new CompatibilityMemory(this);transport=new GeminiConversationTransport();kick();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED){
            Runner r=runner;if(r!=null)r.schedule(120);return;
        }
        if(event.getPackageName()==null||!GeminiUi.isGeminiPackage(event.getPackageName()))return;
        Runner r=runner;if(r!=null)r.schedule(180);
    }

    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(overlay!=null)overlay.hide();if(instance==this)instance=null;super.onDestroy();}
    @Override public void onReturnToGemini(){launchGemini();}

    @Override public void onCloseRequested(){
        Runner r=runner;
        if(r!=null&&!r.finished){
            if(r.isClosing())return;
            r.beginClose();return;
        }
        ProfileSpec p=activeOverlayProfile();
        if(p!=null)startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));
    }

    @Override public OverlayController.Status currentStatus(){
        ProfileSpec p=activeOverlayProfile();
        if(p==null)return new OverlayController.Status("","",false);
        SessionStage s=journal.stage(p.id);
        boolean pending=s==SessionStage.AMBIGUOUS_USER_REQUIRED||s==SessionStage.USER_ACTION_REQUIRED
                ||s==SessionStage.APP_UPDATE_REQUIRED||s==SessionStage.ERROR;
        return new OverlayController.Status(humanStage(s),journal.lastError(p.id),pending);
    }

    private static String humanStage(SessionStage s){
        if(s==null)return "Listo";
        return switch(s){
            case READY -> "Listo";
            case OPENING_SESSION_CHAT, CONTEXT_INJECTING, CONTEXT_READY -> "Preparando";
            case LIVE_STARTING -> "Abriendo Live";
            case LIVE_ACTIVE -> "En Live";
            case CHAT_ACTIVE -> "En chat";
            case CLOSING_SESSION -> "Cerrando";
            case WAITING_TRANSCRIPT, COMMITTING -> "Guardando";
            case ANALYZING -> "Esperando resumen";
            case ERROR, AMBIGUOUS_USER_REQUIRED, USER_ACTION_REQUIRED -> "Requiere atención";
            case APP_UPDATE_REQUIRED -> "Compatibilidad";
        };
    }

    private ProfileSpec activeOverlayProfile(){
        String id=getSharedPreferences("aleyon_runtime",MODE_PRIVATE).getString("active_profile_id",null);
        return id==null?null:journal.loadProfile(id);
    }

    public void kick(){
        handler.post(()->{
            if(passiveProbeInProgress)return;
            if(runner!=null&&!runner.finished)return;
            AutomationRequest req=AutomationCommandBus.consume(this);if(req==null)return;
            try{
                if(req.type==AutomationRequest.Type.DIAGNOSTIC_PROBE){startPassiveProbe(req.profileJson);return;}
                ProfileSpec p=ProfileSpec.fromJson(req.profileJson);journal.saveProfile(p);
                switch(req.type){
                    case START_LIVE_SESSION -> startFreshSession(p,"LIVE");
                    case START_CHAT_SESSION -> startFreshSession(p,"CHAT");
                    case CLOSE_SESSION -> closeOrCancel(p);
                    default -> {}
                }
            }catch(Exception ignored){}
        });
    }

    private void startPassiveProbe(String requestedProbeId){
        passiveProbeInProgress=true;
        final JSONArray samples=new JSONArray();
        final String probeId=requestedProbeId==null?"":requestedProbeId.trim();
        getSharedPreferences("aleyon_probe",MODE_PRIVATE).edit().putString("last_probe","{}")
                .putLong("last_probe_ts",0L).putString("last_probe_id","").apply();
        launchGemini();
        long[] delays={900L,2200L,3600L};
        for(int i=0;i<delays.length;i++){
            final int n=i;handler.postDelayed(()->{try{samples.put(PassiveGeminiProbe.capture(this,n));}catch(Exception ignored){}},delays[i]);
        }
        handler.postDelayed(()->{
            JSONObject report=new JSONObject();
            try{report.put("schema","aleyon-gemini-passive-probe-v10").put("version","0.5.0-alpha10")
                    .put("probeId",probeId).put("readOnly",true).put("sampleCount",samples.length()).put("samples",samples);}catch(Exception ignored){}
            getSharedPreferences("aleyon_probe",MODE_PRIVATE).edit().putString("last_probe",report.toString())
                    .putLong("last_probe_ts",System.currentTimeMillis()).putString("last_probe_id",probeId).apply();
            passiveProbeInProgress=false;launchAleyon();handler.postDelayed(this::kick,250L);
        },4300L);
    }

    /** Explicit START always creates a fresh local session transaction in a fresh provider chat. */
    private void startFreshSession(ProfileSpec p,String mode){
        SessionStage s=journal.stage(p.id);
        AccessibilityNodeInfo current=resolveGeminiRoot();
        if(s==SessionStage.LIVE_ACTIVE && "LIVE".equals(journal.activeSessionMode(p.id))
                && transport.isLiveActive(current)){
            rememberActive(p);showOverlay(p);startWaitRunner(p,"LIVE");return;
        }
        abandonUnfinishedRuntime(p);
        startRunner("CHAT".equals(mode)?Mode.START_CHAT:Mode.START_LIVE,p,mode);
    }

    private void closeOrCancel(ProfileSpec p){
        SessionStage s=journal.stage(p.id);
        if(isCloseableStage(s)) startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));
        else finishReadyOutsideRunner(p);
    }

    private boolean isCloseableStage(SessionStage s){
        return s==SessionStage.LIVE_ACTIVE||s==SessionStage.CHAT_ACTIVE
                ||s==SessionStage.CLOSING_SESSION||s==SessionStage.WAITING_TRANSCRIPT
                ||s==SessionStage.ANALYZING||s==SessionStage.COMMITTING;
    }

    private void abandonUnfinishedRuntime(ProfileSpec p){
        Runner r=runner;if(r!=null&&!r.finished)r.abortWithoutCommit();
        journal.clearError(p.id);journal.clearActiveSession(p.id);
        journal.stage(p.id,SessionStage.READY);clearActive(p);if(overlay!=null)overlay.hide();
    }

    private void startRunner(Mode mode,ProfileSpec p,String sessionMode){
        googleHostVerifiedUntilMs=0L;rememberActive(p);
        if(overlay!=null)overlay.showWorking(p.label,phaseLabel(mode));
        runner=new Runner(mode,p,sessionMode);runner.schedule(0);
    }
    private void startWaitRunner(ProfileSpec p,String sessionMode){runner=new Runner(Mode.WAIT,p,sessionMode);runner.schedule(0);}

    private static String phaseLabel(Mode mode){
        return switch(mode){
            case START_LIVE -> "Preparando Live…";
            case START_CHAT -> "Preparando chat…";
            case WAIT -> "Sesión activa";
            case CLOSE -> "Cerrando y guardando…";
        };
    }

    private void finishReadyOutsideRunner(ProfileSpec p){
        journal.clearError(p.id);journal.stage(p.id,SessionStage.READY);
        journal.clearActiveSession(p.id);clearActive(p);if(overlay!=null)overlay.hide();launchAleyon();
    }

    private void launchGemini(){
        Intent i=getPackageManager().getLaunchIntentForPackage(GEMINI_PACKAGE);
        if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}
    }
    private void launchAleyon(){Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}

    /** alpha11 field-tested root resolver: focused Gemini modal outranks stale active root. */
    private AccessibilityNodeInfo resolveGeminiRoot(){
        lastRootSource="none";lastWindowsSummary="";lastInteractiveWindowCount=0;
        try{
            ArtemisRootResolver.Result result=ArtemisRootResolver.resolve(
                    this,this::isTrustedGeminiCandidate);
            lastRootSource=result.source;
            lastWindowsSummary=result.summary;
            lastInteractiveWindowCount=result.windowCount;
            return result.root;
        }catch(Exception e){
            lastWindowsSummary="artemisResolverException="+e.getClass().getSimpleName();
            return null;
        }
    }

    private boolean isTrustedGeminiCandidate(AccessibilityNodeInfo node){
        if(node==null||node.getPackageName()==null)return false;CharSequence pkg=node.getPackageName();
        if(GEMINI_PACKAGE.contentEquals(pkg))return true;if(!GEMINI_HOST_PACKAGE.contentEquals(pkg))return false;
        long now=System.currentTimeMillis();
        if(GeminiUi.isVerifiedGeminiSurface(node)){googleHostVerifiedUntilMs=now+GOOGLE_HOST_VERIFICATION_LEASE_MS;return true;}
        return runner!=null&&now<=googleHostVerifiedUntilMs;
    }
    private static String sourceFor(AccessibilityNodeInfo node,String base){return GEMINI_HOST_PACKAGE.equals(packageName(node))?base+":google-host":base+":gemini-app";}
    private String artemisRoutineKey(String capability){
        return "policy-"+ARTEMIS_POLICY_VERSION+"|"+capability+"|"
                +packageVersion(GEMINI_PACKAGE)+"|"+packageVersion(GEMINI_HOST_PACKAGE);
    }
    private long packageVersion(String pkg){
        try{
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(pkg,0);
            return android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        }catch(Exception e){return 0L;}
    }
    private static String packageName(AccessibilityNodeInfo n){return n==null||n.getPackageName()==null?"":n.getPackageName().toString();}

    private void rememberActive(ProfileSpec p){if(p!=null)getSharedPreferences("aleyon_runtime",MODE_PRIVATE).edit().putString("active_profile_id",p.id).apply();}
    private void clearActive(ProfileSpec p){if(p!=null)getSharedPreferences("aleyon_runtime",MODE_PRIVATE).edit().remove("active_profile_id").apply();}
    private String status(ProfileSpec p,String field){try{return journal.statusJson(p.id).optString(field,"");}catch(Exception e){return "";}}
    private void showOverlay(ProfileSpec p){if(overlay!=null)overlay.show(p.label,status(p,"objectiveToday"),status(p,"starterPhrase"));}

    private void transition(ProfileSpec p,SessionStage next){if(p!=null)journal.stage(p.id,next);}

    private void recordDiagnostic(ProfileSpec p,String step,String result){
        try{
            AccessibilityNodeInfo r=resolveGeminiRoot();
            diagnostics.record(new AutomationDiagnostics(
                    journal.activeSessionId(p.id),step,journal.stage(p.id).name(),journal.stage(p.id).name(),
                    System.currentTimeMillis(),packageName(r),r!=null,GeminiUi.countNodes(r),"",0,result,
                    lastRootSource,lastInteractiveWindowCount,lastWindowsSummary));
        }catch(Exception ignored){}
    }

    private enum Mode { START_LIVE, START_CHAT, WAIT, CLOSE }
    private enum StartPhase { INIT, ENSURE_FRESH_CHAT, DELIVER_CONTEXT, WAIT_CONTEXT_READY, ACTIVATE_SESSION }
    private enum ClosePhase { INIT, END_LIVE, STABILIZE_TRANSCRIPT, DELIVER_DEBRIEF, WAIT_DEBRIEF }

    private final class Runner {
        private Mode mode;
        private final ProfileSpec profile;
        private String sessionMode;
        private String sessionId;
        private int retries;
        private boolean finished,waitingForUserConsent;
        private StartPhase startPhase=StartPhase.INIT;
        private ClosePhase closePhase=ClosePhase.INIT;
        private ArtemisFlashAgent artemisStartAgent,artemisCloseAgent;
        private boolean contextInitialized,contextWriteIssued,contextWriteVerified,contextSubmitPending,liveStartPending;
        private int contextSubmitAttempts,contextMissingPasses;
        private String contextPayload="";
        private int liveExploreStalls,liveExploreMoves;
        private boolean liveExploreForwardExhausted;
        private boolean endLivePending,closeLaunchIssued;
        private boolean debriefInitialized,debriefWriteIssued,debriefWriteVerified,debriefSubmitPending;
        private int debriefSubmitAttempts,debriefMissingPasses;
        private String debriefPayload="",debriefResponseBaselineText="",debriefLastObservedText="";
        private String stableTranscriptSnapshot="",debriefConversationAnchor="";
        private long stableTranscriptSinceMs;
        private boolean debriefSlowNoticeShown,conversationDriftNotified;
        private boolean debriefRespondNowIssued,debriefRetryWriteIssued,debriefRetryIssued;
        private boolean debriefRetrySubmitPending;
        private int debriefRetryWriteAttempts,debriefRetrySubmitAttempts;
        private String debriefRetryPayload="";
        private final long runnerStartedAtMs=System.currentTimeMillis();
        private long liveExitObservedAtMs;
        private long closeStartedAtMs,debriefWaitStartedAtMs,debriefLastProgressAtMs,postLiveChatObservedAtMs;
        private String sessionEvidenceText="", debriefBaselineText="", contextBaselineText="";
        private final Runnable pumpRunnable=this::pump;

        Runner(Mode mode,ProfileSpec p,String sessionMode){
            this.mode=mode;this.profile=p;this.sessionMode=sessionMode==null?"LIVE":sessionMode;
            this.sessionId=journal.activeSessionId(p.id);
            if(mode==Mode.CLOSE)closeStartedAtMs=System.currentTimeMillis();
        }

        void schedule(long delay){if(finished)return;handler.removeCallbacks(pumpRunnable);handler.postDelayed(pumpRunnable,delay);}
        void abortWithoutCommit(){finished=true;handler.removeCallbacks(pumpRunnable);}
        void beginClose(){
            if(mode==Mode.CLOSE)return;
            SessionStage current=journal.stage(profile.id);
            if(!isCloseableActiveStage(current)){cancelToReady();return;}
            mode=Mode.CLOSE;closePhase=ClosePhase.INIT;retries=0;
            stableTranscriptSnapshot="";stableTranscriptSinceMs=0L;
            closeLaunchIssued=false;debriefWaitStartedAtMs=0L;debriefLastProgressAtMs=0L;
            debriefResponseBaselineText="";debriefLastObservedText="";debriefSlowNoticeShown=false;
            debriefRespondNowIssued=false;debriefRetryWriteIssued=false;debriefRetryIssued=false;
            debriefRetrySubmitPending=false;debriefRetryWriteAttempts=0;debriefRetrySubmitAttempts=0;
            debriefRetryPayload="";postLiveChatObservedAtMs=0L;
            closeStartedAtMs=System.currentTimeMillis();
            if(overlay!=null)overlay.showWorking(profile.label,phaseLabel(Mode.CLOSE));schedule(0);
        }
        boolean isClosing(){return mode==Mode.CLOSE;}
        private boolean isCloseableActiveStage(SessionStage s){return isCloseableStage(s);}
        private void cancelToReady(){
            finished=true;handler.removeCallbacks(pumpRunnable);
            try{AccessibilityNodeInfo r=root();if(r!=null)transport.clearComposer(r);}catch(Exception ignored){}
            journal.clearError(profile.id);journal.stage(profile.id,SessionStage.READY);
            journal.clearActiveSession(profile.id);clearActive(profile);
            if(overlay!=null)overlay.hide();launchAleyon();
        }

        void pump(){
            if(finished)return;
            if((mode==Mode.START_LIVE||mode==Mode.START_CHAT)
                    && System.currentTimeMillis()-runnerStartedAtMs>MAX_START_RUNTIME_MS){
                failUpdate("El transporte no logró progresar dentro del tiempo seguro. "
                        +"Aleyon abortó sin escribir en una superficie no verificada.");
                return;
            }
            if(mode==Mode.CLOSE && closeStartedAtMs>0
                    && System.currentTimeMillis()-closeStartedAtMs>MAX_CLOSE_RUNTIME_MS){
                fail("El cierre no alcanzó una condición verificable dentro del tiempo seguro.");
                return;
            }
            try{
                AccessibilityNodeInfo blocker=root();
                if(GeminiUi.isBlockingConsentDialog(blocker)){
                    // Security/consent surfaces must receive untouched input.
                    // Android may deliberately reject permission-button taps
                    // while an accessibility overlay is visible (tapjacking
                    // protection), so remove every Aleyon overlay while the
                    // human makes the decision. We never click it for them.
                    if(!waitingForUserConsent){waitingForUserConsent=true;if(overlay!=null)overlay.hide();}
                    schedule(OBSERVE_FALLBACK_MS);return;
                }
                if(waitingForUserConsent){
                    waitingForUserConsent=false;
                    if(overlay!=null){
                        if(mode==Mode.WAIT)showOverlay(profile);
                        else overlay.showWorking(profile.label,phaseLabel(mode));
                    }
                }
                switch(mode){case START_LIVE,START_CHAT->pumpStart();case WAIT->pumpWait();case CLOSE->pumpClose();}
            }catch(Exception e){fail("Error interno de automatización: "+e.getClass().getSimpleName());}
        }

        private AccessibilityNodeInfo root(){return resolveGeminiRoot();}
        private void moveStart(StartPhase next){startPhase=next;retries=0;schedule(0);}
        private void moveClose(ClosePhase next){closePhase=next;retries=0;schedule(0);}
        private String newSessionId(){return "session-"+UUID.randomUUID().toString().replace("-","").substring(0,16);}

        private TransportObservation observe(){
            return GeminiStateObserver.observe(root());
        }

        private boolean retryAdaptive(String reason){
            TransportObservation o=observe();
            compatibility.recordFailure(profile.id,reason+" | "+o.state+" | "+o.evidence);
            retries++;
            if(retries>MAX_UI_RETRIES){
                failUpdate(reason+" El transporte semántico no pudo recuperar esta variante de Gemini.");
                return false;
            }
            schedule(OBSERVE_FALLBACK_MS);return true;
        }

        private void pumpStart(){
            if(!profile.canStart()){fail("Perfil incompleto para iniciar sesión.");return;}
            switch(startPhase){
                case INIT -> {
                    sessionMode=mode==Mode.START_CHAT?"CHAT":"LIVE";
                    if(sessionId==null||sessionId.isEmpty())sessionId=newSessionId();
                    artemisStartAgent=new ArtemisFlashAgent(AleyonAccessibilityService.this,
                            artemisRoutineKey("start-session"));
                    journal.activeSession(profile.id,sessionId,sessionMode);
                    transition(profile,SessionStage.OPENING_SESSION_CHAT);
                    launchGemini();moveStart(StartPhase.ENSURE_FRESH_CHAT);
                }
                case ENSURE_FRESH_CHAT -> {
                    AccessibilityNodeInfo r=root();
                    TransportObservation o=GeminiStateObserver.observe(r);
                    boolean blank=transport.isBlankConversation(r);
                    ArtemisFlashAgent.Action action=artemisStartAgent.nextFreshChat(o,blank);
                    switch(action){
                        case WAIT -> schedule(OBSERVE_FALLBACK_MS);
                        case BACK -> {
                            if(performGlobalAction(GLOBAL_ACTION_BACK)){
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_FRESH_CHAT,o.state,action);
                                schedule(0);return;
                            }
                            artemisStartAgent.actionFailed();
                            retryAdaptive("Artemis no pudo volver a una superficie normal de Gemini.");
                        }
                        case CREATE_NORMAL_CHAT -> {
                            if(transport.createNormalConversation(r)){
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_FRESH_CHAT,o.state,action);
                                schedule(0);return;
                            }
                            artemisStartAgent.actionFailed();
                            retryAdaptive("Artemis no pudo abrir un chat nuevo de Gemini.");
                        }
                        case COMPLETE_FRESH_CHAT -> moveStart(StartPhase.DELIVER_CONTEXT);
                        case FAIL_CLOSED -> {
                            artemisStartAgent.actionFailed();
                            retryAdaptive("Artemis no pudo verificar un chat nuevo y seguro de Gemini.");
                        }
                        default -> failUpdate("Artemis propuso una acción no válida al preparar el chat.");
                    }
                }
                case DELIVER_CONTEXT -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    if(!contextInitialized){
                        if(o.state!=TransportState.NORMAL_CHAT||!o.composerReady){
                            retryAdaptive("Gemini todavía no expone un compositor normal verificable.");return;
                        }
                        transition(profile,SessionStage.CONTEXT_INJECTING);
                        contextBaselineText=transport.collectConversationText(r);
                        LearningLedger ledger=learning.load(profile.id);
                        contextPayload=prompts.contextCapsule(profile,ledger,sessionId,
                                "LIVE".equals(sessionMode));
                        contextInitialized=true;
                    }

                    boolean prepared=transport.isContextPrepared(r,contextPayload);
                    String nowText=transport.collectConversationText(r);
                    boolean delivered=contextWriteIssued&&!prepared
                            && transport.hasPostedUserMessage(r,contextPayload);

                    if(prepared&&contextWriteIssued&&!contextWriteVerified){
                        artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_CONTEXT,
                                TransportState.NORMAL_CHAT,ArtemisFlashAgent.Action.WRITE_CONTEXT);
                        contextWriteVerified=true;
                    }

                    if(delivered){
                        if(contextSubmitPending){
                            artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_CONTEXT,
                                    TransportState.NORMAL_CHAT,ArtemisFlashAgent.Action.SUBMIT_CONTEXT);
                        }else{
                            compatibility.recordFailure(profile.id,
                                    "context-submit-observed-without-automation-confirmation");
                        }
                        contextSubmitPending=false;moveStart(StartPhase.WAIT_CONTEXT_READY);return;
                    }

                    if(contextWriteIssued&&!prepared){
                        contextMissingPasses++;
                        if(contextMissingPasses>=3){
                            artemisStartAgent.actionFailed();contextWriteIssued=false;
                            contextWriteVerified=false;contextMissingPasses=0;
                        }
                    }else contextMissingPasses=0;

                    ArtemisFlashAgent.Action action=artemisStartAgent.nextContext(
                            o,prepared,false,contextWriteIssued,contextSubmitAttempts,false);
                    switch(action){
                        case WRITE_CONTEXT -> {
                            if(transport.writeContext(r,contextPayload)){
                                contextWriteIssued=true;contextMissingPasses=0;schedule(0);return;
                            }
                            artemisStartAgent.actionFailed();
                            if(++contextSubmitAttempts>=3){
                                failUpdate("Artemis no pudo escribir el contexto en Gemini.");return;
                            }
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case SUBMIT_CONTEXT -> {
                            contextSubmitAttempts++;
                            if(transport.submitPreparedContext(r)){contextSubmitPending=true;schedule(0);return;}
                            artemisStartAgent.actionFailed();
                            if(contextSubmitAttempts>=3){
                                failUpdate("Artemis detectó el contexto escrito pero no pudo enviarlo.");return;
                            }
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case BACK -> {
                            if(performGlobalAction(GLOBAL_ACTION_BACK)){
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_CONTEXT,o.state,action);
                                schedule(0);return;
                            }
                            artemisStartAgent.actionFailed();
                            failUpdate("Artemis no pudo volver al chat antes de entregar el contexto.");
                        }
                        case WAIT -> schedule(RESPONSE_OBSERVE_FALLBACK_MS);
                        case COMPLETE_CONTEXT -> moveStart(StartPhase.WAIT_CONTEXT_READY);
                        case FAIL_CLOSED -> failUpdate("Artemis no pudo verificar una ruta segura para entregar el contexto.");
                        default -> failUpdate("Artemis propuso una acción no válida durante la entrega de contexto.");
                    }
                }
                case WAIT_CONTEXT_READY -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    if(o.state!=TransportState.NORMAL_CHAT){
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    if("CHAT".equals(sessionMode)&&!o.composerReady){
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    if("LIVE".equals(sessionMode)&&!o.liveAvailable
                            && transport.responseInProgress(r)){
                        if(overlay!=null)overlay.showWorking(profile.label,"Esperando a que Gemini termine…");
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    journal.baselineText(profile.id,transport.collectConversationText(r));
                    transition(profile,SessionStage.CONTEXT_READY);
                    moveStart(StartPhase.ACTIVATE_SESSION);
                }
                case ACTIVATE_SESSION -> {
                    if("CHAT".equals(sessionMode)){
                        artemisStartAgent.complete();
                        transition(profile,SessionStage.CHAT_ACTIVE);rememberActive(profile);showOverlay(profile);
                        mode=Mode.WAIT;retries=0;return;
                    }
                    transition(profile,SessionStage.LIVE_STARTING);
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    ArtemisFlashAgent.Action action=artemisStartAgent.nextLive(o,liveExploreForwardExhausted);
                    switch(action){
                        case SCROLL_FORWARD -> {
                            if(transport.exploreConversationForward(r)){
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_LIVE,o.state,action);
                                liveExploreStalls=0;liveExploreForwardExhausted=false;
                                if(++liveExploreMoves>MAX_LIVE_EXPLORE_MOVES){failUpdate("Artemis recorrió la conversación sin encontrar Live.");return;}
                                schedule(180L);return;
                            }
                            artemisStartAgent.actionFailed();
                            liveExploreStalls++;if(liveExploreStalls>=2)liveExploreForwardExhausted=true;
                            if(liveExploreStalls>=MAX_LIVE_EXPLORE_STALLS){failUpdate("Artemis agotó la exploración segura sin encontrar Live.");return;}
                            schedule(RESPONSE_OBSERVE_FALLBACK_MS);
                        }
                        case SCROLL_BACKWARD -> {
                            if(transport.exploreConversationBackward(r)){
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_LIVE,o.state,action);
                                liveExploreStalls=0;liveExploreForwardExhausted=false;
                                if(++liveExploreMoves>MAX_LIVE_EXPLORE_MOVES){failUpdate("Artemis recorrió la conversación sin encontrar Live.");return;}
                                schedule(180L);return;
                            }
                            artemisStartAgent.actionFailed();
                            liveExploreStalls++;liveExploreForwardExhausted=false;
                            if(liveExploreStalls>=MAX_LIVE_EXPLORE_STALLS){failUpdate("Artemis no pudo revelar Live después de explorar el chat.");return;}
                            schedule(RESPONSE_OBSERVE_FALLBACK_MS);
                        }
                        case START_LIVE -> {
                            if(transport.startLive(r)){
                                liveStartPending=true;
                                artemisStartAgent.actionSucceeded(ArtemisFlashAgent.PHASE_LIVE,o.state,action);
                                schedule(0);return;
                            }
                            artemisStartAgent.actionFailed();
                            if(++retries>=3){failUpdate("Artemis detectó Live pero no pudo activarlo.");return;}
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case COMPLETE_LIVE -> {
                            artemisStartAgent.complete();
                            transition(profile,SessionStage.LIVE_ACTIVE);rememberActive(profile);showOverlay(profile);
                            mode=Mode.WAIT;retries=0;
                        }
                        case WAIT -> schedule(RESPONSE_OBSERVE_FALLBACK_MS);
                        case FAIL_CLOSED -> {
                            artemisStartAgent.actionFailed();
                            if(++retries>=3){failUpdate("Artemis no pudo resolver una ruta segura hacia Gemini Live.");return;}
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        default -> {artemisStartAgent.actionFailed();failUpdate("Artemis propuso una acción no válida al iniciar Gemini Live.");}
                    }
                }
            }
        }

        private void pumpWait(){
            AccessibilityNodeInfo r=root();
            if(!transport.isGeminiSurface(r)){liveExitObservedAtMs=0L;schedule(OBSERVE_FALLBACK_MS);return;}
            if("CHAT".equals(sessionMode))return;
            TransportObservation o=GeminiStateObserver.observe(r);
            if(o.state==TransportState.LIVE_ACTIVE){
                liveExitObservedAtMs=0L;
                return;
            }
            if(o.state==TransportState.NORMAL_CHAT){
                long now=System.currentTimeMillis();
                if(liveExitObservedAtMs==0L){
                    liveExitObservedAtMs=now;
                    if(overlay!=null)overlay.showWorking(profile.label,"Detectando fin de Live…");
                    schedule(OBSERVE_FALLBACK_MS);return;
                }
                if(now-liveExitObservedAtMs>=LIVE_EXIT_CONFIRM_MS){
                    beginClose();return;
                }
                schedule(OBSERVE_FALLBACK_MS);return;
            }
            liveExitObservedAtMs=0L;
            schedule(OBSERVE_FALLBACK_MS);
        }

        private void pumpClose(){
            if(sessionId==null||sessionId.isEmpty()){fail("No existe SESSION_ID para cerrar esta sesión.");return;}
            switch(closePhase){
                case INIT -> {
                    transition(profile,SessionStage.CLOSING_SESSION);
                    artemisCloseAgent=new ArtemisFlashAgent(AleyonAccessibilityService.this,artemisRoutineKey("close-session"));
                    AccessibilityNodeInfo r=root();
                    if(transport.isGeminiSurface(r)){
                        moveClose(ClosePhase.END_LIVE);return;
                    }
                    if(!closeLaunchIssued){closeLaunchIssued=true;launchGemini();}
                    schedule(OBSERVE_FALLBACK_MS);
                }
                case END_LIVE -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    if(o.state==TransportState.NORMAL_CHAT){
                        if(endLivePending){
                            artemisCloseAgent.actionSucceeded(ArtemisFlashAgent.PHASE_END_LIVE,
                                    TransportState.LIVE_ACTIVE,ArtemisFlashAgent.Action.END_LIVE);
                            endLivePending=false;
                        }
                        transition(profile,SessionStage.WAITING_TRANSCRIPT);
                        if(overlay!=null)overlay.showWorking(profile.label,"Guardando conversación…");
                        moveClose(ClosePhase.STABILIZE_TRANSCRIPT);return;
                    }
                    ArtemisFlashAgent.Action action=artemisCloseAgent.nextEndLive(o);
                    switch(action){
                        case END_LIVE -> {
                            if(transport.endLive(r)){endLivePending=true;schedule(0);return;}
                            artemisCloseAgent.actionFailed();
                            if(++retries>=3){failUpdate("Artemis detectó Live activo pero no pudo cerrarlo.");return;}
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case COMPLETE_END_LIVE -> {
                            if(endLivePending){
                                artemisCloseAgent.actionSucceeded(ArtemisFlashAgent.PHASE_END_LIVE,
                                        TransportState.LIVE_ACTIVE,ArtemisFlashAgent.Action.END_LIVE);
                            }
                            transition(profile,SessionStage.WAITING_TRANSCRIPT);
                            moveClose(ClosePhase.STABILIZE_TRANSCRIPT);
                        }
                        case WAIT -> schedule(OBSERVE_FALLBACK_MS);
                        case FAIL_CLOSED -> {
                            artemisCloseAgent.actionFailed();retryAdaptive("Artemis no pudo verificar el final de Live.");
                        }
                        default -> failUpdate("Artemis propuso una acción no válida al cerrar Live.");
                    }
                }
                case STABILIZE_TRANSCRIPT -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    if(o.state!=TransportState.NORMAL_CHAT||!transport.hasComposer(r)){
                        schedule(OBSERVE_FALLBACK_MS);return;
                    }
                    String current=transport.collectConversationText(r);
                    long nowMs=System.currentTimeMillis();
                    if(postLiveChatObservedAtMs==0L)postLiveChatObservedAtMs=nowMs;
                    if(!current.equals(stableTranscriptSnapshot)){
                        stableTranscriptSnapshot=current;stableTranscriptSinceMs=nowMs;
                    }else if(stableTranscriptSinceMs==0L)stableTranscriptSinceMs=nowMs;
                    if(transport.responseInProgress(r)){
                        stableTranscriptSinceMs=nowMs;
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    boolean quietLongEnough=nowMs-stableTranscriptSinceMs>=POST_LIVE_QUIET_MS;
                    if(!quietLongEnough){schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;}
                    sessionEvidenceText=SessionTextDelta.delta(journal.baselineText(profile.id),current);
                    if(sessionEvidenceText.trim().length()<20){finishReady();return;}
                    debriefConversationAnchor=sessionEvidenceText;
                    conversationDriftNotified=false;
                    if(overlay!=null)overlay.showWorking(profile.label,"Pidiendo resumen a Gemini…");
                    moveClose(ClosePhase.DELIVER_DEBRIEF);
                }
                case DELIVER_DEBRIEF -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    String visibleNow=transport.collectConversationText(r);
                    if(o.state!=TransportState.NORMAL_CHAT
                            ||!SessionTextDelta.containsConversationEvidence(debriefConversationAnchor,visibleNow)){
                        if(!conversationDriftNotified&&overlay!=null){
                            conversationDriftNotified=true;
                            overlay.showWorking(profile.label,"Vuelve al chat de esta sesión…");
                        }
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    if(conversationDriftNotified){
                        conversationDriftNotified=false;
                        if(overlay!=null)overlay.showWorking(profile.label,"Pidiendo resumen a Gemini…");
                    }
                    if(!debriefInitialized){
                        if(!o.composerReady){schedule(OBSERVE_FALLBACK_MS);return;}
                        transition(profile,SessionStage.ANALYZING);
                        debriefBaselineText=visibleNow;
                        debriefPayload=prompts.sessionDebrief(profile);
                        debriefInitialized=true;
                    }
                    boolean prepared=transport.isContextPrepared(r,debriefPayload);
                    String now=transport.collectConversationText(r);
                    boolean delivered=debriefWriteIssued&&!prepared
                            && transport.hasPostedUserMessage(r,debriefPayload);
                    if(prepared&&debriefWriteIssued&&!debriefWriteVerified){
                        artemisCloseAgent.actionSucceeded(ArtemisFlashAgent.PHASE_DEBRIEF,
                                TransportState.NORMAL_CHAT,ArtemisFlashAgent.Action.WRITE_CONTEXT);
                        debriefWriteVerified=true;
                    }
                    if(delivered){
                        if(debriefSubmitPending){
                            artemisCloseAgent.actionSucceeded(ArtemisFlashAgent.PHASE_DEBRIEF,
                                    TransportState.NORMAL_CHAT,ArtemisFlashAgent.Action.SUBMIT_CONTEXT);
                        }
                        artemisCloseAgent.complete();
                        debriefResponseBaselineText=now;debriefLastObservedText=now;
                        debriefWaitStartedAtMs=System.currentTimeMillis();debriefLastProgressAtMs=debriefWaitStartedAtMs;
                        debriefSlowNoticeShown=false;
                        if(overlay!=null)overlay.showWorking(profile.label,"Esperando resumen de Gemini…");
                        moveClose(ClosePhase.WAIT_DEBRIEF);return;
                    }
                    if(debriefWriteIssued&&!prepared){
                        debriefMissingPasses++;
                        if(debriefMissingPasses>=3){
                            artemisCloseAgent.actionFailed();debriefWriteIssued=false;
                            debriefWriteVerified=false;debriefMissingPasses=0;
                        }
                    }else debriefMissingPasses=0;
                    ArtemisFlashAgent.Action action=artemisCloseAgent.nextContext(
                            o,prepared,false,debriefWriteIssued,debriefSubmitAttempts,true);
                    switch(action){
                        case WRITE_CONTEXT -> {
                            if(transport.writeContext(r,debriefPayload)){
                                debriefWriteIssued=true;debriefMissingPasses=0;schedule(0);return;
                            }
                            artemisCloseAgent.actionFailed();
                            if(++debriefSubmitAttempts>=3){failUpdate("Artemis no pudo preparar el debrief en Gemini.");return;}
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case SUBMIT_CONTEXT -> {
                            debriefSubmitAttempts++;
                            if(transport.submitPreparedContext(r)){debriefSubmitPending=true;schedule(0);return;}
                            artemisCloseAgent.actionFailed();
                            if(debriefSubmitAttempts>=3){failUpdate("Artemis no pudo enviar el debrief a Gemini.");return;}
                            schedule(OBSERVE_FALLBACK_MS);
                        }
                        case WAIT -> schedule(RESPONSE_OBSERVE_FALLBACK_MS);
                        case FAIL_CLOSED -> failUpdate("Artemis no pudo verificar una ruta segura para solicitar el debrief.");
                        default -> failUpdate("Artemis propuso una acción no válida durante el debrief.");
                    }
                }
                case WAIT_DEBRIEF -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r);
                    if(o.state!=TransportState.NORMAL_CHAT){schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;}
                    String all=transport.collectConversationText(r);long nowMs=System.currentTimeMillis();
                    boolean sameSession=SessionTextDelta.containsConversationEvidence(debriefConversationAnchor,all)
                            ||SessionTextDelta.containsConversationEvidence(debriefResponseBaselineText,all,2);
                    if(!sameSession){
                        if(!conversationDriftNotified&&overlay!=null){
                            conversationDriftNotified=true;
                            overlay.showWorking(profile.label,"Vuelve al chat de esta sesión…");
                        }
                        debriefLastProgressAtMs=nowMs;
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    if(conversationDriftNotified){
                        conversationDriftNotified=false;
                        if(overlay!=null)overlay.showWorking(profile.label,"Esperando resumen de Gemini…");
                    }
                    long waited=debriefWaitStartedAtMs<=0L?0L:nowMs-debriefWaitStartedAtMs;
                    if(!all.equals(debriefLastObservedText)){
                        debriefLastObservedText=all;debriefLastProgressAtMs=nowMs;
                        if(waited>=DEBRIEF_SLOW_NOTICE_MS&&overlay!=null)overlay.showWorking(profile.label,"Recibiendo resumen de Gemini…");
                    }else if(debriefLastProgressAtMs==0L)debriefLastProgressAtMs=nowMs;
                    String debriefDelta=SessionTextDelta.delta(debriefResponseBaselineText,all);
                    SessionReportParser.Report report=SessionReportParser.parseDebrief(debriefDelta);
                    if(report==null){
                        boolean inProgress=transport.responseInProgress(r);
                        if(inProgress&&transport.hasRespondNow(r)
                                &&waited>=DEBRIEF_RESPOND_NOW_AFTER_MS&&!debriefRespondNowIssued){
                            debriefRespondNowIssued=transport.respondNow(r);
                            if(debriefRespondNowIssued&&overlay!=null)
                                overlay.showWorking(profile.label,"Pidiendo a Gemini que responda…");
                            schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                        }

                        long quietFor=nowMs-debriefLastProgressAtMs;
                        String invalidDelta=debriefDelta==null?"":debriefDelta.trim();
                        boolean invalidResponseSettled=invalidDelta.length()>=80
                                &&quietFor>=DEBRIEF_INVALID_RESPONSE_QUIET_MS;
                        boolean silentTooLong=invalidDelta.isEmpty()
                                &&waited>=DEBRIEF_SILENT_RETRY_MS
                                &&quietFor>=DEBRIEF_SILENT_RETRY_MS;

                        if(!inProgress&&!debriefRetryIssued&&o.composerReady
                                &&(invalidResponseSettled||silentTooLong)){
                            if(debriefRetryPayload.isEmpty())
                                debriefRetryPayload=prompts.sessionDebriefRetry(profile,sessionEvidenceText);

                            if(transport.hasPostedUserMessage(r,debriefRetryPayload)){
                                debriefRetryIssued=true;debriefRetrySubmitPending=false;
                                debriefResponseBaselineText=all;debriefLastObservedText=all;
                                debriefWaitStartedAtMs=nowMs;debriefLastProgressAtMs=nowMs;
                                debriefSlowNoticeShown=false;debriefRespondNowIssued=false;
                                if(overlay!=null)overlay.showWorking(profile.label,"Esperando el cierre corregido…");
                                schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                            }

                            boolean retryPrepared=transport.isContextPrepared(r,debriefRetryPayload);
                            if(!debriefRetryWriteIssued&&!retryPrepared){
                                debriefRetryWriteAttempts++;
                                if(transport.writeContext(r,debriefRetryPayload)){
                                    debriefRetryWriteIssued=true;
                                    if(overlay!=null)overlay.showWorking(profile.label,"Reintentando el cierre una vez…");
                                }else if(debriefRetryWriteAttempts>=3){
                                    debriefRetryIssued=true;
                                }
                                schedule(OBSERVE_FALLBACK_MS);return;
                            }
                            if(retryPrepared){
                                debriefRetrySubmitAttempts++;
                                if(transport.submitPreparedContext(r)){
                                    debriefRetrySubmitPending=true;
                                }else if(debriefRetrySubmitAttempts>=3){
                                    debriefRetryIssued=true;transport.clearComposer(r);
                                }
                                schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                            }
                            schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                        }

                        if(waited>=DEBRIEF_SLOW_NOTICE_MS&&!debriefSlowNoticeShown){
                            debriefSlowNoticeShown=true;if(overlay!=null)overlay.showWorking(profile.label,"Gemini tarda; sigo esperando…");
                        }
                        if(nowMs-debriefLastProgressAtMs>=DEBRIEF_IDLE_TIMEOUT_MS){commitTranscriptFallback();return;}
                        schedule(RESPONSE_OBSERVE_FALLBACK_MS);return;
                    }
                    commitCloseReport(report);
                }
            }
        }

        private void commitCloseReport(SessionReportParser.Report report){
            transition(profile,SessionStage.COMMITTING);
            if(overlay!=null)overlay.showWorking(profile.label,"Guardando progreso…");
            if(!learning.commitVerified(profile.id,report,sessionId,sessionEvidenceText)){
                fail("No pude verificar el commit local de la memoria de aprendizaje.");return;
            }
            String summary=report.summary;
            if(!report.feedback.isEmpty())summary=(summary.isEmpty()?"":summary+"\n\n")+"Consejo: "+report.feedback;
            if(!report.nextObjective.isEmpty())summary=(summary.isEmpty()?"":summary+"\n")+"Próximo objetivo: "+report.nextObjective;
            journal.appendCloseSummary(profile.id,summary);
            journal.sessionHints(profile.id,report.nextObjective,"");
            try{profile.sessions++;journal.saveProfile(profile);}catch(Exception ignored){}
            NotificationHelper.postSessionClosed(AleyonAccessibilityService.this,profile.id,profile.label,summary);
            finishReady();
        }

        private void commitTranscriptFallback(){
            SessionReportParser.Report report=new SessionReportParser.Report();
            report.summary="Sesión guardada sin resumen de Gemini; se conservó la evidencia local.";
            LearningLedger previous=learning.load(profile.id);
            report.nextObjective=previous.getNextObjective();
            String evidence=sessionEvidenceText==null?"":sessionEvidenceText.trim();
            if(evidence.length()>600)evidence=evidence.substring(0,600);
            if(!evidence.isEmpty()){
                report.events.add(new LearningEvent("session-transcript","evidencia-de-sesion",
                        "observed",evidence,System.currentTimeMillis()));
            }
            recordDiagnostic(profile,"DEBRIEF_TIMEOUT",
                    "Gemini no devolvió un resumen verificable; evidencia local preservada.");
            commitCloseReport(report);
        }

        private void finishReady(){
            finished=true;journal.clearError(profile.id);
            journal.stage(profile.id,SessionStage.READY);journal.clearActiveSession(profile.id);clearActive(profile);
            if(overlay!=null)overlay.hide();launchAleyon();
        }
        private void failUpdate(String msg){
            recordDiagnostic(profile,"APP_UPDATE_REQUIRED",msg);
            
            journal.appendErrorHistory(profile.id,msg,SessionStage.APP_UPDATE_REQUIRED);
            journal.error(profile.id,msg,SessionStage.APP_UPDATE_REQUIRED);finished=true;
            if(overlay!=null)overlay.markNeedsAttention();launchAleyon();
        }

        private void fail(String msg){
            recordDiagnostic(profile,"FAIL",msg);
            journal.appendErrorHistory(profile.id,msg,SessionStage.ERROR);journal.error(profile.id,msg,SessionStage.ERROR);
            finished=true;if(overlay!=null)overlay.markNeedsAttention();launchAleyon();
        }
    }
}
