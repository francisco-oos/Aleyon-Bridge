package com.aleyon.geminibridge.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.aleyon.geminibridge.core.LearningEvent;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.SessionReportParser;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local-first pedagogical memory. Gemini is a session engine, never the source of truth. */
public final class LearningStore {
    private static final String PREFS="aleyon_learning_v1";
    private final SharedPreferences prefs;
    public LearningStore(Context c){prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public LearningLedger load(String profileId){
        LearningLedger l=new LearningLedger();
        String raw=prefs.getString("ledger:"+profileId,""); if(raw==null||raw.isEmpty())return l;
        try{
            JSONObject r=new JSONObject(raw); l.setLastSessionSummary(r.optString("summary","")); l.setNextObjective(r.optString("next",""));
            JSONArray a=r.optJSONArray("events"); if(a!=null) for(int i=0;i<a.length();i++){
                JSONObject e=a.optJSONObject(i); if(e!=null) l.addVerified(new LearningEvent(e.optString("category"),e.optString("skill"),e.optString("status"),e.optString("evidence"),e.optLong("ts",0)));
            }
        }catch(Exception ignored){}
        return l;
    }

    public void commit(String profileId, SessionReportParser.Report report, String sessionId){
        if(report==null)return;
        LearningLedger l=load(profileId);
        if(!report.summary.isEmpty())l.setLastSessionSummary(clip(report.summary,4000));
        if(!report.nextObjective.isEmpty())l.setNextObjective(clip(report.nextObjective,1200));
        for(LearningEvent e:report.events)l.addVerified(new LearningEvent(clip(e.category,120),clip(e.skill,160),clip(e.status,80),clip(e.evidence,600),e.observedAtMs));
        JSONArray a=new JSONArray();
        try{
            java.util.List<LearningEvent> snapshot=l.snapshot(); int start=Math.max(0,snapshot.size()-500);
            for(int i=start;i<snapshot.size();i++){LearningEvent e=snapshot.get(i);
                JSONObject o=new JSONObject().put("category",e.category).put("skill",e.skill).put("status",e.status).put("evidence",e.evidence).put("ts",e.observedAtMs); a.put(o);
            }
            JSONObject r=new JSONObject().put("summary",l.getLastSessionSummary()).put("next",l.getNextObjective()).put("events",a)
                    .put("lastSessionId",sessionId).put("lastFeedback",clip(report.feedback,3000)).put("updatedAt",System.currentTimeMillis());
            prefs.edit().putString("ledger:"+profileId,r.toString()).apply();
        }catch(Exception ignored){}
    }

    public String exportProfileMemory(String profileId){ return prefs.getString("ledger:"+profileId,"{}"); }
    public void remove(String profileId){prefs.edit().remove("ledger:"+profileId).apply();}
    private static String clip(String v,int max){String s=v==null?"":v.trim();return s.length()<=max?s:s.substring(0,max);}
}
