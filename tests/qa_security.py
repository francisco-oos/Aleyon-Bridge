#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def check(cond,msg):
    if not cond: errors.append(msg)
def read(rel): return (ROOT/rel).read_text(encoding='utf-8',errors='replace')
manifest=read('app/src/main/AndroidManifest.xml')
config=read('app/src/main/res/xml/accessibility_service_config.xml')
main=read('app/src/main/java/com/aleyon/geminibridge/MainActivity.java')
service=read('app/src/main/java/com/aleyon/geminibridge/automation/AleyonAccessibilityService.java')
transport=read('app/src/main/java/com/aleyon/geminibridge/transport/GeminiConversationTransport.java')
notification=read('app/src/main/java/com/aleyon/geminibridge/automation/NotificationHelper.java')
java='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in (ROOT/'app/src/main/java').rglob('*.java'))

# Permission minimization.
for permission in [
    'android.permission.INTERNET','android.permission.RECORD_AUDIO',
    'android.permission.CAMERA','android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE','android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.QUERY_ALL_PACKAGES','android.permission.SYSTEM_ALERT_WINDOW',
    'android.permission.REQUEST_INSTALL_PACKAGES','android.permission.PACKAGE_USAGE_STATS'
]: check(permission not in manifest,f'forbidden/high-authority permission present: {permission}')
check('android.permission.POST_NOTIFICATIONS' in manifest,'expected optional notification permission missing')
check('requestNotificationPermissionIfNeeded' not in main and 'requestPermissions(' not in main,
      'optional notifications must never trigger a runtime permission dialog at startup')
check('checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)' in notification,
      'NotificationHelper must fail closed when notification permission is absent')
check('android:allowBackup="false"' in manifest,'local learner memory must not be Android-backup enabled')
check('android:usesCleartextTraffic="false"' in manifest,'cleartext traffic must stay disabled')

# Accessibility is explicitly scoped and cannot inject gestures.
check('android:packageNames="com.google.android.apps.bard,com.google.android.googlequicksearchbox"' in config,'accessibility package allow-list drifted')
check('android:canPerformGestures="false"' in config,'accessibility gestures unexpectedly enabled')
check('android:canRetrieveWindowContent="true"' in config,'required semantic observation capability missing')

# No general command execution or Artemis vulnerable surfaces in production APK.
for token in ['Runtime.getRuntime().exec','new ProcessBuilder','ProcessBuilder(','executeShell(','run_adb_command','adb shell','ARTEMIS_NOTIFY_CMD','MCP_NOTIFY_COMMAND','subprocess','shell=True']:
    check(token not in java,f'general command/shell capability leaked into APK: {token}')
check('dispatchGesture(' not in java,'coordinate gesture injection leaked into APK')
check('isBlockingConsentDialog(blocker)' in service and 'overlay.hide();' in service,
      'human consent dialogs must remove Aleyon overlays before accepting touch')
check('overlay.showNotice("Gemini requiere una decisión tuya' not in service,
      'consent flow must not place an accessibility overlay over the human decision')
check('openNativeAttachmentSurface' in transport,'narrow native attachment surface missing')
check('clickEndLive' in transport and 'clickAny(root,"Cerrar"' not in transport,'provider labels leaked into transport boundary')

# WebView is local-only and hostile navigation is blocked.
check('setAllowFileAccess(false)' in main,'WebView file access must stay disabled')
check('setAllowContentAccess(false)' in main,'WebView content access must stay disabled')
check('setAllowUniversalAccessFromFileURLs(false)' in main,'WebView universal file URL access must stay disabled')
check('setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)' in main,'WebView mixed content is not blocked')
check('WebView.setWebContentsDebuggingEnabled(false)' in main,'WebView debugging must stay disabled')
check('file:///android_asset/' in main and 'shouldOverrideUrlLoading' in main,'WebView local navigation allow-list missing')

# Export surface: only launcher activity is intentionally exported; Accessibility service is binder-protected.
service_decl=re.search(r'<service\b.*?</service>',manifest,re.S)
check(service_decl is not None,'Accessibility service declaration missing')
if service_decl:
    block=service_decl.group(0)
    check('android:exported="false"' in block,'Accessibility service must not be exported')
    check('android.permission.BIND_ACCESSIBILITY_SERVICE' in block,'Accessibility binder permission missing')

# Materials: never accept filesystem paths as automatic provider handoff.
material=read('app/src/main/java/com/aleyon/geminibridge/core/MaterialHandoffPolicy.java')
check('startsWith("content://")' in material,'material handoff must require content:// URI')
check('MAX_ITEMS=10' in material,'material count guard missing')
check('file://' not in material.replace('file:// paths',''),'Material policy unexpectedly accepts/directly handles file://')

if errors:
    print('FAIL security QA'); [print(' -',e) for e in errors]; sys.exit(1)
print('PASS security QA')
