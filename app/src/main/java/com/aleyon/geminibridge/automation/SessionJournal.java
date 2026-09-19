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

/**
 * Persistent profile index + crash-safe transaction journal.
 * Gemini UI actions are not atomic, therefore every externally-visible phase
 * is journaled before the next action is attempted.
 */
public final class SessionJournal {
    private static final String PREFS="aleyon_session_journal";
    private static final String PROFILE_INDEX="profile_index";
    private static final int MAX_SUMMARY_HISTORY=8;
    private static final int ERROR_HISTORY_LIMIT=12;
    private final SharedPreferences prefs;

    public SessionJournal(Context context){
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public void saveProfile(ProfileSpec p) throws JSONException {
        Set<String> ids=new LinkedHashSet<>(prefs.getStringSet(PROFILE_INDEX,Collections.emptySet()));
        ids.add(p.id);
        prefs.edit().putString("profile:"+p.id,p.toJson().toString())
                .putStringSet(PROFILE_INDEX,ids).apply();
    }

    public ProfileSpec loadProfile(String id){
        String raw=prefs.getString("profile:"+id,null);
        if(raw==null)return null;
        try{return ProfileSpec.fromJson(raw);}catch(Exception e){return null;}
    }

    public JSONArray allProfilesJson(){
        JSONArray out=new JSONArray();
        for(String id:prefs.getStringSet(PROFILE_INDEX,Collections.emptySet())){
            String raw=prefs.getString("profile:"+id,null);
            if(raw!=null)try{out.put(new JSONObject(raw));}catch(Exception ignored){}
        }
        return out;
    }

    public void stage(String id,SessionStage s){
        prefs.edit().putString("stage:"+id,s.name())
                .putLong("stage_ts:"+id,System.currentTimeMillis()).apply();
    }

    public SessionStage stage(String id){
        try{
            String raw=prefs.getString("stage:"+id,SessionStage.READY.name());
            // Legacy builds could persist RECOVERING. Recovery is no longer a
            // product state; old installs migrate that value to READY.
            if("RECOVERING".equals(raw)){
                prefs.edit().putString("stage:"+id,SessionStage.READY.name())
                        .remove("recoverable_stage:"+id).apply();
                return SessionStage.READY;
            }
            return SessionStage.valueOf(raw);
        }catch(Exception e){return SessionStage.ERROR;}
    }

    public long stageTimestamp(String id){return prefs.getLong("stage_ts:"+id,0L);}

    public void activeSession(String id,String sessionId,String mode){
        prefs.edit().putString("session_id:"+id,safe(sessionId))
                .putString("session_mode:"+id,safe(mode)).apply();
    }
    public String activeSessionId(String id){return prefs.getString("session_id:"+id,"");}
    public String activeSessionMode(String id){return prefs.getString("session_mode:"+id,"LIVE");}

    /** Bottom-of-chat snapshot taken immediately before the session capsule. */
    public void baselineText(String id,String text){
        String v=safe(text);
        if(v.length()>32000)v=v.substring(v.length()-32000);
        prefs.edit().putString("baseline:"+id,v).apply();
    }
    public String baselineText(String id){return prefs.getString("baseline:"+id,"");}

    public void sessionHints(String id,String objective,String starter){
        prefs.edit().putString("objective:"+id,safe(objective))
                .putString("starter:"+id,safe(starter)).apply();
    }

    public void appendCloseSummary(String id,String summary){
        JSONArray old=closeSummaryHistory(id), updated=new JSONArray();
        try{
            updated.put(new JSONObject().put("ts",System.currentTimeMillis())
                    .put("summary",safe(summary)));
            for(int i=0;i<old.length()&&updated.length()<MAX_SUMMARY_HISTORY;i++)updated.put(old.get(i));
        }catch(Exception ignored){}
        prefs.edit().putString("close_summary_history:"+id,updated.toString()).apply();
    }

    public JSONArray closeSummaryHistory(String id){
        try{return new JSONArray(prefs.getString("close_summary_history:"+id,"[]"));}
        catch(Exception e){return new JSONArray();}
    }

    public String closeSummary(String id){
        JSONArray h=closeSummaryHistory(id);
        if(h.length()==0)return "";
        try{return h.getJSONObject(0).optString("summary","");}catch(Exception e){return "";}
    }

    public JSONObject statusJson(String id) throws JSONException {
        return new JSONObject().put("profileId",id).put("stage",stage(id).name())
                .put("timestamp",stageTimestamp(id))
                .put("sessionId",activeSessionId(id)).put("mode",activeSessionMode(id))
                .put("objectiveToday",prefs.getString("objective:"+id,""))
                .put("starterPhrase",prefs.getString("starter:"+id,""))
                .put("lastSessionSummary",closeSummary(id))
                .put("lastError",lastError(id));
    }

    public void error(String id,String message,SessionStage terminal){
        prefs.edit().putString("error:"+id,safe(message)).putString("stage:"+id,terminal.name())
                .putLong("stage_ts:"+id,System.currentTimeMillis()).apply();
    }
    public void clearError(String id){prefs.edit().remove("error:"+id).apply();}
    public String lastError(String id){return prefs.getString("error:"+id,"");}

    public void appendErrorHistory(String id,String message,SessionStage stage){
        JSONArray history;
        try{history=new JSONArray(prefs.getString("error_history:"+id,"[]"));}
        catch(Exception e){history=new JSONArray();}
        JSONArray trimmed=new JSONArray();
        int start=Math.max(0,history.length()-(ERROR_HISTORY_LIMIT-1));
        for(int i=start;i<history.length();i++)try{trimmed.put(history.get(i));}catch(Exception ignored){}
        try{trimmed.put(new JSONObject().put("ts",System.currentTimeMillis())
                .put("stage",stage.name()).put("message",safe(message)));}catch(Exception ignored){}
        prefs.edit().putString("error_history:"+id,trimmed.toString()).apply();
    }
    public JSONArray errorHistory(String id){
        try{return new JSONArray(prefs.getString("error_history:"+id,"[]"));}
        catch(Exception e){return new JSONArray();}
    }

    public void clearActiveSession(String id){
        prefs.edit().remove("session_id:"+id).remove("session_mode:"+id).remove("baseline:"+id).apply();
    }

    public void removeProfile(String id){
        Set<String> ids=new LinkedHashSet<>(prefs.getStringSet(PROFILE_INDEX,Collections.emptySet()));
        ids.remove(id);
        prefs.edit().putStringSet(PROFILE_INDEX,ids)
                .remove("profile:"+id).remove("stage:"+id).remove("stage_ts:"+id)
                .remove("objective:"+id).remove("starter:"+id)
                .remove("session_id:"+id).remove("session_mode:"+id).remove("baseline:"+id)
                .remove("close_summary_history:"+id).remove("error:"+id)
                .remove("error_history:"+id).apply();
    }

    private static String safe(String v){return v==null?"":v;}
}
