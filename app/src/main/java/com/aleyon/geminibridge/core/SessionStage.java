package com.aleyon.geminibridge.core;

/** Transaction stages for Bridge 0.4. No notebook/attach-detach states remain. */
public enum SessionStage {
    READY,
    LOCATING_CHAT,
    CREATING_CHAT,
    CONTEXT_INJECTING,
    CONTEXT_READY,
    LIVE_STARTING,
    LIVE_ACTIVE,
    CHAT_ACTIVE,
    CLOSING_SESSION,
    WAITING_TRANSCRIPT,
    ANALYZING,
    COMMITTING,
    RECOVERING,
    AMBIGUOUS_USER_REQUIRED,
    USER_ACTION_REQUIRED,
    APP_UPDATE_REQUIRED,
    ERROR
}
