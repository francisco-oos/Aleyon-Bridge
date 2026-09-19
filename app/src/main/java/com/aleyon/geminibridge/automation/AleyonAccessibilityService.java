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
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.RecoveryPlanner;
import com.aleyon.geminibridge.core.SessionReportParser;
import com.aleyon.geminibridge.core.SessionStage;
import com.aleyon.geminibridge.core.SessionTextDelta;
import com.aleyon.geminibridge.core.TransportState;
import com.aleyon.geminibridge.transport.CompatibilityMemory;
import com.aleyon.geminibridge.transport.ConversationRegistry;
import com.aleyon.geminibridge.transport.GeminiConversationTransport;
import com.aleyon.geminibridge.transport.GeminiStateObserver;
import com.aleyon.geminibridge.transport.SessionIntent;
import com.aleyon.geminibridge.transport.TransportObservation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Aleyon Bridge runtime on the field-tested alpha11 Android/Gemini layer.
 *
 * Aleyon owns profile, continuity and learning evidence. Gemini remains the
 * native cognitive/Live/multimodal surface. Each session opens a normal
 * canonical Gemini conversation, injects bounded Aleyon context, runs Chat/Live,
 * then commits learning back to Aleyon. Provider history is useful but never
 * required for continuity because local memory remains authoritative.
 */
