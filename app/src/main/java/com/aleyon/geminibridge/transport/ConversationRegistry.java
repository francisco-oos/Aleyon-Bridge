package com.aleyon.geminibridge.transport;

import android.content.Context;
import android.content.SharedPreferences;

import com.aleyon.geminibridge.automation.ProfileSpec;
import com.aleyon.geminibridge.core.CanonicalConversationPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Local record of the reusable Gemini conversation for each profile.
 *
 * No provider conversation id is required. The deterministic title is a
 * navigation aid only; local learner memory remains authoritative.
 */
public final class ConversationRegistry {
    private static final String PREFS="aleyon_conversation_registry_v1";
    private final SharedPreferences prefs;

    public ConversationRegistry(Context context){
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public String title(ProfileSpec p){
        return CanonicalConversationPolicy.title(p==null?"":p.targetLanguage);
    }

    public boolean isKnown(String profileId){return prefs.getBoolean("known:"+safe(profileId),false);}
    public long lastVerifiedAt(String profileId){return prefs.getLong("verified:"+safe(profileId),0L);}
    public int rebuildCount(String profileId){
        String raw=prefs.getString("rebuilds:"+safe(profileId),"0");
        try{return Integer.parseInt(raw);}catch(Exception e){return 0;}
    }

    public void markVerified(ProfileSpec p, boolean rebuilt){
        if(p==null)return;
        String id=safe(p.id);SharedPreferences.Editor e=prefs.edit()
                .putBoolean("known:"+id,true)
                .putString("title:"+id,title(p))
                .putLong("verified:"+id,System.currentTimeMillis());
        if(rebuilt)e.putString("rebuilds:"+id,Integer.toString(rebuildCount(id)+1));
        e.apply();
    }

    public void markMissing(ProfileSpec p){
        if(p==null)return;
        prefs.edit().putBoolean("known:"+safe(p.id),false).apply();
    }

    public JSONObject status(ProfileSpec p) throws JSONException {
        String id=p==null?"":safe(p.id);
        return new JSONObject().put("title",p==null?"":title(p))
                .put("known",isKnown(id)).put("lastVerifiedAt",lastVerifiedAt(id))
                .put("rebuildCount",rebuildCount(id));
    }

    public void remove(String profileId){
        String id=safe(profileId);prefs.edit().remove("known:"+id).remove("title:"+id)
                .remove("verified:"+id).remove("rebuilds:"+id).apply();
    }

    private static String safe(String v){return v==null?"":v.trim();}
}
