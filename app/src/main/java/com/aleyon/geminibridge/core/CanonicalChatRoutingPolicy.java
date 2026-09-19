package com.aleyon.geminibridge.core;

/**
 * Pure canonical-chat routing policy.
 *
 * Provider history is a useful cache, never learner memory. Profiles created
 * before the canonical-chat registry are migrated by rebuilding directly from
 * Aleyon's local truth. Search is reserved for a chat that Aleyon previously
 * verified and can no longer see immediately.
 */
public final class CanonicalChatRoutingPolicy {
    public enum Action {
        OPEN_VISIBLE,
        REBUILD_DIRECT,
        SEARCH_KNOWN_ONCE,
        REBUILD_AFTER_SEARCH
    }

    private CanonicalChatRoutingPolicy() {}

    public static Action decide(boolean registryKnown, boolean canonicalVisible,
            boolean searchAlreadyAttempted) {
        if (canonicalVisible) return Action.OPEN_VISIBLE;
        if (!registryKnown) return Action.REBUILD_DIRECT;
        return searchAlreadyAttempted
                ? Action.REBUILD_AFTER_SEARCH
                : Action.SEARCH_KNOWN_ONCE;
    }
}
