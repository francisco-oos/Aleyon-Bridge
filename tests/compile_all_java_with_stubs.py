#!/usr/bin/env python3
"""
Compile every production Java source against tiny API stubs.

Purpose: catch Java syntax/internal-reference regressions in environments where
Android SDK is unavailable. This is not a substitute for Gradle/Android SDK
compilation or device QA.
"""
from pathlib import Path
import shutil, subprocess, sys, textwrap

ROOT = Path(__file__).resolve().parents[1]
TMP = ROOT / ".full-java-stub-test"
if TMP.exists():
    shutil.rmtree(TMP)
src = TMP / "src"
out = TMP / "out"
src.mkdir(parents=True)
out.mkdir(parents=True)

STUBS = {
"android/util/Log.java": '''package android.util; public class Log { public static int d(String tag, String msg){return 0;} public static int e(String tag, String msg){return 0;} public static int w(String tag, String msg){return 0;} public static int i(String tag, String msg){return 0;} }''',
"android/content/Context.java": '''
package android.content;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
public class Context {
  public static final int MODE_PRIVATE=0;
  public static final String CLIPBOARD_SERVICE="clipboard", WINDOW_SERVICE="window", NOTIFICATION_SERVICE="notification";
  public SharedPreferences getSharedPreferences(String n,int m){return null;}
  public Object getSystemService(String n){return null;}
  public Context getApplicationContext(){return this;}
  public PackageManager getPackageManager(){return null;}
  public AssetManager getAssets(){return null;}
  public Resources getResources(){return null;}
  public void startActivity(Intent i){}
  public Object getContentResolver(){return null;}
  public int checkSelfPermission(String perm){return 0;}
}
''',
"android/app/Activity.java": '''
package android.app;
import android.content.Context; import android.os.Bundle;
public class Activity extends Context {
  protected void onCreate(Bundle b){} protected void onResume(){}
  public void onBackPressed(){} public void setContentView(Object v){}
  public void runOnUiThread(Runnable r){r.run();}
  public void requestPermissions(String[] perms,int code){}
}
''',
"android/content/res/Resources.java": '''package android.content.res; import android.util.DisplayMetrics; public class Resources { public DisplayMetrics getDisplayMetrics(){return new DisplayMetrics();} }''',
"android/util/DisplayMetrics.java": '''package android.util; public class DisplayMetrics { public int widthPixels=1080,heightPixels=2400; public float density=3f; }''',
"android/content/SharedPreferences.java": '''
package android.content;
import java.util.Set;
public interface SharedPreferences {
 String getString(String k,String d); long getLong(String k,long d); boolean getBoolean(String k,boolean d);
 Set<String> getStringSet(String k,Set<String> d); boolean contains(String k); Editor edit();
 interface Editor {
   Editor putString(String k,String v); Editor putLong(String k,long v); Editor putBoolean(String k,boolean v);
   Editor putStringSet(String k,Set<String> v); Editor remove(String k); void apply(); boolean commit();
 }
}
''',
"android/content/Intent.java": '''
package android.content;
public class Intent {
 public static final String ACTION_SEND="android.intent.action.SEND", EXTRA_TEXT="android.intent.extra.TEXT";
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000, FLAG_ACTIVITY_REORDER_TO_FRONT=0x00020000, FLAG_ACTIVITY_CLEAR_TOP=0x04000000;
 public Intent(){} public Intent(String a){} public Intent(Context c,Class<?> cls){}
 public Intent addFlags(int f){return this;} public Intent setFlags(int f){return this;} public Intent setType(String t){return this;}
 public Intent putExtra(String k,String v){return this;}
 public static Intent createChooser(Intent i,String t){return i;}
}
''',
"android/content/ComponentName.java": '''
package android.content;
public class ComponentName { public ComponentName(Context c,Class<?> k){} public String flattenToString(){return "";} }
''',
"android/content/ClipData.java": '''package android.content; public class ClipData { public static ClipData newPlainText(CharSequence l,CharSequence t){return new ClipData();} }''',
"android/content/ClipboardManager.java": '''package android.content; public class ClipboardManager { public void setPrimaryClip(ClipData c){} }''',
"android/content/pm/PackageManager.java": '''
package android.content.pm; import android.content.Intent;
public class PackageManager {
 public static final int PERMISSION_GRANTED=0, PERMISSION_DENIED=-1;
 public static class NameNotFoundException extends Exception{}
 public PackageInfo getPackageInfo(String p,int f) throws NameNotFoundException {return null;}
 public Intent getLaunchIntentForPackage(String p){return null;}
}
''',
"android/content/pm/PackageInfo.java": '''package android.content.pm; public class PackageInfo { public int versionCode=1; public long getLongVersionCode(){return versionCode;} }''',
"android/Manifest.java": '''
package android;
public final class Manifest {
 public static final class permission {
  public static final String POST_NOTIFICATIONS="android.permission.POST_NOTIFICATIONS";
 }
}
''',
"android/os/Build.java": '''
package android.os;
public class Build {
 public static class VERSION { public static final int SDK_INT=34; }
}
''',
"android/os/SystemClock.java": '''package android.os; public class SystemClock { public static void sleep(long ms){} }''',
"android/app/PendingIntent.java": '''
package android.app;
import android.content.Context; import android.content.Intent;
public class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=0x08000000, FLAG_IMMUTABLE=0x04000000;
 public static PendingIntent getActivity(Context c,int rc,Intent i,int flags){return new PendingIntent();}
}
''',
"android/app/NotificationChannel.java": '''
package android.app;
public class NotificationChannel {
 public NotificationChannel(String id,String name,int importance){}
 public void setDescription(String d){}
}
''',
"android/app/NotificationManager.java": '''
package android.app;
public class NotificationManager {
 public static final int IMPORTANCE_DEFAULT=3;
 public NotificationChannel getNotificationChannel(String id){return null;}
 public void createNotificationChannel(NotificationChannel c){}
 public void notify(int id,Notification n){}
}
''',
"android/app/Notification.java": '''
package android.app;
import android.content.Context;
public class Notification {
 public static class Builder {
  public Builder(Context c,String channelId){}
  public Builder setSmallIcon(int id){return this;}
  public Builder setContentTitle(CharSequence t){return this;}
  public Builder setContentText(CharSequence t){return this;}
  public Builder setAutoCancel(boolean b){return this;}
  public Builder setContentIntent(PendingIntent p){return this;}
  public Builder setStyle(Object style){return this;}
  public Notification build(){return new Notification();}
 }
 public static class BigTextStyle {
  public BigTextStyle bigText(CharSequence t){return this;}
 }
}
''',
"android/content/res/AssetManager.java": '''package android.content.res; import java.io.*; public class AssetManager { public InputStream open(String p) throws IOException{return null;} }''',
"android/os/Bundle.java": '''package android.os; import java.util.HashMap; public class Bundle extends HashMap<String,Object> { public void putCharSequence(String k,CharSequence v){put(k,v);} }''',
"android/os/Looper.java": '''package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }''',
"android/os/Handler.java": '''package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){return true;} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} }''',
"android/provider/Settings.java": '''
package android.provider;
public class Settings {
 public static final String ACTION_ACCESSIBILITY_SETTINGS="android.settings.ACCESSIBILITY_SETTINGS";
 public static class Secure { public static final String ENABLED_ACCESSIBILITY_SERVICES="enabled_accessibility_services"; public static String getString(Object r,String k){return "";} }
}
''',
"android/text/TextUtils.java": '''
package android.text; import java.util.*;
public class TextUtils {
 public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}
 public static class SimpleStringSplitter implements Iterator<String> { public SimpleStringSplitter(char c){} public void setString(String s){} public boolean hasNext(){return false;} public String next(){return "";} }
}
''',
"android/net/Uri.java": '''package android.net; public class Uri { public String toString(){return "";} }''',
"android/webkit/JavascriptInterface.java": '''package android.webkit; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface JavascriptInterface {}''',
"android/webkit/WebViewClient.java": '''package android.webkit; import android.webkit.WebResourceRequest; public class WebViewClient { public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){return false;} public boolean shouldOverrideUrlLoading(WebView v,String u){return false;} }''',
"android/webkit/WebResourceRequest.java": '''package android.webkit; import android.net.Uri; public interface WebResourceRequest { Uri getUrl(); }''',
"android/webkit/WebSettings.java": '''package android.webkit; public class WebSettings { public static final int MIXED_CONTENT_NEVER_ALLOW=1; public void setJavaScriptEnabled(boolean b){} public void setDomStorageEnabled(boolean b){} public void setAllowFileAccess(boolean b){} public void setAllowContentAccess(boolean b){} public void setAllowFileAccessFromFileURLs(boolean b){} public void setAllowUniversalAccessFromFileURLs(boolean b){} public void setMixedContentMode(int m){} public void setSafeBrowsingEnabled(boolean b){} public void setSaveFormData(boolean b){} }''',
"android/webkit/WebView.java": '''
package android.webkit; import android.content.Context;
public class WebView { public WebView(Context c){} public WebSettings getSettings(){return new WebSettings();} public static void setWebContentsDebuggingEnabled(boolean b){} public void setWebViewClient(WebViewClient c){} public void setWebChromeClient(WebChromeClient c){} public void addJavascriptInterface(Object o,String n){} public void loadUrl(String u){} public boolean canGoBack(){return false;} public void goBack(){} public void evaluateJavascript(String s,ValueCallback<String> cb){} }
''',
"android/webkit/ValueCallback.java": '''package android.webkit; public interface ValueCallback<T>{void onReceiveValue(T value);}''',
"android/webkit/JsResult.java": '''package android.webkit; public class JsResult { public void confirm(){} public void cancel(){} }''',
"android/R.java": '''package android; public final class R { public static final class string { public static final int ok=1,cancel=2; } }''',
"android/webkit/WebChromeClient.java": '''package android.webkit; public class WebChromeClient { public boolean onJsAlert(WebView v,String u,String m,JsResult r){return false;} public boolean onJsConfirm(WebView v,String u,String m,JsResult r){return false;} }''',
"android/content/DialogInterface.java": '''package android.content; public interface DialogInterface { interface OnClickListener{void onClick(DialogInterface d,int w);} interface OnCancelListener{void onCancel(DialogInterface d);} }''',
"android/app/AlertDialog.java": '''
package android.app;
import android.content.Context; import android.content.DialogInterface;
public class AlertDialog {
 public static class Builder {
  public Builder(Context c){}
  public Builder setMessage(String m){return this;}
  public Builder setCancelable(boolean b){return this;}
  public Builder setPositiveButton(int textRes, DialogInterface.OnClickListener l){return this;}
  public Builder setNegativeButton(int textRes, DialogInterface.OnClickListener l){return this;}
  public Builder setOnCancelListener(DialogInterface.OnCancelListener l){return this;}
  public AlertDialog show(){return new AlertDialog();}
 }
}
''',
"android/widget/Toast.java": '''package android.widget; import android.content.Context; public class Toast { public static final int LENGTH_SHORT=0; public static Toast makeText(Context c,CharSequence s,int d){return new Toast();} public void show(){} }''',
"android/accessibilityservice/AccessibilityService.java": '''
package android.accessibilityservice; import android.content.Context; import android.view.accessibility.*;
public abstract class AccessibilityService extends Context { public static final int GLOBAL_ACTION_BACK=1; protected void onServiceConnected(){} public abstract void onAccessibilityEvent(AccessibilityEvent e); public abstract void onInterrupt(); public void onDestroy(){} public AccessibilityNodeInfo getRootInActiveWindow(){return null;} public java.util.List<AccessibilityWindowInfo> getWindows(){return java.util.Collections.emptyList();} public AccessibilityNodeInfo findFocus(int focus){return null;} public boolean performGlobalAction(int action){return true;} }
''',
"android/view/accessibility/AccessibilityEvent.java": '''package android.view.accessibility; public class AccessibilityEvent { public static final int TYPE_WINDOWS_CHANGED=4194304; public CharSequence getPackageName(){return null;} public int getEventType(){return 0;} }''',
"android/view/accessibility/AccessibilityWindowInfo.java": '''
package android.view.accessibility;
public class AccessibilityWindowInfo {
 public static final int TYPE_APPLICATION=1;
 public AccessibilityNodeInfo getRoot(){return null;} public AccessibilityNodeInfo getRoot(int flags){return null;} public int getType(){return TYPE_APPLICATION;} public int getLayer(){return 0;}
 public boolean isActive(){return false;} public boolean isFocused(){return false;} public CharSequence getTitle(){return null;}
}
''',
"android/view/accessibility/AccessibilityNodeInfo.java": '''
package android.view.accessibility; import android.os.Bundle; import android.graphics.Rect;
public class AccessibilityNodeInfo {
 public static final int ACTION_SCROLL_FORWARD=4096,ACTION_SCROLL_BACKWARD=8192,ACTION_CLICK=16,ACTION_SET_TEXT=2097152,FOCUS_INPUT=1,FOCUS_ACCESSIBILITY=2;
 public static final String ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE="ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
 public CharSequence getText(){return null;} public CharSequence getContentDescription(){return null;} public CharSequence getHintText(){return null;} public CharSequence getPackageName(){return null;} public CharSequence getClassName(){return null;} public String getViewIdResourceName(){return null;}
 public int getChildCount(){return 0;} public AccessibilityNodeInfo getChild(int i){return null;} public AccessibilityNodeInfo getParent(){return null;}
 public boolean isEditable(){return false;} public boolean isFocused(){return false;} public boolean isMultiLine(){return false;} public boolean isCheckable(){return false;} public boolean isChecked(){return false;} public boolean isClickable(){return false;} public boolean isEnabled(){return false;} public boolean isScrollable(){return false;} public boolean isVisibleToUser(){return true;} public boolean isShowingHintText(){return false;}
 public int getActions(){return 0;} public void getBoundsInScreen(Rect r){} public boolean performAction(int a){return false;} public boolean performAction(int a,Bundle b){return false;}
 public java.util.List<Object> getActionList(){return java.util.Collections.emptyList();}
}
''',
"android/graphics/Rect.java": '''package android.graphics; public class Rect { public int left,top,right,bottom; public Rect(){} public Rect(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;} public int width(){return right-left;} public int height(){return bottom-top;} public int centerX(){return (left+right)/2;} public int centerY(){return (top+bottom)/2;} public boolean isEmpty(){return right<=left||bottom<=top;} public boolean intersect(Rect o){int l=Math.max(left,o.left),t=Math.max(top,o.top),r=Math.min(right,o.right),b=Math.min(bottom,o.bottom); if(r<=l||b<=t)return false; left=l;top=t;right=r;bottom=b;return true;} public boolean equals(Object o){if(!(o instanceof Rect))return false;Rect r=(Rect)o;return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;} }''',
"android/graphics/Color.java": '''package android.graphics; public class Color { public static final int WHITE=0xffffffff; public static int rgb(int r,int g,int b){return 0;} public static int argb(int a,int r,int g,int b){return 0;} }''',
"android/graphics/PixelFormat.java": '''package android.graphics; public class PixelFormat { public static final int TRANSLUCENT=-3, OPAQUE=-1; }''',
"android/graphics/drawable/GradientDrawable.java": '''package android.graphics.drawable; public class GradientDrawable { public static final int OVAL=1; public void setColor(int c){} public void setShape(int s){} public void setCornerRadius(float r){} public void setStroke(int w,int c){} }''',
"android/view/Gravity.java": '''package android.view; public class Gravity { public static final int END=1,START=8,CENTER_VERTICAL=2,TOP=4,CENTER=17,CENTER_HORIZONTAL=1; }''',
"android/view/MotionEvent.java": '''package android.view; public class MotionEvent { public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_OUTSIDE=4; public int getActionMasked(){return 0;} public float getRawX(){return 0f;} public float getRawY(){return 0f;} }''',
"android/view/View.java": '''package android.view; public class View { public static final int VISIBLE=0,GONE=8; public interface OnClickListener{void onClick(View v);} public interface OnTouchListener{boolean onTouch(View v, MotionEvent e);} public void setOnClickListener(OnClickListener l){} public void setOnTouchListener(OnTouchListener l){} public void setVisibility(int v){} public void setBackground(Object d){} }''',
"android/view/WindowManager.java": '''
package android.view;
public interface WindowManager { void addView(View v,LayoutParams p); void removeView(View v); void updateViewLayout(View v,LayoutParams p); public static class LayoutParams { public static final int TYPE_ACCESSIBILITY_OVERLAY=2032,FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCH_MODAL=32,FLAG_WATCH_OUTSIDE_TOUCH=262144,FLAG_LAYOUT_IN_SCREEN=256,WRAP_CONTENT=-2,MATCH_PARENT=-1; public int gravity,x,y,flags; public LayoutParams(int w,int h,int t,int f,int p){flags=f;} } }
''',
"android/widget/TextView.java": '''package android.widget; import android.content.Context; import android.view.View; public class TextView extends View { public TextView(Context c){} public void setText(CharSequence s){} public void setTextSize(float s){} public void setTextColor(int c){} public void setPadding(int a,int b,int c,int d){} }''',
"android/widget/Button.java": '''package android.widget; import android.content.Context; public class Button extends TextView { public Button(Context c){super(c);} public void setAllCaps(boolean b){} public void setBackground(Object d){} }''',
"android/widget/LinearLayout.java": '''
package android.widget;
import android.content.Context; import android.view.View; import android.view.ViewGroup;
public class LinearLayout extends View {
 public static final int VERTICAL=1;
 public LinearLayout(Context c){}
 public void setOrientation(int o){}
 public void setPadding(int a,int b,int c,int d){}
 public void setBackground(Object d){}
 public void addView(View v){}
 public void addView(View v, ViewGroup.LayoutParams p){}
 public static class LayoutParams extends ViewGroup.LayoutParams {
  public static final int WRAP_CONTENT=-2, MATCH_PARENT=-1;
  public int gravity, topMargin;
  public LayoutParams(int w,int h){super(w,h);}
 }
}
''',
"android/widget/FrameLayout.java": '''
package android.widget;
import android.content.Context; import android.view.View; import android.view.ViewGroup;
public class FrameLayout extends View {
 public FrameLayout(Context c){}
 public void addView(View v){}
 public void addView(View v, ViewGroup.LayoutParams p){}
 public static class LayoutParams extends ViewGroup.LayoutParams {
  public static final int WRAP_CONTENT=-2, MATCH_PARENT=-1;
  public int gravity;
  public LayoutParams(int w,int h){super(w,h);}
 }
}
''',
"android/view/ViewGroup.java": '''
package android.view;
public class ViewGroup extends View {
 public static class LayoutParams { public int width,height; public LayoutParams(int w,int h){width=w;height=h;} }
}
''',
"android/widget/ProgressBar.java": '''package android.widget; import android.content.Context; import android.view.View; public class ProgressBar extends View { public ProgressBar(Context c){} public void setIndeterminate(boolean b){} }''',
"android/widget/ImageView.java": '''package android.widget; import android.content.Context; import android.view.View; public class ImageView extends View { public enum ScaleType { FIT_CENTER } public ImageView(Context c){} public void setImageResource(int id){} public void setScaleType(ScaleType t){} }''',
"com/aleyon/geminibridge/R.java": '''package com.aleyon.geminibridge; public final class R { public static final class mipmap { public static final int ic_launcher=1,ic_launcher_round=2; } }''',
"org/json/JSONException.java": '''package org.json; public class JSONException extends Exception { public JSONException(String s){super(s);} }''',
"org/json/JSONObject.java": '''
package org.json;
public class JSONObject { public JSONObject(){} public JSONObject(String s) throws JSONException{} public JSONObject put(String k,Object v) throws JSONException{return this;} public String getString(String k) throws JSONException{return "";} public String optString(String k,String d){return d;} public String optString(String k){return "";} public boolean optBoolean(String k,boolean d){return d;} public int optInt(String k,int d){return d;} public long optLong(String k,long d){return d;} public JSONArray optJSONArray(String k){return null;} public JSONObject optJSONObject(String k){return null;} public String toString(){return "{}";} }
''',
"org/json/JSONArray.java": '''package org.json; public class JSONArray { public JSONArray(){} public JSONArray(String s) throws JSONException{} public JSONArray put(Object v){return this;} public int length(){return 0;} public Object get(int i) throws JSONException{return null;} public JSONObject getJSONObject(int i) throws JSONException{return null;} public JSONObject optJSONObject(int i){return null;} public String toString(){return "[]";} }''',
}

for rel, content in STUBS.items():
    p = src / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(textwrap.dedent(content), encoding="utf-8")

prod = [str(p) for p in (ROOT / "app/src/main/java").rglob("*.java")]
stubs = [str(p) for p in src.rglob("*.java")]
cmd = ["javac", "-source", "17", "-target", "17", "-d", str(out)] + stubs + prod
proc = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if proc.returncode:
    print("FAIL full Java stub compile")
    print(proc.stdout)
    print(proc.stderr)
    sys.exit(proc.returncode)
print(f"PASS full Java stub compile: {len(prod)} production files")
shutil.rmtree(TMP, ignore_errors=True)
