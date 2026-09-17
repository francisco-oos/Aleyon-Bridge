package com.aleyon.geminibridge.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.aleyon.geminibridge.core.SessionStage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Crash-safe local transaction journal plus native profile index. */
public final class SessionJournal {
    private static final String PREFS="aleyon_session_journal", PROFILE_INDEX="profile_index";
    private final SharedPreferences prefs;
    public SessionJournal(Context c){prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public void saveProfile(ProfileSpec p) throws JSONException {
        Set<String> ids=new LinkedHashSet<>(prefs.getStringSet(PROFILE_INDEX, Collections.emptySet())); ids.add(p.id);
        prefs.edit().putString("profile:"+p.id,p.toJson().toString()).putStringSet(PROFILE_INDEX,ids).apply();
    }
    public ProfileSpec loadProfile(String id){String raw=prefs.getString("profile:"+id,null);if(raw==null)return null;try{return ProfileSpec.fromJson(raw);}catch(Exception e){return null;}}
    public JSONArray allProfilesJson(){JSONArray out=new JSONArray();for(String id:prefs.getStringSet(PROFILE_INDEX,Collections.emptySet())){String raw=prefs.getString("profile:"+id,null);if(raw!=null)try{out.put(new JSONObject(raw));}catch(Exception ignored){}}return out;}
    public void stage(String id,SessionStage s){prefs.edit().putString("stage:"+id,s.name()).putLong("stage_ts:"+id,System.currentTimeMillis()).apply();}
    public SessionStage stage(String id){try{return SessionStage.valueOf(prefs.getString("stage:"+id,SessionStage.READY.name()));}catch(Exception e){return SessionStage.ERROR;}}
    public long stageTimestamp(String id){return prefs.getLong("stage_ts:"+id,0L);}
    public void sessionHints(String id,String objective,String starter){prefs.edit().putString("objective:"+id,safe(objective)).putString("starter:"+id,safe(starter)).apply();}
    public void activeSession(String id,String sessionId,String mode){prefs.edit().putString("session_id:"+id,safe(sessionId)).putString("session_mode:"+id,safe(mode)).apply();}
    public void baselineText(String id,String text){String v=safe(text);if(v.length()>24000)v=v.substring(v.length()-24000);prefs.edit().putString("baseline:"+id,v).apply();}
    public String baselineText(String id){return prefs.getString("baseline:"+id,"");}
    public String activeSessionId(String id){return prefs.getString("session_id:"+id,"");}
    public String activeSessionMode(String id){return prefs.getString("session_mode:"+id,"LIVE");}
    public JSONObject statusJson(String id) throws JSONException {return new JSONObject().put("profileId",id).put("stage",stage(id).name()).put("timestamp",stageTimestamp(id)).put("sessionId",activeSessionId(id)).put("mode",activeSessionMode(id)).put("objectiveToday",prefs.getString("objective:"+id,"")).put("starterPhrase",prefs.getString("starter:"+id,"")).put("lastError",prefs.getString("error:"+id,""));}
    public void error(String id,String message,SessionStage terminal){prefs.edit().putString("error:"+id,safe(message)).putString("stage:"+id,terminal.name()).putLong("stage_ts:"+id,System.currentTimeMillis()).apply();}
    public void clearError(String id){prefs.edit().remove("error:"+id).apply();}
    public void clearActiveSession(String id){prefs.edit().remove("session_id:"+id).remove("session_mode:"+id).remove("baseline:"+id).apply();}
    public void removeProfile(String id){Set<String> ids=new LinkedHashSet<>(prefs.getStringSet(PROFILE_INDEX,Collections.emptySet()));ids.remove(id);prefs.edit().putStringSet(PROFILE_INDEX,ids).remove("profile:"+id).remove("stage:"+id).remove("stage_ts:"+id).remove("objective:"+id).remove("starter:"+id).remove("session_id:"+id).remove("session_mode:"+id).remove("baseline:"+id).remove("error:"+id).apply();}
    private static String safe(String v){return v==null?"":v;}
}