public final class AleyonAccessibilityService extends AccessibilityService
        implements OverlayController.Listener {

    public static final String GEMINI_PACKAGE=GeminiUi.GEMINI_APP_PACKAGE;
    public static final String GEMINI_HOST_PACKAGE=GeminiUi.GEMINI_GOOGLE_HOST_PACKAGE;
    private static final long GOOGLE_HOST_VERIFICATION_LEASE_MS=8_000L;
    private static final long STEP_DELAY_MS=320L;
    private static final long RESPONSE_DELAY_MS=650L;
    private static final long TRANSCRIPT_SETTLE_MS=1200L;
    private static final int MAX_UI_RETRIES=32;
    private static final int MAX_RESPONSE_RETRIES=80;
    private static final int MAX_ROUTE_REPLANS=8;
    private static final long MAX_START_RUNTIME_MS=180_000L;

    private static AleyonAccessibilityService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SessionJournal journal;
    private LearningStore learning;
    private PromptRepository prompts;
    private OverlayController overlay;
    private DiagnosticsRecorder diagnostics;
    private ConversationRegistry conversations;
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
        diagnostics=new DiagnosticsRecorder(this);conversations=new ConversationRegistry(this);
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
        if(r!=null&&!r.finished){r.beginClose();return;}
        ProfileSpec p=activeOverlayProfile();
        if(p!=null)startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));
    }

    @Override public void onRecoverRequested(){
        ProfileSpec p=activeOverlayProfile();if(p!=null)startRecovery(p);
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
            case ANALYZING -> "Resumiendo";
            case RECOVERING -> "Recuperando";
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
                    case RECOVER -> startRecovery(p);
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
            try{report.put("schema","aleyon-gemini-passive-probe-v9").put("version","0.5.0-alpha2")
                    .put("probeId",probeId).put("readOnly",true).put("sampleCount",samples.length()).put("samples",samples);}catch(Exception ignored){}
            getSharedPreferences("aleyon_probe",MODE_PRIVATE).edit().putString("last_probe",report.toString())
                    .putLong("last_probe_ts",System.currentTimeMillis()).putString("last_probe_id",probeId).apply();
            passiveProbeInProgress=false;launchAleyon();handler.postDelayed(this::kick,250L);
        },4300L);
    }

    /** Explicit START always creates a fresh local session transaction; provider chat may be reused. */
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
        journal.clearError(p.id);journal.clearRecoverableStage(p.id);journal.clearActiveSession(p.id);
        journal.stage(p.id,SessionStage.READY);clearActive(p);if(overlay!=null)overlay.hide();
    }

    private void startRecovery(ProfileSpec p){
        SessionStage previous=journal.stage(p.id);
        journal.stage(p.id,SessionStage.RECOVERING);launchGemini();
        handler.postDelayed(()->{
            boolean live=transport.isLiveActive(resolveGeminiRoot());
            RecoveryPlanner.RecoveryAction action=RecoveryPlanner.reconcile(previous,journal.recoverableStage(p.id),live);
            switch(action){
                case NONE -> finishReadyOutsideRunner(p);
                case RETRY_START -> startRunner("CHAT".equals(journal.activeSessionMode(p.id))?Mode.START_CHAT:Mode.START_LIVE,p,journal.activeSessionMode(p.id));
                case RESTORE_LIVE_OVERLAY -> {journal.stage(p.id,SessionStage.LIVE_ACTIVE);rememberActive(p);showOverlay(p);startWaitRunner(p,"LIVE");}
                case RESTORE_CHAT_OVERLAY -> {journal.stage(p.id,SessionStage.CHAT_ACTIVE);rememberActive(p);showOverlay(p);startWaitRunner(p,"CHAT");}
                case FINISH_CLOSE -> {
                    if(journal.activeSessionId(p.id).isEmpty())failWithoutRunner(p,"No existe SESSION_ID recuperable para cerrar con evidencia.",SessionStage.USER_ACTION_REQUIRED);
                    else startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));
                }
                case ASK_USER -> failWithoutRunner(p,"La última ejecución quedó en un estado ambiguo. Abre Gemini o usa Diagnóstico y vuelve a Recuperar.",SessionStage.AMBIGUOUS_USER_REQUIRED);
                case REQUIRE_BRIDGE_UPDATE -> failWithoutRunner(p,"Gemini cambió de interfaz y esta variante requiere una nueva regla de compatibilidad verificada.",SessionStage.APP_UPDATE_REQUIRED);
            }
        },900L);
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
            case CLOSE -> "Guardando sesión…";
        };
    }

    private void failWithoutRunner(ProfileSpec p,String msg,SessionStage terminal){
        if(p!=null){SessionStage old=journal.stage(p.id);journal.recoverableStage(p.id,old);journal.appendErrorHistory(p.id,msg,terminal);journal.error(p.id,msg,terminal);rememberActive(p);if(overlay!=null){overlay.show(p.label,"","");overlay.markNeedsAttention();}}
        launchAleyon();
    }

    private void finishReadyOutsideRunner(ProfileSpec p){
        journal.clearError(p.id);journal.clearRecoverableStage(p.id);journal.stage(p.id,SessionStage.READY);
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
        return capability+"|"+packageVersion(GEMINI_PACKAGE)+"|"
                +packageVersion(GEMINI_HOST_PACKAGE);
    }
    private String artemisRoutineKey(boolean registryKnown){
        return artemisRoutineKey("canonical-nav")+"|"+(registryKnown?"known":"migration");
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

    private final class Runner {
        private Mode mode;
        private final ProfileSpec profile;
        private String sessionMode;
        private String sessionId;
        private int step,retries,liveMissingChecks,searchMisses,routeReplans;
        private boolean finished,waitingForUserConsent,rebuildingConversation,searchAttempted;
        private boolean searchQueryIssued,openingCanonical;
        private ArtemisFlashAgent artemisAgent,artemisContextAgent,artemisLiveAgent;
        private boolean contextInitialized,contextWriteIssued,contextWriteVerified,contextSubmitPending,liveStartPending;
        private int contextSubmitAttempts,contextMissingPasses;
        private String contextPayload="";
        private final long runnerStartedAtMs=System.currentTimeMillis();
        private int transcriptSettlePasses;
        private String sessionEvidenceText="", debriefBaselineText="", contextBaselineText="";
        private String canonicalTitle="",canonicalRoute="";
        private final Runnable pumpRunnable=this::pump;

        Runner(Mode mode,ProfileSpec p,String sessionMode){
            this.mode=mode;this.profile=p;this.sessionMode=sessionMode==null?"LIVE":sessionMode;
            this.sessionId=journal.activeSessionId(p.id);
        }

        void schedule(long delay){if(finished)return;handler.removeCallbacks(pumpRunnable);handler.postDelayed(pumpRunnable,delay);}
        void abortWithoutCommit(){finished=true;handler.removeCallbacks(pumpRunnable);}
        void beginClose(){
            SessionStage current=journal.stage(profile.id);
            SessionStage recoverable=journal.recoverableStage(profile.id);
            boolean committedPath=isCloseableActiveStage(current)||isCloseableActiveStage(recoverable);
            if(!committedPath){cancelToReady();return;}
            mode=Mode.CLOSE;step=0;retries=0;transcriptSettlePasses=0;
            if(overlay!=null)overlay.showWorking(profile.label,phaseLabel(Mode.CLOSE));schedule(0);
        }
        private boolean isCloseableActiveStage(SessionStage s){return isCloseableStage(s);}
        private void cancelToReady(){
            finished=true;handler.removeCallbacks(pumpRunnable);
            try{AccessibilityNodeInfo r=root();if(r!=null)transport.clearComposer(r);}catch(Exception ignored){}
            journal.clearError(profile.id);journal.clearRecoverableStage(profile.id);journal.stage(profile.id,SessionStage.READY);
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
            try{
                AccessibilityNodeInfo blocker=root();
                if(GeminiUi.isBlockingConsentDialog(blocker)){
                    // Security/consent surfaces must receive untouched input.
                    // Android may deliberately reject permission-button taps
                    // while an accessibility overlay is visible (tapjacking
                    // protection), so remove every Aleyon overlay while the
                    // human makes the decision. We never click it for them.
                    if(!waitingForUserConsent){waitingForUserConsent=true;if(overlay!=null)overlay.hide();}
                    schedule(1200);return;
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
        private void advance(){step++;retries=0;schedule(STEP_DELAY_MS);}
        private void go(int n){step=n;retries=0;schedule(STEP_DELAY_MS);}
        private boolean retry(String reason,boolean response){
            retries++;int max=response?MAX_RESPONSE_RETRIES:MAX_UI_RETRIES;
            if(retries>max){fail(reason);return false;}schedule(response?RESPONSE_DELAY_MS:STEP_DELAY_MS);return true;
        }
        private boolean retryMarker(String reason){transport.scrollConversation(root());return retry(reason,true);}
        private boolean replan(String reason,int nextStep){
            compatibility.recordFailure(profile.id,reason);
            if(++routeReplans>MAX_ROUTE_REPLANS){
                failUpdate(reason+" Se agotó el presupuesto de recuperación semántica.");
                return false;
            }
            go(nextStep);return true;
        }
        private String newSessionId(){return "session-"+UUID.randomUUID().toString().replace("-","").substring(0,16);}

        private TransportObservation observe(){
            return GeminiStateObserver.observe(root(),canonicalTitle);
        }

        private boolean retryAdaptive(String reason){
            TransportObservation o=observe();
            compatibility.recordFailure(profile.id,reason+" | "+o.state+" | "+o.evidence);
            retries++;
            if(retries>MAX_UI_RETRIES){
                failUpdate(reason+" El transporte semántico no pudo recuperar esta variante de Gemini.");
                return false;
            }
            schedule(STEP_DELAY_MS);return true;
        }

        private void pumpStart(){
            if(!profile.canStart()){fail("Perfil incompleto para iniciar sesión.");return;}
            switch(step){
                case 0 -> {
                    sessionMode=mode==Mode.START_CHAT?"CHAT":"LIVE";
                    if(sessionId==null||sessionId.isEmpty())sessionId=newSessionId();
                    canonicalTitle=conversations.title(profile);
                    artemisAgent=new ArtemisFlashAgent(AleyonAccessibilityService.this,
                            artemisRoutineKey(conversations.isKnown(profile.id)));
                    artemisContextAgent=new ArtemisFlashAgent(AleyonAccessibilityService.this,
                            artemisRoutineKey("context-delivery"));
                    artemisLiveAgent=new ArtemisFlashAgent(AleyonAccessibilityService.this,
                            artemisRoutineKey("live-start"));
                    journal.activeSession(profile.id,sessionId,sessionMode);
                    transition(profile,SessionStage.OPENING_SESSION_CHAT);
                    launchGemini();advance();
                }
                case 1 -> {
                    TransportObservation o=observe();
                    if(o.state==TransportState.UNAVAILABLE){retry("Gemini no expuso una superficie verificable.",false);return;}
                    // A manually-opened Gemini Live session is an input state, not a catastrophe.
                    // Return to chat chrome and normalize from there before touching any learner context.
                    if(o.state==TransportState.LIVE_ACTIVE){
                        performGlobalAction(GLOBAL_ACTION_BACK);schedule(STEP_DELAY_MS);return;
                    }
                    if(o.state==TransportState.UNKNOWN){retryAdaptive("Gemini quedó en un estado inicial desconocido.");return;}
                    advance();
                }
                case 2 -> {
                    AccessibilityNodeInfo r=root();
                    TransportObservation o=GeminiStateObserver.observe(r,canonicalTitle);
                    ArtemisFlashAgent.Action action=artemisAgent.next(
                            o,
                            conversations.isKnown(profile.id),
                            searchAttempted,
                            searchQueryIssued,
                            searchMisses,
                            openingCanonical,
                            rebuildingConversation);

                    switch(action){
                        case WAIT -> {
                            if(o.state==TransportState.CONVERSATION_SEARCH && searchQueryIssued){
                                searchMisses++;
                                schedule(RESPONSE_DELAY_MS);
                            }else{
                                schedule(STEP_DELAY_MS);
                            }
                        }
                        case BACK -> {
                            if(!performGlobalAction(GLOBAL_ACTION_BACK)){
                                artemisAgent.actionFailed();
                                retryAdaptive("Gemini no aceptó volver desde el estado observado.");return;
                            }
                            artemisAgent.actionSucceeded(o.state,action);
                            routeReplans++;
                            if(routeReplans>MAX_ROUTE_REPLANS){
                                failUpdate("El transporte agotó su presupuesto de recuperación semántica.");return;
                            }
                            schedule(STEP_DELAY_MS);
                        }
                        case OPEN_CONVERSATION_LIST -> {
                            if(transport.openConversationList(r)){
                                artemisAgent.actionSucceeded(o.state,action);
                                retries=0;schedule(STEP_DELAY_MS);return;
                            }
                            artemisAgent.actionFailed();
                            retryAdaptive("No pude abrir de forma semántica la lista de conversaciones de Gemini.");
                        }
                        case OPEN_VISIBLE_CANONICAL -> {
                            if(transport.openCanonicalConversation(r,canonicalTitle)){
                                artemisAgent.actionSucceeded(o.state,action);
                                openingCanonical=true;
                                rebuildingConversation=false;
                                canonicalRoute=artemisAgent.isReplaying()?"artemis-replay":"artemis-learn";
                                retries=0;schedule(STEP_DELAY_MS);return;
                            }
                            artemisAgent.actionFailed();
                            retryAdaptive("La conversación canónica estaba visible pero no pudo abrirse.");
                        }
                        case OPEN_SEARCH -> {
                            if(transport.openConversationSearch(r)){
                                artemisAgent.actionSucceeded(o.state,action);
                                searchAttempted=true;searchQueryIssued=false;searchMisses=0;
                                retries=0;schedule(STEP_DELAY_MS);return;
                            }
                            artemisAgent.actionFailed();
                            retryAdaptive("Aleyon conocía el chat canónico, pero Gemini no expuso búsqueda verificable.");
                        }
                        case TYPE_SEARCH_QUERY -> {
                            if(transport.searchConversation(r,canonicalTitle)){
                                artemisAgent.actionSucceeded(o.state,action);
                                searchQueryIssued=true;searchMisses=0;
                                retries=0;schedule(RESPONSE_DELAY_MS);return;
                            }
                            artemisAgent.actionFailed();
                            retryAdaptive("No pude entregar la consulta al buscador semántico de conversaciones.");
                        }
                        case CREATE_NORMAL_CHAT -> {
                            conversations.markMissing(profile);
                            if(transport.createNormalConversation(r)){
                                artemisAgent.actionSucceeded(o.state,action);
                                rebuildingConversation=true;openingCanonical=false;
                                canonicalRoute=artemisAgent.isReplaying()?"artemis-replay":"artemis-learn";
                                retries=0;schedule(STEP_DELAY_MS);return;
                            }
                            artemisAgent.actionFailed();
                            retryAdaptive("No pude crear un chat normal seguro para reconstruir la continuidad local.");
                        }
                        case COMPLETE_REUSE -> {
                            artemisAgent.complete();
                            rebuildingConversation=false;openingCanonical=false;go(7);
                        }
                        case COMPLETE_REBUILD -> {
                            artemisAgent.complete();
                            rebuildingConversation=true;openingCanonical=false;go(7);
                        }
                        case FAIL_CLOSED -> {
                            compatibility.recordFailure(profile.id,
                                    "adaptive-navigation-unknown | "+o.state+" | "+o.evidence);
                            if(++routeReplans>MAX_ROUTE_REPLANS){
                                failUpdate("Gemini cambió a una superficie que Aleyon no puede verificar con seguridad.");return;
                            }
                            schedule(STEP_DELAY_MS);
                        }
                    }
                }
                case 7 -> {
                    AccessibilityNodeInfo r=root();
                    TransportObservation o=GeminiStateObserver.observe(r,canonicalTitle);
                    if(!contextInitialized){
                        if(o.state!=TransportState.NORMAL_CHAT||!o.composerReady){
                            retryAdaptive("La conversación seleccionada no llegó a un chat normal verificable.");return;
                        }
                        if(!rebuildingConversation){
                            conversations.markVerified(profile,false);
                            compatibility.recordSuccess(profile.id,canonicalRoute,o.evidence);
                        }
                        transition(profile,SessionStage.CONTEXT_INJECTING);
                        contextBaselineText=transport.collectConversationText(r);
                        LearningLedger ledger=learning.load(profile.id);
                        contextPayload=prompts.contextCapsule(profile,ledger,sessionId,
                                "LIVE".equals(sessionMode),rebuildingConversation);
                        contextInitialized=true;
                    }

                    boolean prepared=transport.isContextPrepared(r,contextPayload);
                    String nowText=transport.collectConversationText(r);
                    boolean delivered=contextWriteIssued&&!prepared
                            && SessionTextDelta.delta(contextBaselineText,nowText).trim().length()>=20;

                    if(prepared&&contextWriteIssued&&!contextWriteVerified){
                        artemisContextAgent.actionSucceeded(TransportState.NORMAL_CHAT,
                                ArtemisFlashAgent.Action.WRITE_CONTEXT);
                        contextWriteVerified=true;
                    }

                    if(delivered){
                        if(contextSubmitPending){
                            artemisContextAgent.actionSucceeded(TransportState.NORMAL_CHAT,
                                    ArtemisFlashAgent.Action.SUBMIT_CONTEXT);
                            artemisContextAgent.complete();
                        }else{
                            // Human/manual submit is accepted as the postcondition, but it
                            // is not promoted as proof that automation executed the click.
                            compatibility.recordFailure(profile.id,
                                    "context-submit-observed-without-automation-confirmation");
                        }
                        contextSubmitPending=false;
                        advance();return;
                    }

                    if(contextWriteIssued&&!prepared){
                        contextMissingPasses++;
                        if(contextMissingPasses>=3){
                            artemisContextAgent.actionFailed();
                            contextWriteIssued=false;contextWriteVerified=false;
                            contextMissingPasses=0;
                        }
                    }else contextMissingPasses=0;

                    ArtemisFlashAgent.Action action=artemisContextAgent.nextContext(
                            o,prepared,false,contextWriteIssued,contextSubmitAttempts);
                    switch(action){
                        case WRITE_CONTEXT -> {
                            if(transport.writeContext(r,contextPayload)){
                                contextWriteIssued=true;
                                contextMissingPasses=0;
                                schedule(160L);return;
                            }
                            artemisContextAgent.actionFailed();
                            if(++contextSubmitAttempts>=3){
                                failUpdate("Artemis no pudo escribir el contexto en un compositor verificable.");return;
                            }
                            schedule(220L);
                        }
                        case SUBMIT_CONTEXT -> {
                            contextSubmitAttempts++;
                            if(transport.submitPreparedContext(r)){
                                contextSubmitPending=true;
                                schedule(220L);return;
                            }
                            artemisContextAgent.actionFailed();
                            if(contextSubmitAttempts>=3){
                                failUpdate("Artemis detectó el contexto escrito pero no pudo activar Enviar.");return;
                            }
                            schedule(220L);
                        }
                        case BACK -> {
                            if(performGlobalAction(GLOBAL_ACTION_BACK)){schedule(STEP_DELAY_MS);return;}
                            artemisContextAgent.actionFailed();
                            failUpdate("Artemis no pudo normalizar Gemini antes de entregar el contexto.");
                        }
                        case WAIT -> schedule(220L);
                        case COMPLETE_CONTEXT -> {artemisContextAgent.complete();advance();}
                        case FAIL_CLOSED -> failUpdate("Artemis no pudo verificar una ruta segura para entregar el contexto.");
                        default -> failUpdate("Artemis propuso una acción no válida durante la entrega de contexto.");
                    }
                }
                case 8 -> {
                    AccessibilityNodeInfo r=root();TransportObservation o=GeminiStateObserver.observe(r,canonicalTitle);
                    if(o.state!=TransportState.NORMAL_CHAT||!o.composerReady){
                        schedule(RESPONSE_DELAY_MS);return;
                    }
                    if("LIVE".equals(sessionMode)&&!o.liveAvailable){
                        // Gemini is still producing/settling its reply. This is model
                        // latency, not a transport failure, so do not burn retry budget.
                        schedule(RESPONSE_DELAY_MS);return;
                    }
                    String all=transport.collectConversationText(r);
                    journal.baselineText(profile.id,all);
                    transition(profile,SessionStage.CONTEXT_READY);
                    if(rebuildingConversation)go(9);else go(13);
                }
                case 9 -> {
                    if(transport.openChatOptions(root()))advance();
                    else retryAdaptive("No pude abrir las opciones del chat recién reconstruido.");
                }
                case 10 -> {
                    if(transport.chooseRename(root()))advance();
                    else retryAdaptive("No pude localizar la acción semántica para nombrar el chat canónico.");
                }
                case 11 -> {
                    if(transport.setCanonicalTitle(root(),canonicalTitle))advance();
                    else retryAdaptive("No pude escribir el nombre del chat canónico.");
                }
                case 12 -> {
                    if(transport.saveCanonicalTitle(root())){
                        conversations.markVerified(profile,true);
                        compatibility.recordSuccess(profile.id,"rebuild-rename","canonical="+canonicalTitle);
                        go(13);return;
                    }
                    retryAdaptive("No pude confirmar el nombre del chat canónico.");
                }
                case 13 -> {
                    if("CHAT".equals(sessionMode)){
                        transition(profile,SessionStage.CHAT_ACTIVE);rememberActive(profile);showOverlay(profile);
                        mode=Mode.WAIT;step=0;retries=0;return;
                    }
                    transition(profile,SessionStage.LIVE_STARTING);
                    AccessibilityNodeInfo r=root();
                    TransportObservation o=GeminiStateObserver.observe(r,canonicalTitle);
                    ArtemisFlashAgent.Action action=artemisLiveAgent.nextLive(o);
                    switch(action){
                        case START_LIVE -> {
                            if(transport.startLive(r)){
                                liveStartPending=true;
                                schedule(220L);return;
                            }
                            artemisLiveAgent.actionFailed();
                            if(++retries>=3){
                                failUpdate("Artemis detectó Live pero no pudo activarlo.");return;
                            }
                            schedule(220L);
                        }
                        case COMPLETE_LIVE -> {
                            if(liveStartPending){
                                artemisLiveAgent.actionSucceeded(TransportState.NORMAL_CHAT,
                                        ArtemisFlashAgent.Action.START_LIVE);
                                artemisLiveAgent.complete();
                            }
                            transition(profile,SessionStage.LIVE_ACTIVE);
                            rememberActive(profile);showOverlay(profile);
                            mode=Mode.WAIT;step=0;retries=0;
                        }
                        case WAIT -> schedule(RESPONSE_DELAY_MS);
                        case FAIL_CLOSED -> {
                            artemisLiveAgent.actionFailed();
                            if(++retries>=3){
                                failUpdate("Artemis no pudo resolver una ruta segura hacia Gemini Live.");return;
                            }
                            schedule(STEP_DELAY_MS);
                        }
                        default -> {
                            artemisLiveAgent.actionFailed();
                            failUpdate("Artemis propuso una acción no válida al iniciar Gemini Live.");
                        }
                    }
                }
                default -> {}
            }
        }

        private void pumpWait(){
            AccessibilityNodeInfo r=root();if(!transport.isGeminiSurface(r))return;
            if("CHAT".equals(sessionMode))return;
            if(transport.isLiveActive(r)){liveMissingChecks=0;return;}
            if(++liveMissingChecks>=2)beginClose();else schedule(650L);
        }

        private void pumpClose(){
            if(sessionId==null||sessionId.isEmpty()){fail("No existe SESSION_ID para cerrar esta sesión.");return;}
            switch(step){
                case 0 -> {transition(profile,SessionStage.CLOSING_SESSION);launchGemini();advance();}
                case 1 -> {
                    AccessibilityNodeInfo r=root();
                    if("LIVE".equals(sessionMode)&&transport.isLiveActive(r)){
                        boolean clicked=transport.endLive(r);
                        if(!clicked)performGlobalAction(GLOBAL_ACTION_BACK);
                        go(2);return;
                    }
                    advance();
                }
                case 2 -> {transition(profile,SessionStage.WAITING_TRANSCRIPT);schedule(TRANSCRIPT_SETTLE_MS);step=3;}
                case 3 -> {
                    AccessibilityNodeInfo r=root();
                    if(r==null||!transport.hasComposer(r)){
                        retry("Gemini todavía no devolvió la misma conversación después de la sesión.",false);return;
                    }
                    if(transcriptSettlePasses<3){transcriptSettlePasses++;transport.scrollConversation(r);schedule(TRANSCRIPT_SETTLE_MS);return;}
                    sessionEvidenceText=SessionTextDelta.delta(journal.baselineText(profile.id),transport.collectConversationText(r));
                    if(sessionEvidenceText.trim().length()<20){finishReady();return;}
                    advance();
                }
                case 4 -> {
                    transition(profile,SessionStage.ANALYZING);
                    AccessibilityNodeInfo r=root();
                    debriefBaselineText=transport.collectConversationText(r);
                    if(transport.sendContext(r,prompts.sessionDebrief(profile)))advance();
                    else retry("No pude solicitar el cierre breve de esta sesión.",false);
                }
                case 5 -> {
                    String all=transport.collectConversationText(root());
                    String debriefDelta=SessionTextDelta.delta(debriefBaselineText,all);
                    SessionReportParser.Report report=SessionReportParser.parseDebrief(debriefDelta);
                    if(report==null){retryMarker("Gemini aún no terminó el cierre breve de esta sesión.");return;}
                    transition(profile,SessionStage.COMMITTING);
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
                default -> {}
            }
        }

        private void finishReady(){
            finished=true;journal.clearError(profile.id);journal.clearRecoverableStage(profile.id);
            journal.stage(profile.id,SessionStage.READY);journal.clearActiveSession(profile.id);clearActive(profile);
            if(overlay!=null)overlay.hide();launchAleyon();
        }
        private void failUpdate(String msg){
            recordDiagnostic(profile,"APP_UPDATE_REQUIRED",msg);
            SessionStage safe=journal.stage(profile.id);journal.recoverableStage(profile.id,safe);
            journal.appendErrorHistory(profile.id,msg,SessionStage.APP_UPDATE_REQUIRED);
            journal.error(profile.id,msg,SessionStage.APP_UPDATE_REQUIRED);finished=true;
            if(overlay!=null)overlay.markNeedsAttention();launchAleyon();
        }

        private void fail(String msg){
            recordDiagnostic(profile,"FAIL",msg);SessionStage safe=journal.stage(profile.id);journal.recoverableStage(profile.id,safe);
            journal.appendErrorHistory(profile.id,msg,SessionStage.ERROR);journal.error(profile.id,msg,SessionStage.ERROR);
            finished=true;if(overlay!=null)overlay.markNeedsAttention();launchAleyon();
        }
        private void failAmbiguous(String msg){
            recordDiagnostic(profile,"AMBIGUOUS",msg);SessionStage safe=journal.stage(profile.id);journal.recoverableStage(profile.id,safe);
            journal.appendErrorHistory(profile.id,msg,SessionStage.AMBIGUOUS_USER_REQUIRED);
            journal.error(profile.id,msg,SessionStage.AMBIGUOUS_USER_REQUIRED);finished=true;
            if(overlay!=null)overlay.markNeedsAttention();launchAleyon();
        }

    }
}
