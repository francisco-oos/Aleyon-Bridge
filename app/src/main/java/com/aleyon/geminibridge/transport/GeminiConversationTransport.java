package com.aleyon.geminibridge.transport;

import android.view.accessibility.AccessibilityNodeInfo;

import com.aleyon.geminibridge.automation.GeminiUi;

/**
 * Narrow, allow-listed Gemini transport used by Bridge.
 *
 * No shell, package installation, arbitrary URL, arbitrary app control or
 * coordinate gesture is exposed here. This is intentionally much smaller than
 * a general Artemis runtime.
 */
public final class GeminiConversationTransport {
    public boolean openConversationList(AccessibilityNodeInfo root){return GeminiUi.clickNavigationToggle(root);}
    public boolean hasCanonicalConversation(AccessibilityNodeInfo root,String title){return GeminiUi.hasConversationTitle(root,title);}
    public boolean openCanonicalConversation(AccessibilityNodeInfo root,String title){return GeminiUi.clickConversationByTitle(root,title);}
    public boolean openConversationSearch(AccessibilityNodeInfo root){return GeminiUi.clickConversationSearch(root);}
    public boolean searchConversation(AccessibilityNodeInfo root,String title){return GeminiUi.setConversationSearchQuery(root,title);}
    public boolean createNormalConversation(AccessibilityNodeInfo root){return GeminiUi.clickNormalNewChat(root);}
    public boolean writeContext(AccessibilityNodeInfo root,String payload){return GeminiUi.writeComposer(root,payload);}
    public boolean isContextPrepared(AccessibilityNodeInfo root,String payload){return GeminiUi.composerContainsExactText(root,payload);}
    public boolean submitPreparedContext(AccessibilityNodeInfo root){return GeminiUi.clickSendAction(root);}
    public boolean sendContext(AccessibilityNodeInfo root,String payload){return GeminiUi.sendMessage(root,payload);}
    public boolean startLive(AccessibilityNodeInfo root){return GeminiUi.clickGeminiLive(root);}
    public boolean isLiveActive(AccessibilityNodeInfo root){return GeminiUi.isLiveScreen(root);}
    public boolean endLive(AccessibilityNodeInfo root){return GeminiUi.clickEndLive(root);}
    public boolean openNativeAttachmentSurface(AccessibilityNodeInfo root){return GeminiUi.clickAddFiles(root);}
    public boolean isGeminiSurface(AccessibilityNodeInfo root){return GeminiUi.isGeminiRoot(root);}
    public boolean hasComposer(AccessibilityNodeInfo root){return GeminiUi.chatComposer(root)!=null;}
    public boolean clearComposer(AccessibilityNodeInfo root){return GeminiUi.clearComposer(root);}
    public String collectConversationText(AccessibilityNodeInfo root){return GeminiUi.collectAllText(root);}
    public boolean scrollConversation(AccessibilityNodeInfo root){return GeminiUi.scrollForward(root);}
    public boolean openChatOptions(AccessibilityNodeInfo root){return GeminiUi.clickMoreOptionsMenu(root);}
    public boolean chooseRename(AccessibilityNodeInfo root){return GeminiUi.clickRenameConversation(root);}
    public boolean setCanonicalTitle(AccessibilityNodeInfo root,String title){return GeminiUi.setConversationTitle(root,title);}
    public boolean saveCanonicalTitle(AccessibilityNodeInfo root){return GeminiUi.saveConversationTitle(root);}
}
