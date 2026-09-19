package com.aleyon.geminibridge.automation;

import org.json.JSONException;
import org.json.JSONObject;

/** Serializable command placed in the local command bus. */
public final class AutomationRequest {
    public enum Type {
        DIAGNOSTIC_PROBE,
        START_LIVE_SESSION,
        START_CHAT_SESSION,
        CLOSE_SESSION
    }

    public final Type type;
    public final String profileJson;
    public final boolean destructiveConfirmed;

    public AutomationRequest(Type type,String profileJson,boolean destructiveConfirmed){
        this.type=type;
        this.profileJson=profileJson==null?"":profileJson;
        this.destructiveConfirmed=destructiveConfirmed;
    }

    public String toJson() throws JSONException {
        return new JSONObject().put("type",type.name()).put("profileJson",profileJson)
                .put("destructiveConfirmed",destructiveConfirmed).toString();
    }

    public static AutomationRequest fromJson(String json) throws JSONException {
        JSONObject o=new JSONObject(json);
        return new AutomationRequest(Type.valueOf(o.getString("type")),
                o.optString("profileJson",""),o.optBoolean("destructiveConfirmed",false));
    }
}
