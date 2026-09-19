package com.aleyon.geminibridge.transport;

import android.view.accessibility.AccessibilityNodeInfo;

import com.aleyon.geminibridge.automation.GeminiUi;

/**
 * Narrow provider transport for Aleyon Bridge.
 *
 * Provider history, conversation titles and recovery navigation are deliberately
 * not part of this interface. Each explicit Bridge session uses a fresh normal
 * Gemini chat; Aleyon owns continuity locally.
 */
public final class GeminiConversationTransport {
    public boolean createNormalConversation(AccessibilityNodeInfo root){
        return GeminiUi.clickNormalNewChat(root);
    }

    public boolean isBlankConversation(AccessibilityNodeInfo root){
        return GeminiUi.isBlankNormalChat(root);
    }

    public boolean writeContext(AccessibilityNodeInfo root,String payload){
        return GeminiUi.writeComposer(root,payload);
    }

    public boolean isContextPrepared(AccessibilityNodeInfo root,String payload){
        return GeminiUi.composerContainsExactText(root,payload);
    }

    public boolean submitPreparedContext(AccessibilityNodeInfo root){
        return GeminiUi.clickSendAction(root);
    }

    public boolean startLive(AccessibilityNodeInfo root){
        return GeminiUi.clickGeminiLive(root);
    }

    public boolean exploreConversationForward(AccessibilityNodeInfo root){return GeminiUi.scrollForward(root);}
    public boolean exploreConversationBackward(AccessibilityNodeInfo root){return GeminiUi.scrollBackward(root);}

    public boolean isLiveActive(AccessibilityNodeInfo root){
        return GeminiUi.isLiveScreen(root);
    }

    public boolean endLive(AccessibilityNodeInfo root){
        return GeminiUi.clickEndLive(root);
    }

    public boolean openNativeAttachmentSurface(AccessibilityNodeInfo root){
        return GeminiUi.clickAddFiles(root);
    }

    public boolean isGeminiSurface(AccessibilityNodeInfo root){
        return GeminiUi.isGeminiRoot(root);
    }

    public boolean hasComposer(AccessibilityNodeInfo root){
        return GeminiUi.chatComposer(root)!=null;
    }

    public boolean clearComposer(AccessibilityNodeInfo root){
        return GeminiUi.clearComposer(root);
    }

    public String collectConversationText(AccessibilityNodeInfo root){
        return GeminiUi.collectAllText(root);
    }

    public boolean scrollConversation(AccessibilityNodeInfo root){
        return GeminiUi.scrollForward(root);
    }
}
