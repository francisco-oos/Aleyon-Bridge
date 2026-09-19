package com.aleyon.geminibridge.core;

/** Persistent transaction states. States describe only the local-memory chat/Live transaction. */
public enum SessionStage {
    READY,
    OPENING_SESSION_CHAT,
    CONTEXT_INJECTING,
    CONTEXT_READY,
    LIVE_STARTING,
    LIVE_ACTIVE,
    CHAT_ACTIVE,
    CLOSING_SESSION,
    WAITING_TRANSCRIPT,
    ANALYZING,
    COMMITTING,
    AMBIGUOUS_USER_REQUIRED,
    USER_ACTION_REQUIRED,
    APP_UPDATE_REQUIRED,
    ERROR
}
