package com.aleyon.geminibridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.aleyon.geminibridge.automation.AleyonAccessibilityService;
import com.aleyon.geminibridge.automation.AutomationRequest;
import com.aleyon.geminibridge.automation.DiagnosticsRecorder;
import com.aleyon.geminibridge.automation.LearningStore;
import com.aleyon.geminibridge.automation.ProfileSpec;
import com.aleyon.geminibridge.automation.SessionJournal;
import com.aleyon.geminibridge.transport.CompatibilityMemory;

import java.util.UUID;

/** Native shell for the local profile/memory UI. */
public final class MainActivity extends Activity {
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        webView=new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.getSettings().setSafeBrowsingEnabled(true);
        webView.getSettings().setSaveFormData(false);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebViewClient(new WebViewClient(){
            private boolean allow(String url){return url!=null&&url.startsWith("file:///android_asset/");}
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                return request==null||request.getUrl()==null||!allow(request.getUrl().toString());
            }
            @Override @SuppressWarnings("deprecation") public boolean shouldOverrideUrlLoading(WebView view,String url){
                return !allow(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton(android.R.string.ok,(d,w)->result.confirm())
                        .setOnCancelListener(d->result.confirm()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton(android.R.string.ok,(DialogInterface d,int w)->result.confirm())
                        .setNegativeButton(android.R.string.cancel,(d,w)->result.cancel())
                        .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(this),"AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
        // Session-summary notifications are optional. Never open a runtime
        // permission dialog during startup: an already-enabled accessibility
        // overlay can make Android security dialogs reject touches. If the
        // user has not granted notifications, NotificationHelper simply skips
        // the optional summary notification without affecting Bridge.
    }

    @Override protected void onResume(){
        super.onResume();
        if(webView!=null)webView.evaluateJavascript("window.onAleyonResume&&window.onAleyonResume()",null);
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed(){
        if(webView==null){super.onBackPressed();return;}
        webView.evaluateJavascript("(window.handleAleyonBack&&window.handleAleyonBack())?'handled':'not-handled'",value->{
            if(value!=null&&value.contains("handled")&&!value.contains("not-handled"))return;
            if(webView.canGoBack())webView.goBack(); else MainActivity.super.onBackPressed();
        });
    }

    public static final class AndroidBridge {
        private final Activity activity;
        private final SessionJournal journal;
        private final LearningStore learning;
        private final DiagnosticsRecorder diagnostics;
        private final CompatibilityMemory compatibility;
        AndroidBridge(Activity a){activity=a;journal=new SessionJournal(a);learning=new LearningStore(a);diagnostics=new DiagnosticsRecorder(a);compatibility=new CompatibilityMemory(a);}

        @JavascriptInterface public boolean isGeminiInstalled(){
            try{activity.getPackageManager().getPackageInfo(AleyonAccessibilityService.GEMINI_PACKAGE,0);return true;}
            catch(PackageManager.NameNotFoundException e){return false;}
        }
        @JavascriptInterface public boolean isAutomationEnabled(){
            String expected=new ComponentName(activity,AleyonAccessibilityService.class).flattenToString();
            String enabled=Settings.Secure.getString(activity.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(TextUtils.isEmpty(enabled))return false;
            TextUtils.SimpleStringSplitter s=new TextUtils.SimpleStringSplitter(':');s.setString(enabled);
            while(s.hasNext())if(expected.equalsIgnoreCase(s.next()))return true;
            return false;
        }
        @JavascriptInterface public void openAutomationSettings(){activity.runOnUiThread(()->{
            try{activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(Exception e){Toast.makeText(activity,"No pude abrir Accesibilidad.",Toast.LENGTH_SHORT).show();}
        });}
        @JavascriptInterface public void openGemini(){activity.runOnUiThread(()->{
            Intent i=activity.getPackageManager().getLaunchIntentForPackage(AleyonAccessibilityService.GEMINI_PACKAGE);
            if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);activity.startActivity(i);}
            else Toast.makeText(activity,"Gemini no está instalado.",Toast.LENGTH_SHORT).show();
        });}

        @JavascriptInterface public boolean saveProfileLocally(String json){
            try{journal.saveProfile(ProfileSpec.fromJson(json));return true;}catch(Exception e){return false;}
        }
        // Compatibility alias used by the first 0.4 prototype.
        @JavascriptInterface public boolean saveProfileLocal(String json){return saveProfileLocally(json);}
        // Profile setup is purely local; Gemini is touched only when starting a session.
        @JavascriptInterface public boolean setupProfile(String json){return saveProfileLocally(json);}
        @JavascriptInterface public boolean startSession(String json){return startLiveSession(json);}
        @JavascriptInterface public boolean startLiveSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.START_LIVE_SESSION,json,false));}
        @JavascriptInterface public boolean startChatSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.START_CHAT_SESSION,json,false));}
        @JavascriptInterface public boolean closeSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.CLOSE_SESSION,json,false));}

        @JavascriptInterface public String startDiagnosticProbe(){
            String id="probe-"+UUID.randomUUID();
            return submit(new AutomationRequest(AutomationRequest.Type.DIAGNOSTIC_PROBE,id,false))?id:"";
        }
        @JavascriptInterface public boolean runDiagnosticProbe(){return !startDiagnosticProbe().isEmpty();}
        @JavascriptInterface public String getProbeJson(){return activity.getSharedPreferences("aleyon_probe",MODE_PRIVATE).getString("last_probe","{}");}
        @JavascriptInterface public long getProbeTimestamp(){return activity.getSharedPreferences("aleyon_probe",MODE_PRIVATE).getLong("last_probe_ts",0L);}
        @JavascriptInterface public String getProbeId(){return activity.getSharedPreferences("aleyon_probe",MODE_PRIVATE).getString("last_probe_id","");}

        @JavascriptInterface public String getNativeProfilesJson(){return journal.allProfilesJson().toString();}
        @JavascriptInterface public String getSessionSummaryHistory(String id){return journal.closeSummaryHistory(id).toString();}
        @JavascriptInterface public String getProfileStatus(String id){try{return journal.statusJson(id).toString();}catch(Exception e){return "{\"profileId\":\""+id+"\",\"stage\":\"ERROR\"}";}}
        @JavascriptInterface public String getProfileMemory(String id){return learning.exportProfileMemory(id);}
        @JavascriptInterface public String getDiagnosticsJson(){return diagnostics.recentJson();}
        @JavascriptInterface public String getPendingSummaryProfileId(){return activity.getSharedPreferences("aleyon_runtime",MODE_PRIVATE).getString("pending_summary_profile","");}
        @JavascriptInterface public void acknowledgePendingSummary(){activity.getSharedPreferences("aleyon_runtime",MODE_PRIVATE).edit().remove("pending_summary_profile").apply();}
        @JavascriptInterface public void removeNativeLocalProfile(String id){journal.removeProfile(id);learning.remove(id);compatibility.remove(id);}

        @JavascriptInterface public void copyText(String text){activity.runOnUiThread(()->{
            ClipboardManager cm=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Aleyon",text));Toast.makeText(activity,"Copiado",Toast.LENGTH_SHORT).show();
        });}
        @JavascriptInterface public void shareText(String text){activity.runOnUiThread(()->{
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);
            activity.startActivity(Intent.createChooser(send,"Compartir Aleyon Bridge"));
        });}

        private boolean submit(AutomationRequest request){return AleyonAccessibilityService.submit(activity,request);}
    }
}
