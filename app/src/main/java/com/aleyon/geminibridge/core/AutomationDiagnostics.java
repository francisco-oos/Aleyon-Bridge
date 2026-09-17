package com.aleyon.geminibridge.core;

/**
 * Structured diagnostic snapshot for one Bridge automation transition.
 *
 * Accessibility against a third-party app is inherently sensitive to window
 * timing and UI rollouts. This record keeps enough evidence to diagnose a
 * failed canonical-chat, context-injection, Live/Chat, or close operation
 * without requiring a debugger on the phone. It never stores credentials,
 * audio, or the complete conversation.
 */
public final class AutomationDiagnostics {
    public final String runId;
    public final String step;
    public final String stateBefore;
    public final String stateAfter;
    public final long timestampMs;
    public final String foregroundPackage;
    public final boolean rootAvailable;
    public final int nodeCount;
    public final String selectorTried;
    public final int candidatesFound;
    public final String actionResult;
    public final String rootSource;
    public final int interactiveWindowCount;
    public final String windowsSummary;

    public AutomationDiagnostics(String runId, String step, String stateBefore, String stateAfter,
            long timestampMs, String foregroundPackage, boolean rootAvailable, int nodeCount,
            String selectorTried, int candidatesFound, String actionResult) {
        this(runId, step, stateBefore, stateAfter, timestampMs, foregroundPackage,
                rootAvailable, nodeCount, selectorTried, candidatesFound, actionResult,
                "", 0, "");
    }

    public AutomationDiagnostics(String runId, String step, String stateBefore, String stateAfter,
            long timestampMs, String foregroundPackage, boolean rootAvailable, int nodeCount,
            String selectorTried, int candidatesFound, String actionResult, String rootSource,
            int interactiveWindowCount, String windowsSummary) {
        this.runId = runId == null ? "" : runId;
        this.step = step == null ? "" : step;
        this.stateBefore = stateBefore == null ? "" : stateBefore;
        this.stateAfter = stateAfter == null ? "" : stateAfter;
        this.timestampMs = timestampMs;
        this.foregroundPackage = foregroundPackage == null ? "" : foregroundPackage;
        this.rootAvailable = rootAvailable;
        this.nodeCount = nodeCount;
        this.selectorTried = selectorTried == null ? "" : selectorTried;
        this.candidatesFound = candidatesFound;
        this.actionResult = actionResult == null ? "" : actionResult;
        this.rootSource = rootSource == null ? "" : rootSource;
        this.interactiveWindowCount = interactiveWindowCount;
        this.windowsSummary = windowsSummary == null ? "" : windowsSummary;
    }

    /**
     * Minimal hand-rolled JSON object text.
     *
     * The core package is intentionally dependency-free (see CoreTests:
     * "Dependency-free unit tests runnable with the JDK only"), so this does
     * not use org.json even though the automation layer that consumes it
     * does. All fields are simple/controlled (short identifiers, selector
     * lists, package names); escaping only needs to be defensive.
     */
    public String toJsonString() {
        StringBuilder b = new StringBuilder("{");
        field(b, "runId", runId);
        field(b, "step", step);
        field(b, "stateBefore", stateBefore);
        field(b, "stateAfter", stateAfter);
        b.append("\"timestampMs\":").append(timestampMs).append(',');
        field(b, "foregroundPackage", foregroundPackage);
        b.append("\"rootAvailable\":").append(rootAvailable).append(',');
        b.append("\"nodeCount\":").append(nodeCount).append(',');
        field(b, "selectorTried", selectorTried);
        b.append("\"candidatesFound\":").append(candidatesFound).append(',');
        field(b, "actionResult", actionResult);
        field(b, "rootSource", rootSource);
        b.append("\"interactiveWindowCount\":").append(interactiveWindowCount).append(',');
        b.append("\"windowsSummary\":\"").append(escape(windowsSummary)).append("\"}");
        return b.toString();
    }

    private static void field(StringBuilder b, String key, String value) {
        b.append('"').append(key).append("\":\"").append(escape(value)).append("\",");
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
