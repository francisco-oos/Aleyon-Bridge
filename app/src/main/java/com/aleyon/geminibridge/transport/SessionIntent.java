package com.aleyon.geminibridge.transport;

import com.aleyon.geminibridge.core.SessionMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small transport contract. It describes the desired session, not a sequence
 * of taps. Study materials are metadata-only references: Android/Gemini owns
 * the actual file-byte handoff while the adaptive transport coordinates and
 * verifies the surrounding Gemini flow.
 */
public final class SessionIntent {
    public enum Mode { CHAT, LIVE }
    public final String profileId;
    public final String sessionId;
    public final String canonicalConversationTitle;
    public final Mode mode;
    public final String contextPayload;
    public final boolean reconstructConversation;
    public final List<SessionMaterial> materials;

    public SessionIntent(String profileId,String sessionId,String canonicalConversationTitle,
            Mode mode,String contextPayload,boolean reconstructConversation){
        this(profileId,sessionId,canonicalConversationTitle,mode,contextPayload,
                reconstructConversation,Collections.emptyList());
    }

    public SessionIntent(String profileId,String sessionId,String canonicalConversationTitle,
            Mode mode,String contextPayload,boolean reconstructConversation,
            List<SessionMaterial> materials){
        this.profileId=profileId==null?"":profileId;
        this.sessionId=sessionId==null?"":sessionId;
        this.canonicalConversationTitle=canonicalConversationTitle==null?"":canonicalConversationTitle;
        this.mode=mode==null?Mode.LIVE:mode;
        this.contextPayload=contextPayload==null?"":contextPayload;
        this.reconstructConversation=reconstructConversation;
        this.materials=materials==null?Collections.emptyList():
                Collections.unmodifiableList(new ArrayList<>(materials));
    }
}
