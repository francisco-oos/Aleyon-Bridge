/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Aleyon Bridge adaptation of the bounded-memory/retry ideas used by
 * Google Artemis StepMemoryService. This derivative stores only successful
 * semantic transport routines; it stores no learner content.
 */
package com.aleyon.geminibridge.artemis;

import android.content.Context;
import android.content.SharedPreferences;

import com.aleyon.geminibridge.core.TransportState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent semantic routine memory for the embedded Artemis transport.
 *
 * A routine is phase-aware: NORMAL_CHAT can legitimately mean different
 * things while creating a fresh chat, delivering context or launching Live.
 * Storing the phase prevents a learned action from leaking into a different
 * part of the same task.
 */
public final class ArtemisRoutineMemory {
    private static final String PREFS="aleyon_artemis_routines_v2";
    private static final int MAX_STEPS=20;
    private final SharedPreferences prefs;

    public ArtemisRoutineMemory(Context context){
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static final class Step {
        public final String phase;
        public final TransportState state;
        public final String action;

        public Step(String phase,TransportState state,String action){
            this.phase=safe(phase);
            this.state=state==null?TransportState.UNKNOWN:state;
            this.action=action==null?"":action;
        }

        JSONObject toJson(){
            JSONObject o=new JSONObject();
            try{o.put("phase",phase).put("state",state.name()).put("action",action);}
            catch(Exception ignored){}
            return o;
        }

        static Step fromJson(JSONObject o){
            if(o==null)return new Step("",TransportState.UNKNOWN,"");
            try{
                return new Step(o.optString("phase",""),
                        TransportState.valueOf(o.optString("state","UNKNOWN")),
                        o.optString("action",""));
            }catch(Exception e){
                return new Step("",TransportState.UNKNOWN,"");
            }
        }
    }

    public static final class Routine {
        public final String key;
        public final List<Step> steps;
        public final long learnedAt;

        Routine(String key,List<Step> steps,long learnedAt){
            this.key=key;
            this.steps=Collections.unmodifiableList(steps);
            this.learnedAt=learnedAt;
        }
    }

    public Routine load(String key){
        String raw=prefs.getString("routine:"+safe(key),"");
        if(raw.isEmpty())return null;
        try{
            JSONObject root=new JSONObject(raw);
            JSONArray a=root.optJSONArray("steps");
            if(a==null||a.length()==0)return null;
            List<Step> out=new ArrayList<>();
            for(int i=0;i<a.length()&&i<MAX_STEPS;i++){
                Step s=Step.fromJson(a.optJSONObject(i));
                if(s.phase.isEmpty()||s.action.isEmpty())return null;
                out.add(s);
            }
            return out.isEmpty()?null:new Routine(key,out,root.optLong("learnedAt",0L));
        }catch(Exception e){
            invalidate(key);return null;
        }
    }

    public void save(String key,List<Step> steps){
        if(steps==null||steps.isEmpty())return;
        JSONArray a=new JSONArray();
        int n=Math.min(MAX_STEPS,steps.size());
        for(int i=0;i<n;i++)a.put(steps.get(i).toJson());
        JSONObject root=new JSONObject();
        try{
            root.put("schema",2)
                    .put("learnedAt",System.currentTimeMillis())
                    .put("steps",a);
            prefs.edit().putString("routine:"+safe(key),root.toString()).apply();
        }catch(Exception ignored){}
    }

    public void invalidate(String key){
        prefs.edit().remove("routine:"+safe(key)).apply();
    }

    public boolean has(String key){return prefs.contains("routine:"+safe(key));}

    private static String safe(String v){return v==null?"":v.trim();}
}
