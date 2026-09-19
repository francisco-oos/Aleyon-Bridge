/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Aleyon Bridge adaptation of Google Artemis FlashRunner:
 * one observation -> one allow-listed action -> observe again.
 * The learned successful action/state sequence is persisted and replayed
 * until it stops matching, at which point it is invalidated and relearned.
 */
package com.aleyon.geminibridge.artemis;

import android.content.Context;

import com.aleyon.geminibridge.core.CanonicalChatRoutingPolicy;
import com.aleyon.geminibridge.core.TransportState;
import com.aleyon.geminibridge.transport.TransportObservation;

import java.util.ArrayList;
import java.util.List;

public final class ArtemisFlashAgent {
    public enum Action {
        WAIT,
        BACK,
        OPEN_CONVERSATION_LIST,
        OPEN_VISIBLE_CANONICAL,
        OPEN_SEARCH,
        TYPE_SEARCH_QUERY,
        CREATE_NORMAL_CHAT,
        WRITE_CONTEXT,
        SUBMIT_CONTEXT,
        START_LIVE,
        COMPLETE_CONTEXT,
        COMPLETE_LIVE,
        COMPLETE_REUSE,
        COMPLETE_REBUILD,
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

    public Action next(TransportObservation o,
            boolean registryKnown,
            boolean searchAttempted,
            boolean queryIssued,
            int searchMisses,
            boolean openingCanonical,
            boolean rebuilding){
        if(o==null)return Action.FAIL_CLOSED;
        Action fallback=explore(o.state,registryKnown,o.canonicalConversationVisible,
                searchAttempted,queryIssued,searchMisses,openingCanonical,rebuilding);
        return replayOr(o.state,fallback);
    }

    /**
     * Flash-style context delivery: decide from the current observation, never
     * from an assumed screen sequence. A successful WRITE/SUBMIT routine can
     * be replayed on later sessions and is invalidated on mismatch/failure.
     */
    public Action nextContext(TransportObservation o,
            boolean payloadPrepared,
            boolean deliveryObserved,
            boolean writeIssued,
            int submitAttempts){
        if(o==null)return Action.FAIL_CLOSED;
        if(deliveryObserved)return Action.COMPLETE_CONTEXT;
        Action fallback=switch(o.state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case NORMAL_CHAT -> {
                if(payloadPrepared)yield Action.SUBMIT_CONTEXT;
                if(!writeIssued)yield Action.WRITE_CONTEXT;
                if(submitAttempts<3)yield Action.WAIT;
                yield Action.FAIL_CLOSED;
            }
            case LIVE_ACTIVE -> Action.BACK;
            default -> Action.FAIL_CLOSED;
        };
        return replayOr(o.state,fallback);
    }

    /** Starts Live only when the current semantic observation proves it is available. */
    public Action nextLive(TransportObservation o){
        if(o==null)return Action.FAIL_CLOSED;
        if(o.state==TransportState.LIVE_ACTIVE)return Action.COMPLETE_LIVE;
        Action fallback=switch(o.state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case NORMAL_CHAT -> o.liveAvailable?Action.START_LIVE:Action.WAIT;
            default -> Action.FAIL_CLOSED;
        };
        return replayOr(o.state,fallback);
    }

    private Action replayOr(TransportState state,Action fallback){
        if(replaying&&replayIndex<replay.steps.size()){
            ArtemisRoutineMemory.Step s=replay.steps.get(replayIndex);
            if(s.state==state){
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

    private static Action explore(TransportState state,
            boolean registryKnown,
            boolean canonicalVisible,
            boolean searchAttempted,
            boolean queryIssued,
            int searchMisses,
            boolean openingCanonical,
            boolean rebuilding){
        if(state==null)return Action.FAIL_CLOSED;
        return switch(state){
            case CONSENT_REQUIRED,UNAVAILABLE -> Action.WAIT;
            case LIVE_ACTIVE -> Action.BACK;
            case NORMAL_CHAT -> {
                if(rebuilding)yield Action.COMPLETE_REBUILD;
                if(openingCanonical)yield Action.COMPLETE_REUSE;
                yield Action.OPEN_CONVERSATION_LIST;
            }
            case TEMPORARY_CHAT -> Action.OPEN_CONVERSATION_LIST;
            case CONVERSATION_LIST -> {
                if(openingCanonical||rebuilding)yield Action.WAIT;
                CanonicalChatRoutingPolicy.Action route=CanonicalChatRoutingPolicy.decide(
                        registryKnown,canonicalVisible,searchAttempted);
                yield switch(route){
                    case OPEN_VISIBLE -> Action.OPEN_VISIBLE_CANONICAL;
                    case SEARCH_KNOWN_ONCE -> Action.OPEN_SEARCH;
                    case REBUILD_DIRECT,REBUILD_AFTER_SEARCH -> Action.CREATE_NORMAL_CHAT;
                };
            }
            case CONVERSATION_SEARCH -> {
                if(openingCanonical)yield Action.WAIT;
                if(!registryKnown)yield Action.BACK;
                if(canonicalVisible)yield Action.OPEN_VISIBLE_CANONICAL;
                if(!queryIssued)yield Action.TYPE_SEARCH_QUERY;
                if(searchMisses<3)yield Action.WAIT;
                yield Action.BACK;
            }
            case UNKNOWN -> Action.FAIL_CLOSED;
        };
    }

    public void actionSucceeded(TransportState state,Action action){
        if(action==null||state==null||!recordable(action))return;
        if(replaying){
            replayIndex++;
            if(replay!=null&&replayIndex>=replay.steps.size())replaying=false;
            return;
        }
        if(learned.size()<24)learned.add(new ArtemisRoutineMemory.Step(state,action.name()));
    }

    public void actionFailed(){
        if(replaying)invalidateReplay();
    }

    public void complete(){
        if(!learned.isEmpty())memory.save(routineKey,learned);
    }

    private static boolean recordable(Action a){
        return a==Action.BACK
                ||a==Action.OPEN_CONVERSATION_LIST
                ||a==Action.OPEN_VISIBLE_CANONICAL
                ||a==Action.OPEN_SEARCH
                ||a==Action.TYPE_SEARCH_QUERY
                ||a==Action.CREATE_NORMAL_CHAT
                ||a==Action.WRITE_CONTEXT
                ||a==Action.SUBMIT_CONTEXT
                ||a==Action.START_LIVE;
    }
}
