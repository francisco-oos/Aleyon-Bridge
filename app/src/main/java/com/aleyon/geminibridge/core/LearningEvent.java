package com.aleyon.geminibridge.core;

public final class LearningEvent {
    public final String category, skill, status, evidence;
    public final long observedAtMs;
    public LearningEvent(String category, String skill, String status, String evidence, long observedAtMs) {
        this.category=safe(category); this.skill=safe(skill); this.status=safe(status); this.evidence=safe(evidence); this.observedAtMs=observedAtMs;
    }
    private static String safe(String v){ return v==null?"":v.trim(); }
}
