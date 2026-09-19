package com.aleyon.geminibridge.core;

/** Pure recovery policy for the session-scoped Gemini transaction. */
public final class RecoveryPlanner {
    public enum RecoveryAction {
        NONE, RETRY_START, RESTORE_LIVE_OVERLAY, RESTORE_CHAT_OVERLAY,
        FINISH_CLOSE, ASK_USER, REQUIRE_BRIDGE_UPDATE
    }
    private RecoveryPlanner() {}

    public static RecoveryAction plan(SessionStage stage, boolean liveStillActive) {
        if(stage==null) return RecoveryAction.RETRY_START;
        return switch(stage) {
            case READY -> RecoveryAction.NONE;
            case OPENING_SESSION_CHAT, CONTEXT_INJECTING, CONTEXT_READY -> RecoveryAction.RETRY_START;
            case LIVE_STARTING, LIVE_ACTIVE -> liveStillActive
                    ? RecoveryAction.RESTORE_LIVE_OVERLAY : RecoveryAction.FINISH_CLOSE;
            case CHAT_ACTIVE -> RecoveryAction.RESTORE_CHAT_OVERLAY;
            case CLOSING_SESSION, WAITING_TRANSCRIPT, ANALYZING, COMMITTING, RECOVERING -> RecoveryAction.FINISH_CLOSE;
            case AMBIGUOUS_USER_REQUIRED, USER_ACTION_REQUIRED -> RecoveryAction.ASK_USER;
            case APP_UPDATE_REQUIRED -> RecoveryAction.REQUIRE_BRIDGE_UPDATE;
            case ERROR -> RecoveryAction.RETRY_START;
        };
    }

    public static RecoveryAction reconcile(SessionStage persisted, SessionStage recoverable,
                                           boolean liveStillActive) {
        RecoveryAction action=plan(persisted,liveStillActive);
        if((action==RecoveryAction.ASK_USER || action==RecoveryAction.REQUIRE_BRIDGE_UPDATE)
                && recoverable!=null) return plan(recoverable,liveStillActive);
        return action;
    }
}
