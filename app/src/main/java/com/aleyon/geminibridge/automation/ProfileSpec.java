package com.aleyon.geminibridge.automation;

import com.aleyon.geminibridge.core.ProfileNaming;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Local authoritative learner profile.
 *
 * Aleyon owns continuity locally. Every learning session injects a compact
 * context capsule into a normal Gemini conversation; provider history is never
 * authoritative memory.
 */
public final class ProfileSpec {
    public String id="", label="", nativeLanguage="", targetLanguage="", level="";
    public String correction="suave";
    public String conversationStyle="natural, paciente y conversacional";
    public String nativeLanguageUse="solo si hay bloqueo o se solicita";
    public String profession="", interests="", goal="", situations="", specialization="";
    public boolean managedByBridge=true, detectedOnly=false;
    public int sessions=0;

    public static ProfileSpec fromJson(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public static ProfileSpec fromJson(JSONObject o) throws JSONException {
        ProfileSpec p=new ProfileSpec();
        p.label=required(value(o,"label",value(o,"targetLang","")),"label");
        p.detectedOnly=o.optBoolean("detectedOnly",false);
        p.nativeLanguage=value(o,"nativeLang","");
        p.targetLanguage=value(o,"targetLang",p.label);
        String suppliedId=value(o,"id","");
        p.id=ProfileNaming.isValidProfileId(suppliedId)?suppliedId:ProfileNaming.profileId(p.targetLanguage);
        p.level=value(o,"level","");
        p.correction=value(o,"correction","suave");
        p.conversationStyle=value(o,"conversationStyle","natural, paciente y conversacional");
        p.nativeLanguageUse=value(o,"nativeUse","solo si hay bloqueo o se solicita");
        p.profession=value(o,"career",value(o,"profession",""));
        p.interests=value(o,"interests","");
        p.goal=value(o,"goal","");
        p.situations=value(o,"situations","");
        p.specialization=value(o,"extra",value(o,"specialization",""));
        p.managedByBridge=o.optBoolean("managedByBridge",true);
        p.sessions=o.optInt("sessions",0);
        if(!p.detectedOnly){
            p.nativeLanguage=required(p.nativeLanguage,"nativeLang");
            p.targetLanguage=required(p.targetLanguage,"targetLang");
            p.goal=required(p.goal,"goal");
        }
        return p;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id",id).put("label",label)
                .put("nativeLang",nativeLanguage).put("targetLang",targetLanguage)
                .put("level",level).put("correction",correction)
                .put("conversationStyle",conversationStyle).put("nativeUse",nativeLanguageUse)
                .put("career",profession).put("interests",interests)
                .put("goal",goal).put("situations",situations)
                .put("extra",specialization)
                .put("managedByBridge",managedByBridge).put("detectedOnly",detectedOnly)
                .put("sessions",sessions);
    }

    public boolean canStart(){
        return !blank(id) && !blank(targetLanguage) && !blank(nativeLanguage) && !blank(goal);
    }

    private static boolean blank(String v){ return v==null||v.trim().isEmpty(); }
    private static String value(JSONObject o,String k,String f){
        String v=o.optString(k,f); return v==null?f:v.trim();
    }
    private static String required(String v,String field) throws JSONException {
        if(blank(v)) throw new JSONException("Missing required profile field: "+field);
        return v.trim();
    }
}
