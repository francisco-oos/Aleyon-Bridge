package com.aleyon.geminibridge.core;

/** Stable machine contract for the local-memory + session-scoped Gemini runtime. */
public final class ProtocolContract {
    public static final int SCHEMA_VERSION = 6;
    public static final String SESSION_READY = "ALEYON_SESSION_READY";
    private ProtocolContract() {}
}
