package com.aleyon.geminibridge.transport;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Small local "immune memory" for validated transport routes.
 * It stores no learner content and grants no new device capability.
 */
public final class CompatibilityMemory {
    private static final String PREFS="aleyon_transport_compat_v1";
    private final SharedPreferences prefs;
    public CompatibilityMemory(Context context){prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public void recordSuccess(String profileId,String route,String evidence){
        String id=safe(profileId);prefs.edit().putString("route:"+id,safe(route))
                .putString("evidence:"+id,clip(evidence,400))
                .putLong("success_ts:"+id,System.currentTimeMillis()).apply();
    }
    public void recordFailure(String profileId,String evidence){
        String id=safe(profileId);int n=failureCount(id)+1;
        prefs.edit().putString("failures:"+id,Integer.toString(n))
                .putString("last_failure:"+id,clip(evidence,400))
                .putLong("failure_ts:"+id,System.currentTimeMillis()).apply();
    }
    public String preferredRoute(String profileId){return prefs.getString("route:"+safe(profileId),"");}
    public int failureCount(String profileId){
        try{return Integer.parseInt(prefs.getString("failures:"+safe(profileId),"0"));}
        catch(Exception e){return 0;}
    }
    public JSONObject status(String profileId) throws JSONException {
        String id=safe(profileId);return new JSONObject().put("preferredRoute",preferredRoute(id))
                .put("failureCount",failureCount(id)).put("evidence",prefs.getString("evidence:"+id,""))
                .put("lastFailure",prefs.getString("last_failure:"+id,""));
    }
    public void remove(String profileId){
        String id=safe(profileId);prefs.edit().remove("route:"+id).remove("evidence:"+id)
                .remove("success_ts:"+id).remove("failures:"+id).remove("last_failure:"+id)
                .remove("failure_ts:"+id).apply();
    }
    private static String safe(String v){return v==null?"":v.trim();}
    private static String clip(String v,int n){String s=safe(v);return s.length()<=n?s:s.substring(0,n);}
}
