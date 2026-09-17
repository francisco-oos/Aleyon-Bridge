package com.aleyon.geminibridge.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.aleyon.geminibridge.MainActivity;
import com.aleyon.geminibridge.core.AutomationDiagnostics;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.RecoveryPlanner;
import com.aleyon.geminibridge.core.SessionReportParser;
import com.aleyon.geminibridge.core.SessionStage;
import com.aleyon.geminibridge.core.SessionTextDelta;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/** Bridge 0.4 orchestrator: Gemini owns session cognition; Aleyon owns continuity and evidence. */
public final class AleyonAccessibilityService extends AccessibilityService implements OverlayController.Listener {
    public static final String GEMINI_PACKAGE="com.google.android.apps.bard";
    private static final long STEP_DELAY_MS=650, RESPONSE_DELAY_MS=1100, TRANSCRIPT_SETTLE_MS=2500;
    private static final int MAX_UI_RETRIES=18, MAX_RESPONSE_RETRIES=55;
    private static AleyonAccessibilityService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SessionJournal journal; private LearningStore learning; private PromptRepository prompts; private OverlayController overlay; private DiagnosticsRecorder diagnostics; private Runner runner;
    private String lastRootSource="none", lastWindowsSummary=""; private int lastInteractiveWindowCount;

    public static AleyonAccessibilityService instance(){return instance;}
    public static boolean submit(android.content.Context context,AutomationRequest request){try{AutomationCommandBus.enqueue(context,request);AleyonAccessibilityService s=instance;if(s!=null)s.kick();return s!=null;}catch(Exception e){return false;}}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;journal=new SessionJournal(this);learning=new LearningStore(this);prompts=new PromptRepository(this);overlay=new OverlayController(this,this);diagnostics=new DiagnosticsRecorder(this);kick();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(event==null)return;if(event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED){if(runner!=null)runner.schedule(120);return;}if(event.getPackageName()!=null&&GEMINI_PACKAGE.contentEquals(event.getPackageName())&&runner!=null)runner.schedule(180);}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(overlay!=null)overlay.hide();if(instance==this)instance=null;super.onDestroy();}
    @Override public void onReturnToGemini(){launchGemini();}
    @Override public void onCloseRequested(){if(runner!=null&&!runner.finished){runner.beginClose();return;}String id=getSharedPreferences("aleyon_runtime",MODE_PRIVATE).getString("active_profile_id",null);if(id!=null){ProfileSpec p=journal.loadProfile(id);if(p!=null)startRunner(Mode.CLOSE,p,journal.activeSessionMode(id));}}

    public void kick(){handler.post(()->{if(runner!=null&&!runner.finished)return;AutomationRequest req=AutomationCommandBus.consume(this);if(req==null)return;try{ProfileSpec p=ProfileSpec.fromJson(req.profileJson);journal.saveProfile(p);switch(req.type){case START_LIVE_SESSION->startOrRecover(p,"LIVE");case START_CHAT_SESSION->startOrRecover(p,"CHAT");case CLOSE_SESSION->startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));case RECOVER->recover(p);}}catch(Exception ignored){}});}
    private void startOrRecover(ProfileSpec p,String sessionMode){SessionStage s=journal.stage(p.id);if(s==SessionStage.READY||s==SessionStage.ERROR)startRunner("CHAT".equals(sessionMode)?Mode.START_CHAT:Mode.START_LIVE,p,sessionMode);else recover(p);}
    private void recover(ProfileSpec p){SessionStage previous=journal.stage(p.id);journal.stage(p.id,SessionStage.RECOVERING);launchGemini();handler.postDelayed(()->{boolean live=GeminiUi.isLiveScreen(resolveGeminiRoot());RecoveryPlanner.RecoveryAction action=RecoveryPlanner.plan(previous,live);switch(action){case NONE->finishReadyOutsideRunner(p);case RETRY_START->startRunner("CHAT".equals(journal.activeSessionMode(p.id))?Mode.START_CHAT:Mode.START_LIVE,p,journal.activeSessionMode(p.id));case RESTORE_LIVE_OVERLAY->{journal.stage(p.id,SessionStage.LIVE_ACTIVE);rememberActive(p);showOverlay(p);startRunner(Mode.WAIT,p,"LIVE");}case RESTORE_CHAT_OVERLAY->{journal.stage(p.id,SessionStage.CHAT_ACTIVE);rememberActive(p);showOverlay(p);startRunner(Mode.WAIT,p,"CHAT");}case FINISH_CLOSE->startRunner(Mode.CLOSE,p,journal.activeSessionMode(p.id));case ASK_USER->failWithoutRunner(p,"Se detectó una ambigüedad que requiere revisión humana.",SessionStage.AMBIGUOUS_USER_REQUIRED);case REQUIRE_BRIDGE_UPDATE->failWithoutRunner(p,"Gemini cambió de interfaz. Aleyon Bridge necesita actualizar selectores.",SessionStage.APP_UPDATE_REQUIRED);}},900);}
    private void startRunner(Mode mode,ProfileSpec p,String sessionMode){runner=new Runner(mode,p,sessionMode);runner.schedule(0);}private void failWithoutRunner(ProfileSpec p,String msg,SessionStage stage){journal.error(p.id,msg,stage);launchAleyon();}private void finishReadyOutsideRunner(ProfileSpec p){journal.stage(p.id,SessionStage.READY);journal.clearActiveSession(p.id);clearActive(p);if(overlay!=null)overlay.hide();launchAleyon();}
    private void launchGemini(){Intent i=getPackageManager().getLaunchIntentForPackage(GEMINI_PACKAGE);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}}
    private void launchAleyon(){Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}

    /** Android-16/OEM-safe resolver retained from alpha4; returns only Gemini-owned roots. */
    private AccessibilityNodeInfo resolveGeminiRoot(){lastRootSource="none";lastWindowsSummary="";lastInteractiveWindowCount=0;try{AccessibilityNodeInfo active=getRootInActiveWindow();if(GeminiUi.isGeminiRoot(active)){lastRootSource="active-root";lastWindowsSummary="activeRoot=gemini";return active;}List<AccessibilityWindowInfo>windows=getWindows();if(windows==null||windows.isEmpty()){lastWindowsSummary=active==null?"activeRoot=null; windows=0":"activeRootPackage="+packageName(active)+"; windows=0";return null;}lastInteractiveWindowCount=windows.size();StringBuilder summary=new StringBuilder();int applicationWindows=0;AccessibilityNodeInfo soleGeminiApplicationRoot=null,focusedGeminiRoot=null,activeGeminiRoot=null;for(int i=0;i<windows.size();i++){AccessibilityWindowInfo w=windows.get(i);if(w==null)continue;AccessibilityNodeInfo candidate=null;try{candidate=w.getRoot();}catch(Exception ignored){}String pkg=packageName(candidate);boolean application=w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION;if(application)applicationWindows++;if(summary.length()>0)summary.append(" | ");summary.append('#').append(i).append(":type=").append(w.getType()).append(",active=").append(w.isActive()).append(",focused=").append(w.isFocused()).append(",pkg=").append(pkg.isEmpty()?"?":pkg);if(!GeminiUi.isGeminiRoot(candidate))continue;if(application)soleGeminiApplicationRoot=candidate;if(w.isActive())activeGeminiRoot=candidate;if(w.isFocused())focusedGeminiRoot=candidate;}lastWindowsSummary=summary.toString();if(activeGeminiRoot!=null){lastRootSource="interactive-window-active";return activeGeminiRoot;}if(focusedGeminiRoot!=null){lastRootSource="interactive-window-focused";return focusedGeminiRoot;}if(applicationWindows==1&&soleGeminiApplicationRoot!=null){lastRootSource="interactive-window-sole-application";return soleGeminiApplicationRoot;}}catch(Exception e){lastWindowsSummary="resolverException="+e.getClass().getSimpleName();}return null;}
    private static String packageName(AccessibilityNodeInfo n){return n==null||n.getPackageName()==null?"":n.getPackageName().toString();}
    private void rememberActive(ProfileSpec p){getSharedPreferences("aleyon_runtime",MODE_PRIVATE).edit().putString("active_profile_id",p.id).apply();}private void clearActive(ProfileSpec p){getSharedPreferences("aleyon_runtime",MODE_PRIVATE).edit().remove("active_profile_id").apply();}private String status(ProfileSpec p,String field){try{return journal.statusJson(p.id).optString(field,"");}catch(Exception e){return "";}}private void showOverlay(ProfileSpec p){overlay.show(p.label,status(p,"objectiveToday"),status(p,"starterPhrase"));}
    private void recordDiagnostic(ProfileSpec p,String step,String result){try{AccessibilityNodeInfo r=resolveGeminiRoot();diagnostics.record(new AutomationDiagnostics(p==null?"":journal.activeSessionId(p.id),step,p==null?"":journal.stage(p.id).name(),p==null?"":journal.stage(p.id).name(),System.currentTimeMillis(),packageName(r),r!=null,GeminiUi.countNodes(r),"",0,result,lastRootSource,lastInteractiveWindowCount,lastWindowsSummary));}catch(Exception ignored){}}

    private enum Mode{START_LIVE,START_CHAT,WAIT,CLOSE}
    private final class Runner{
        private Mode mode;private final ProfileSpec profile;private String sessionMode;private String sessionId;private int step,retries,liveMissingChecks;private boolean finished,creatingNewChat;private String sessionEvidenceText="";private final Runnable pumpRunnable=this::pump;
        Runner(Mode mode,ProfileSpec p,String sessionMode){this.mode=mode;this.profile=p;this.sessionMode=sessionMode==null?"LIVE":sessionMode;this.sessionId=journal.activeSessionId(p.id);}void schedule(long ms){if(finished)return;handler.removeCallbacks(pumpRunnable);handler.postDelayed(pumpRunnable,ms);}void beginClose(){mode=Mode.CLOSE;step=0;retries=0;schedule(0);}void pump(){if(finished)return;try{switch(mode){case START_LIVE,START_CHAT->pumpStart();case WAIT->pumpWait();case CLOSE->pumpClose();}}catch(Exception e){fail("Error interno: "+e.getClass().getSimpleName());}}private AccessibilityNodeInfo root(){return resolveGeminiRoot();}private void advance(){step++;retries=0;schedule(STEP_DELAY_MS);}private void go(int n){step=n;retries=0;schedule(STEP_DELAY_MS);}private boolean retry(String reason,boolean response){retries++;if(retries>(response?MAX_RESPONSE_RETRIES:MAX_UI_RETRIES)){fail(reason);return false;}schedule(response?RESPONSE_DELAY_MS:STEP_DELAY_MS);return true;}private String newSessionId(){return "session-"+UUID.randomUUID().toString().replace("-","").substring(0,16);}

        private void pumpStart(){if(!profile.canStart()){fail("Perfil incompleto para iniciar sesión.");return;}switch(step){
            case 0->{sessionMode=mode==Mode.START_CHAT?"CHAT":"LIVE";sessionId=newSessionId();journal.activeSession(profile.id,sessionId,sessionMode);journal.stage(profile.id,SessionStage.LOCATING_CHAT);launchGemini();advance();}
            case 1->{if(GeminiUi.clickAny(root(),"Menú","Menu","Abrir menú","Open menu"))advance();else retry("No pude abrir el menú de Gemini.",false);}
            case 2->{if(GeminiUi.clickAny(root(),"Buscar chats","Search chats","Buscar conversaciones","Search conversations"))advance();else retry("No encontré Buscar chats.",false);}
            case 3->{if(GeminiUi.setFirstEditable(root(),profile.chatName))advance();else retry("No encontré el campo de búsqueda de chats.",false);}
            case 4->{int count=countExactText(root(),profile.chatName);if(count>1){failAmbiguous("Encontré más de un chat canónico exacto para este perfil.");return;}if(count==1&&GeminiUi.clickExact(root(),profile.chatName)){creatingNewChat=false;go(8);return;}retries++;if(retries<3){schedule(STEP_DELAY_MS);return;}creatingNewChat=true;retries=0;performGlobalAction(GLOBAL_ACTION_BACK);performGlobalAction(GLOBAL_ACTION_BACK);launchGemini();journal.stage(profile.id,SessionStage.CREATING_CHAT);go(5);}
            case 5->{if(GeminiUi.clickAny(root(),"Nuevo chat","New chat","Chat nuevo")||GeminiUi.firstEditable(root())!=null)advance();else retry("No pude crear un chat nuevo.",false);}
            case 6->{journal.baselineText(profile.id,"");sendCapsuleOrRetry();}
            case 7->{if(waitContextReady())go(12);else retry("Gemini no confirmó la cápsula de continuidad.",true);}
            case 8->{journal.baselineText(profile.id,GeminiUi.collectAllText(root()));sendCapsuleOrRetry();}
            case 9->{if(waitContextReady())go(15);else retry("Gemini no confirmó la cápsula de continuidad.",true);}
            case 12->{if(GeminiUi.clickAny(root(),"Más","More"))advance();else retry("No pude abrir el menú para nombrar el chat canónico.",false);}
            case 13->{if(GeminiUi.clickAny(root(),"Cambiar nombre","Rename","Renombrar","Edit title"))advance();else retry("No encontré Renombrar.",false);}
            case 14->{if(GeminiUi.setFirstEditable(root(),profile.chatName)){GeminiUi.clickAny(root(),"Guardar","Save","Aceptar","OK");go(15);}else retry("No pude escribir el nombre canónico.",false);}
            case 15->{if("CHAT".equals(sessionMode)){journal.stage(profile.id,SessionStage.CHAT_ACTIVE);rememberActive(profile);showOverlay(profile);mode=Mode.WAIT;step=0;retries=0;return;}journal.stage(profile.id,SessionStage.LIVE_STARTING);if(GeminiUi.clickAny(root(),"Live","Gemini Live","Iniciar Live","Start Live"))advance();else retry("No encontré el botón Live.",false);}
            case 16->{if(GeminiUi.isLiveScreen(root())){journal.stage(profile.id,SessionStage.LIVE_ACTIVE);rememberActive(profile);showOverlay(profile);mode=Mode.WAIT;step=0;retries=0;}else retry("Live no llegó a estado activo.",true);}
        }}
        private void sendCapsuleOrRetry(){LearningLedger ledger=learning.load(profile.id);String capsule=prompts.contextCapsule(profile,ledger,sessionId,"LIVE".equals(sessionMode));journal.stage(profile.id,SessionStage.CONTEXT_INJECTING);if(GeminiUi.sendMessage(root(),capsule))advance();else retry("No pude enviar la cápsula de continuidad.",false);}
        private boolean waitContextReady(){String all=GeminiUi.collectAllText(root());if(SessionReportParser.hasSessionReady(all,sessionId,profile.id)){journal.stage(profile.id,SessionStage.CONTEXT_READY);return true;}return false;}
        private void pumpWait(){AccessibilityNodeInfo r=root();if(!GeminiUi.isGeminiRoot(r))return;if("CHAT".equals(sessionMode))return;if(GeminiUi.isLiveScreen(r)){liveMissingChecks=0;return;}if(++liveMissingChecks>=2)beginClose();else schedule(650);}
        private void pumpClose(){switch(step){
            case 0->{journal.stage(profile.id,SessionStage.CLOSING_SESSION);launchGemini();advance();}
            case 1->{if(GeminiUi.isLiveScreen(root())){boolean clicked=GeminiUi.clickAny(root(),"Cerrar","Close","Finalizar","End","Salir de Live","End Live");if(!clicked)performGlobalAction(GLOBAL_ACTION_BACK);schedule(TRANSCRIPT_SETTLE_MS);return;}advance();}
            case 2->{journal.stage(profile.id,SessionStage.WAITING_TRANSCRIPT);schedule(TRANSCRIPT_SETTLE_MS);step=3;}
            case 3->{if(GeminiUi.firstEditable(root())!=null){sessionEvidenceText=SessionTextDelta.delta(journal.baselineText(profile.id),GeminiUi.collectAllText(root()));advance();return;}launchGemini();go(20);}
            case 4->{journal.stage(profile.id,SessionStage.ANALYZING);if(GeminiUi.sendMessage(root(),prompts.sessionReport(profile,sessionId)))advance();else retry("No pude solicitar el informe de sesión.",false);}
            case 5->{SessionReportParser.Report report=SessionReportParser.parse(GeminiUi.collectAllText(root()),sessionId,profile.id,sessionEvidenceText);if(report==null){retry("Gemini aún no devolvió el informe estructurado de esta sesión.",true);return;}journal.stage(profile.id,SessionStage.COMMITTING);learning.commit(profile.id,report,sessionId);if(!report.nextObjective.isEmpty())journal.sessionHints(profile.id,report.nextObjective,"");try{profile.sessions++;journal.saveProfile(profile);}catch(Exception ignored){}finishReady();}
            case 20->{if(GeminiUi.clickAny(root(),"Menú","Menu","Abrir menú","Open menu"))advance();else retry("No pude abrir el menú para localizar el chat al cerrar.",false);}
            case 21->{if(GeminiUi.clickAny(root(),"Buscar chats","Search chats","Buscar conversaciones","Search conversations"))advance();else retry("No encontré Buscar chats al cerrar.",false);}
            case 22->{if(GeminiUi.setFirstEditable(root(),profile.chatName))advance();else retry("No encontré el buscador al cerrar.",false);}
            case 23->{int count=countExactText(root(),profile.chatName);if(count>1){failAmbiguous("Hay más de un chat canónico exacto al cerrar.");}else if(count==1&&GeminiUi.clickExact(root(),profile.chatName)){go(24);}else retry("No pude localizar el chat canónico para cerrar.",false);}
            case 24->{if(GeminiUi.firstEditable(root())!=null){sessionEvidenceText=SessionTextDelta.delta(journal.baselineText(profile.id),GeminiUi.collectAllText(root()));go(4);}else retry("El chat canónico aún no terminó de abrir al cerrar.",false);}
        }}
        private void finishReady(){finished=true;journal.clearError(profile.id);journal.stage(profile.id,SessionStage.READY);journal.clearActiveSession(profile.id);clearActive(profile);if(overlay!=null)overlay.hide();launchAleyon();}
        private void fail(String msg){recordDiagnostic(profile,"FAIL",msg);finished=true;journal.error(profile.id,msg,SessionStage.ERROR);if(overlay!=null)overlay.hide();launchAleyon();}
        private void failAmbiguous(String msg){recordDiagnostic(profile,"AMBIGUOUS",msg);finished=true;journal.error(profile.id,msg,SessionStage.AMBIGUOUS_USER_REQUIRED);if(overlay!=null)overlay.hide();launchAleyon();}
        private int countExactText(AccessibilityNodeInfo root,String exact){if(root==null||exact==null)return 0;String wanted=exact.trim();Queue<AccessibilityNodeInfo>q=new ArrayDeque<>();q.add(root);int count=0;while(!q.isEmpty()){AccessibilityNodeInfo n=q.remove();CharSequence t=n.getText();if(t!=null&&wanted.equals(t.toString().trim()))count++;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}}return count;}
    }
}
