package com.aleyon.geminibridge.core;

/**
 * Reactive next-action planner for canonical Gemini navigation.
 *
 * This is deliberately not a screen script. Every invocation receives the
 * current semantic observation plus tiny execution memory and returns one
 * allow-listed next action. The caller must observe the UI again after acting.
 */
public final class AdaptiveNavigationPlanner {
    public enum Action {
        WAIT,
        BACK,
        OPEN_CONVERSATION_LIST,
        OPEN_VISIBLE_CANONICAL,
        OPEN_SEARCH,
        TYPE_SEARCH_QUERY,
        CREATE_NORMAL_CHAT,
        COMPLETE_REUSE,
        COMPLETE_REBUILD,
        FAIL_CLOSED
    }

    private AdaptiveNavigationPlanner() {}

    public static Action next(
            TransportState state,
            boolean registryKnown,
            boolean canonicalVisible,
            boolean searchAttempted,
            boolean queryIssued,
            int searchMisses,
            boolean openingCanonical,
            boolean rebuilding) {

        if (state == null) return Action.FAIL_CLOSED;

        return switch (state) {
            case CONSENT_REQUIRED, UNAVAILABLE -> Action.WAIT;
            case LIVE_ACTIVE -> Action.BACK;

            case NORMAL_CHAT -> {
                if (rebuilding) yield Action.COMPLETE_REBUILD;
                if (openingCanonical) yield Action.COMPLETE_REUSE;
                yield Action.OPEN_CONVERSATION_LIST;
            }

            case TEMPORARY_CHAT -> Action.OPEN_CONVERSATION_LIST;

            case CONVERSATION_LIST -> {
                CanonicalChatRoutingPolicy.Action route=CanonicalChatRoutingPolicy.decide(
                        registryKnown,canonicalVisible,searchAttempted);
                yield switch(route){
                    case OPEN_VISIBLE -> Action.OPEN_VISIBLE_CANONICAL;
                    case SEARCH_KNOWN_ONCE -> Action.OPEN_SEARCH;
                    case REBUILD_DIRECT, REBUILD_AFTER_SEARCH -> Action.CREATE_NORMAL_CHAT;
                };
            }

            case CONVERSATION_SEARCH -> {
                if (!registryKnown) yield Action.BACK;
                if (canonicalVisible) yield Action.OPEN_VISIBLE_CANONICAL;
                if (!queryIssued) yield Action.TYPE_SEARCH_QUERY;
                if (searchMisses < 3) yield Action.WAIT;
                yield Action.BACK;
            }

            case UNKNOWN -> Action.FAIL_CLOSED;
        };
    }
}
