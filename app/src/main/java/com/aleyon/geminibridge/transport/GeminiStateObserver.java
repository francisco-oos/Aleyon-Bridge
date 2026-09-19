package com.aleyon.geminibridge.transport;

import android.view.accessibility.AccessibilityNodeInfo;

import com.aleyon.geminibridge.automation.GeminiUi;
import com.aleyon.geminibridge.core.TransportState;

/**
 * Artemis-inspired perception boundary: observe first, then act.
 *
 * This class deliberately classifies semantic states instead of exposing the
 * caller to device/build-specific UI details.
 */
public final class GeminiStateObserver {
    private GeminiStateObserver() {}

    public static TransportObservation observe(AccessibilityNodeInfo root, String canonicalTitle) {
        if(root==null) return new TransportObservation(TransportState.UNAVAILABLE,false,false,false,"root-unavailable");
        if(GeminiUi.isBlockingConsentDialog(root))
            return new TransportObservation(TransportState.CONSENT_REQUIRED,false,false,false,"human-consent");
        if(GeminiUi.isLiveScreen(root))
            return new TransportObservation(TransportState.LIVE_ACTIVE,false,false,false,"live-active");
        boolean canonical=canonicalTitle!=null&&!canonicalTitle.trim().isEmpty()
                && GeminiUi.hasExact(root,canonicalTitle);
        if(GeminiUi.isNavigationDrawerOpen(root))
            return new TransportObservation(TransportState.CONVERSATION_LIST,false,false,canonical,"conversation-list");
        if(GeminiUi.isTemporaryChat(root))
            return new TransportObservation(TransportState.TEMPORARY_CHAT,
                    GeminiUi.chatComposer(root)!=null,false,false,"temporary-chat");
        if(GeminiUi.isGeminiRoot(root)&&GeminiUi.chatComposer(root)!=null) {
            boolean live=GeminiUi.hasVisibleGeminiLiveLauncher(root);
            return new TransportObservation(TransportState.NORMAL_CHAT,true,live,canonical,
                    live?"normal-chat+live":"normal-chat");
        }
        return new TransportObservation(TransportState.UNKNOWN,false,false,canonical,"unclassified-gemini-state");
    }
}
