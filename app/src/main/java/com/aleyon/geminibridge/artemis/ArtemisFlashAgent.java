/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Aleyon Bridge adaptation of Google Artemis FlashRunner:
 * observe -> choose one allow-listed action -> act -> observe again.
 */
package com.aleyon.geminibridge.artemis;

import android.content.Context;

import com.aleyon.geminibridge.core.TransportState;
import com.aleyon.geminibridge.transport.TransportObservation;

import java.util.ArrayList;
import java.util.List;

/**
 * Small task-oriented Artemis runtime used by Bridge.
 *
 * Bridge has only two provider tasks:
 *  - START_SESSION: fresh chat -> context -> Live/chat ready
 *  - CLOSE_SESSION: end Live -> debrief -> local commit
 *
 * The phase is part of learned routine memory, so the same semantic screen can
 * safely map to different actions in different parts of one task.
 */
public final class ArtemisFlashAgent {
    public static final String PHASE_FRESH_CHAT="fresh-chat";
    public static final String PHASE_CONTEXT="context";
    public static final String PHASE_LIVE="live";
    public static final String PHASE_END_LIVE="end-live";
    public static final String PHASE_DEBRIEF="debrief";

    public enum Action {
        WAIT,
        BACK,
        CREATE_NORMAL_CHAT,
        WRITE_CONTEXT,
        SUBMIT_CONTEXT,
        START_LIVE,
        END_LIVE,
        COMPLETE_FRESH_CHAT,
        COMPLETE_CONTEXT,
        COMPLETE_LIVE,
        COMPLETE_END_LIVE,
        FAIL_CLOSED
    }

    private final ArtemisRoutineMemory memory;
    private final String routineKey;
    private ArtemisRoutineMemory.Routine replay;
    private int replayIndex;
    private boolean replaying;
    private final List<ArtemisRoutineMemory.Step> learned=new ArrayList<>();

    public ArtemisFlashAgent(Context context,String routineKey){
        this.memory=new ArtemisRoutineMemory(context);
        this.routineKey=routineKey;
        this.replay=memory.load(routineKey);
        this.replaying=replay!=null&&!replay.steps.isEmpty();
    }

    public boolean isReplaying(){return replaying;}

    /** Ensure each Bridge session starts in a clean normal Gemini chat. */
    public Action nextFreshChat(TransportObservation o,boolean blankNormalChat){
        if(o==null)return Action.FAIL_CLOSED;
        if(o.state==TransportState.NORMAL_CHAT&&blankNormalChat)
            return replayOr(PHASE_FRESH_CHAT,o.state,Action.COMPLETE_FRESH_CHAT);

        Action fallback=switch(o.state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case NORMAL_CHAT,TEMPORARY_CHAT -> Action.CREATE_NORMAL_CHAT;
            case CONVERSATION_LIST,CONVERSATION_SEARCH,LIVE_ACTIVE -> Action.BACK;
            case UNKNOWN -> Action.FAIL_CLOSED;
        };
        return replayOr(PHASE_FRESH_CHAT,o.state,fallback);
    }

    /** Deliver the locally-owned continuity capsule to the current chat. */
    public Action nextContext(TransportObservation o,
            boolean payloadPrepared,
            boolean deliveryObserved,
            boolean writeIssued,
            int submitAttempts,
            boolean debrief){
        if(o==null)return Action.FAIL_CLOSED;
        String phase=debrief?PHASE_DEBRIEF:PHASE_CONTEXT;
        if(deliveryObserved)return replayOr(phase,o.state,Action.COMPLETE_CONTEXT);
        Action fallback=switch(o.state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case NORMAL_CHAT -> {
                if(payloadPrepared)yield submitAttempts<3?Action.SUBMIT_CONTEXT:Action.FAIL_CLOSED;
                if(!writeIssued)yield Action.WRITE_CONTEXT;
                if(submitAttempts<3)yield Action.WAIT;
                yield Action.FAIL_CLOSED;
            }
            case LIVE_ACTIVE -> Action.BACK;
            default -> Action.FAIL_CLOSED;
        };
        return replayOr(phase,o.state,fallback);
    }

    /** Start Live only after the context message has been delivered. */
    public Action nextLive(TransportObservation o){
        if(o==null)return Action.FAIL_CLOSED;
        if(o.state==TransportState.LIVE_ACTIVE)
            return replayOr(PHASE_LIVE,o.state,Action.COMPLETE_LIVE);
        Action fallback=switch(o.state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case NORMAL_CHAT -> o.liveAvailable?Action.START_LIVE:Action.WAIT;
            default -> Action.FAIL_CLOSED;
        };
        return replayOr(PHASE_LIVE,o.state,fallback);
    }

    /** End Live reactively and verify the return to the same normal chat. */
    public Action nextEndLive(TransportObservation o){
        if(o==null)return Action.FAIL_CLOSED;
        if(o.state==TransportState.NORMAL_CHAT)
            return replayOr(PHASE_END_LIVE,o.state,Action.COMPLETE_END_LIVE);
        Action fallback=switch(o.state){
            case LIVE_ACTIVE -> Action.END_LIVE;
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            default -> Action.FAIL_CLOSED;
        };
        return replayOr(PHASE_END_LIVE,o.state,fallback);
    }

    private Action replayOr(String phase,TransportState state,Action fallback){
        if(replaying&&replayIndex<replay.steps.size()){
            ArtemisRoutineMemory.Step s=replay.steps.get(replayIndex);
            if(s.phase.equals(phase)&&s.state==state){
                try{return Action.valueOf(s.action);}catch(Exception ignored){}
            }
            invalidateReplay();
        }
        return fallback;
    }

    private void invalidateReplay(){
        memory.invalidate(routineKey);
        replaying=false;
        replay=null;
        replayIndex=0;
        learned.clear();
    }

    public void actionSucceeded(String phase,TransportState state,Action action){
        if(action==null||state==null||phase==null||phase.isEmpty()||!recordable(action))return;
        if(replaying){
            replayIndex++;
            if(replay!=null&&replayIndex>=replay.steps.size())replaying=false;
            return;
        }
        if(learned.size()<20)learned.add(new ArtemisRoutineMemory.Step(phase,state,action.name()));
    }

    public void actionFailed(){
        if(replaying)invalidateReplay();
    }

    public void complete(){
        if(!learned.isEmpty())memory.save(routineKey,learned);
    }

    private static boolean recordable(Action a){
        return a==Action.BACK
                ||a==Action.CREATE_NORMAL_CHAT
                ||a==Action.WRITE_CONTEXT
                ||a==Action.SUBMIT_CONTEXT
                ||a==Action.START_LIVE
                ||a==Action.END_LIVE;
    }
}
