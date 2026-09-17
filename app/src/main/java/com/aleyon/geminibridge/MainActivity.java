package com.aleyon.geminibridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.aleyon.geminibridge.automation.AleyonAccessibilityService;
import com.aleyon.geminibridge.automation.AutomationRequest;
import com.aleyon.geminibridge.automation.DiagnosticsRecorder;
import com.aleyon.geminibridge.automation.LearningStore;
import com.aleyon.geminibridge.automation.ProfileSpec;
import com.aleyon.geminibridge.automation.SessionJournal;

/** Minimal local control surface; Gemini remains the cognitive/Live surface. */
public final class MainActivity extends Activity {
    private WebView webView;
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);webView=new WebView(this);webView.getSettings().setJavaScriptEnabled(true);webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);webView.getSettings().setAllowContentAccess(false);webView.getSettings().setAllowFileAccessFromFileURLs(false);webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        WebView.setWebContentsDebuggingEnabled(false);webView.setWebViewClient(new WebViewClient());webView.addJavascriptInterface(new AndroidBridge(this),"AndroidBridge");webView.loadUrl("file:///android_asset/index.html");setContentView(webView);
    }
    @Override protected void onResume(){super.onResume();if(webView!=null)webView.evaluateJavascript("window.onAleyonResume&&window.onAleyonResume()",null);}
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}

    public static final class AndroidBridge {
        private final Activity activity;private final SessionJournal journal;private final LearningStore learning;private final DiagnosticsRecorder diagnostics;
        AndroidBridge(Activity a){activity=a;journal=new SessionJournal(a);learning=new LearningStore(a);diagnostics=new DiagnosticsRecorder(a);}
        @JavascriptInterface public boolean isGeminiInstalled(){try{activity.getPackageManager().getPackageInfo(AleyonAccessibilityService.GEMINI_PACKAGE,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}
        @JavascriptInterface public boolean isAutomationEnabled(){String expected=new ComponentName(activity,AleyonAccessibilityService.class).flattenToString();String enabled=Settings.Secure.getString(activity.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(TextUtils.isEmpty(enabled))return false;TextUtils.SimpleStringSplitter s=new TextUtils.SimpleStringSplitter(':');s.setString(enabled);while(s.hasNext())if(expected.equalsIgnoreCase(s.next()))return true;return false;}
        @JavascriptInterface public void openAutomationSettings(){activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){Toast.makeText(activity,"No pude abrir Accesibilidad.",Toast.LENGTH_SHORT).show();}});}
        @JavascriptInterface public void openGemini(){activity.runOnUiThread(()->{Intent i=activity.getPackageManager().getLaunchIntentForPackage(AleyonAccessibilityService.GEMINI_PACKAGE);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);activity.startActivity(i);}else Toast.makeText(activity,"Gemini no está instalado.",Toast.LENGTH_SHORT).show();});}

        @JavascriptInterface public boolean saveProfileLocal(String json){try{journal.saveProfile(ProfileSpec.fromJson(json));return true;}catch(Exception e){return false;}}
        @JavascriptInterface public boolean startLiveSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.START_LIVE_SESSION,json,false));}
        @JavascriptInterface public boolean startChatSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.START_CHAT_SESSION,json,false));}
        @JavascriptInterface public boolean closeSession(String json){return submit(new AutomationRequest(AutomationRequest.Type.CLOSE_SESSION,json,false));}
        @JavascriptInterface public boolean recoverProfile(String json){return submit(new AutomationRequest(AutomationRequest.Type.RECOVER,json,false));}
        private boolean submit(AutomationRequest r){return AleyonAccessibilityService.submit(activity,r);}

        @JavascriptInterface public String getNativeProfilesJson(){return journal.allProfilesJson().toString();}
        @JavascriptInterface public String getProfileStatus(String id){try{return journal.statusJson(id).toString();}catch(Exception e){return "{\"profileId\":\""+id+"\",\"stage\":\"ERROR\"}";}}
        @JavascriptInterface public String getProfileMemory(String id){return learning.exportProfileMemory(id);}
        @JavascriptInterface public String getDiagnosticsJson(){return diagnostics.recentJson();}
        @JavascriptInterface public void removeNativeLocalProfile(String id){journal.removeProfile(id);learning.remove(id);}
        @JavascriptInterface public void copyText(String text){activity.runOnUiThread(()->{ClipboardManager cm=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Aleyon",text));Toast.makeText(activity,"Copiado",Toast.LENGTH_SHORT).show();});}
        @JavascriptInterface public void shareText(String text){activity.runOnUiThread(()->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);activity.startActivity(Intent.createChooser(send,"Compartir Aleyon Bridge"));});}
    }
}
