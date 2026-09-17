package com.aleyon.geminibridge.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Evidence ledger owned by Bridge, never by Gemini. */
public final class LearningLedger {
    private final List<LearningEvent> events = new ArrayList<>();
    private String lastSessionSummary="", nextObjective="";
    public void addVerified(LearningEvent e){ if(e!=null && !e.evidence.isEmpty()) events.add(e); }
    public List<LearningEvent> snapshot(){ return Collections.unmodifiableList(new ArrayList<>(events)); }
    public String getLastSessionSummary(){ return lastSessionSummary; }
    public void setLastSessionSummary(String v){ lastSessionSummary=v==null?"":v.trim(); }
    public String getNextObjective(){ return nextObjective; }
    public void setNextObjective(String v){ nextObjective=v==null?"":v.trim(); }
}
