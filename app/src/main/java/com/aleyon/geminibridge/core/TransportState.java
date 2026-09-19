package com.aleyon.geminibridge.core;

/** Semantic device state. No device brand, resource id or coordinate leaks here. */
public enum TransportState {
    UNAVAILABLE,
    CONSENT_REQUIRED,
    LIVE_ACTIVE,
    CONVERSATION_LIST,
    TEMPORARY_CHAT,
    NORMAL_CHAT,
    UNKNOWN
}
