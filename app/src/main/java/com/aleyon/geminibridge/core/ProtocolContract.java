package com.aleyon.geminibridge.core;

/** Stable machine-readable contract exchanged with Gemini inside one canonical chat per profile. */
public final class ProtocolContract {
    public static final int SCHEMA_VERSION = 3;
    public static final String SESSION_READY = "ALEYON_SESSION_READY";
    public static final String REPORT_BEGIN = "ALEYON_REPORT_BEGIN";
    public static final String REPORT_END = "ALEYON_REPORT_END";
    private ProtocolContract() {}
}
