package com.aleyon.geminibridge.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.aleyon.geminibridge.core.LearningEvent;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.PedagogicalState;
import com.aleyon.geminibridge.core.PedagogicalStateBuilder;
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

    /**
     * Synchronous commit plus read-back verification. Session cleanup is never
     * allowed to outrun this point; a failed write leaves the transaction in ERROR.
     */
    public boolean commitVerified(String profileId, SessionReportParser.Report report, String sessionId){ return commitVerified(profileId,report,sessionId,""); }

    public boolean commitVerified(String profileId, SessionReportParser.Report report, String sessionId, String sessionText){
        if(report==null||profileId==null||profileId.trim().isEmpty()||sessionId==null||sessionId.trim().isEmpty())return false;
        LearningLedger l=load(profileId);
        if(!report.summary.isEmpty())l.setLastSessionSummary(clip(report.summary,4000));
        if(!report.nextObjective.isEmpty())l.setNextObjective(clip(report.nextObjective,1200));
        for(LearningEvent e:report.events)l.addVerified(new LearningEvent(clip(e.category,120),clip(e.skill,160),clip(e.status,80),clip(e.evidence,600),e.observedAtMs));
        try{
            JSONArray a=new JSONArray();
            java.util.List<LearningEvent> snapshot=l.snapshot(); int start=Math.max(0,snapshot.size()-500);
            for(int i=start;i<snapshot.size();i++){LearningEvent e=snapshot.get(i);
                a.put(new JSONObject().put("category",e.category).put("skill",e.skill).put("status",e.status).put("evidence",e.evidence).put("ts",e.observedAtMs));
            }
            JSONObject r=new JSONObject().put("summary",l.getLastSessionSummary()).put("next",l.getNextObjective()).put("events",a)
                    .put("pedagogyVersion",2)
                    .put("lastSessionId",sessionId).put("lastFeedback",clip(report.feedback,3000))
                    .put("lastSessionText",clip(sessionText,12000)).put("updatedAt",System.currentTimeMillis());
            if(!prefs.edit().putString("ledger:"+profileId,r.toString()).commit())return false;
            String written=prefs.getString("ledger:"+profileId,"");
            if(written==null||written.isEmpty())return false;
            JSONObject check=new JSONObject(written);
            return sessionId.equals(check.optString("lastSessionId",""));
        }catch(Exception e){return false;}
    }

    public String exportProfileMemory(String profileId){ return prefs.getString("ledger:"+profileId,"{}"); }

    /** Read-only learned state for the local UI. It is derived, never a second source of truth. */
    public String pedagogicalStateJson(String profileId){
        try{
            PedagogicalState s=PedagogicalStateBuilder.build(load(profileId));
            JSONObject o=new JSONObject()
                    .put("evidenceCount",s.evidenceCount())
                    .put("levelEstimate",s.levelEstimate())
                    .put("nextObjective",s.nextObjective())
                    .put("lastSummary",s.lastSummary());
            JSONArray progress=new JSONArray();for(String v:s.progress())progress.put(v);o.put("progress",progress);
            JSONArray reinforce=new JSONArray();
            for(PedagogicalState.Pattern p:s.reinforcement()){
                reinforce.put(new JSONObject().put("text",p.text).put("evidenceCount",p.evidenceCount).put("confirmed",p.confirmed));
            }
            o.put("reinforcement",reinforce);
            JSONArray vocabulary=new JSONArray();for(String v:s.vocabulary())vocabulary.put(v);o.put("vocabulary",vocabulary);
            return o.toString();
        }catch(Exception e){return "{}";}
    }

    public void remove(String profileId){prefs.edit().remove("ledger:"+profileId).apply();}
    private static String clip(String v,int max){String s=v==null?"":v.trim();return s.length()<=max?s:s.substring(0,max);}
}
