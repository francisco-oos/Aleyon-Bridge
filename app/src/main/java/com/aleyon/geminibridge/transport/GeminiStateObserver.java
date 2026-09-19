package com.aleyon.geminibridge.transport;

import android.view.accessibility.AccessibilityNodeInfo;

import com.aleyon.geminibridge.automation.GeminiUi;
import com.aleyon.geminibridge.core.TransportState;

/**
 * Artemis-inspired perception boundary: observe first, then act.
 *
 * It classifies only the current Gemini surface. Conversation titles and
 * provider history are deliberately outside the model because Bridge does not
 * depend on them for continuity.
 */
public final class GeminiStateObserver {
    private GeminiStateObserver() {}

    public static TransportObservation observe(AccessibilityNodeInfo root) {
        if(root==null)
            return new TransportObservation(TransportState.UNAVAILABLE,false,false,"root-unavailable");
        if(GeminiUi.isBlockingConsentDialog(root))
            return new TransportObservation(TransportState.CONSENT_REQUIRED,false,false,"human-consent");
        if(GeminiUi.isConversationSearchOpen(root))
            return new TransportObservation(TransportState.CONVERSATION_SEARCH,false,false,"conversation-search");
        if(GeminiUi.isLiveScreen(root))
            return new TransportObservation(TransportState.LIVE_ACTIVE,false,false,"live-active");
        if(GeminiUi.isNavigationDrawerOpen(root))
            return new TransportObservation(TransportState.CONVERSATION_LIST,false,false,"conversation-list");
        if(GeminiUi.isTemporaryChat(root))
            return new TransportObservation(TransportState.TEMPORARY_CHAT,
                    GeminiUi.chatComposer(root)!=null,false,"temporary-chat");
        if(GeminiUi.isGeminiRoot(root)&&GeminiUi.hasConversationViewport(root)) {
            boolean composer=GeminiUi.chatComposer(root)!=null;
            boolean live=composer&&GeminiUi.hasVisibleGeminiLiveLauncher(root);
            return new TransportObservation(TransportState.NORMAL_CHAT,composer,live,
                    live?"normal-chat+live":composer?"normal-chat":"normal-chat-scrolled");
        }
        return new TransportObservation(TransportState.UNKNOWN,false,false,"unclassified-gemini-state");
    }
}
