package com.aleyon.geminibridge.core;

/** Pure crash-recovery policy for the simplified canonical-chat transaction. */
public final class RecoveryPlanner {
    public enum RecoveryAction {
        NONE, RETRY_START, RESTORE_LIVE_OVERLAY, RESTORE_CHAT_OVERLAY,
        FINISH_CLOSE, ASK_USER, REQUIRE_BRIDGE_UPDATE
    }
    private RecoveryPlanner() {}

    public static RecoveryAction plan(SessionStage stage, boolean liveStillActive) {
        if (stage == null) return RecoveryAction.RETRY_START;
        return switch (stage) {
            case READY -> RecoveryAction.NONE;
            case LOCATING_CHAT, CREATING_CHAT, CONTEXT_INJECTING, CONTEXT_READY -> RecoveryAction.RETRY_START;
            case LIVE_STARTING, LIVE_ACTIVE -> liveStillActive
                    ? RecoveryAction.RESTORE_LIVE_OVERLAY : RecoveryAction.FINISH_CLOSE;
            case CHAT_ACTIVE -> RecoveryAction.RESTORE_CHAT_OVERLAY;
            case CLOSING_SESSION, WAITING_TRANSCRIPT, ANALYZING, COMMITTING, RECOVERING -> RecoveryAction.FINISH_CLOSE;
            case AMBIGUOUS_USER_REQUIRED, USER_ACTION_REQUIRED -> RecoveryAction.ASK_USER;
            case APP_UPDATE_REQUIRED -> RecoveryAction.REQUIRE_BRIDGE_UPDATE;
            case ERROR -> RecoveryAction.RETRY_START;
        };
    }
}
