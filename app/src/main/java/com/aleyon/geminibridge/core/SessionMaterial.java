package com.aleyon.geminibridge.core;

/**
 * Metadata-only description of material the user wants Gemini to study.
 *
 * Bridge does not copy the document/image/audio/video bytes into its learner
 * memory. The bytes remain owned by Android/Gemini's native attachment flow;
 * Bridge transports only the user's intent and the minimum metadata needed to
 * keep the session coherent.
 */
public final class SessionMaterial {
    public enum Kind { DOCUMENT, IMAGE, AUDIO, VIDEO, CODE, OTHER }

    public final String id;
    public final Kind kind;
    public final String displayName;
    public final String mimeType;
    public final long sizeBytes;
    public final String contentUri;
    public final String studyInstruction;

    public SessionMaterial(String id, Kind kind, String displayName, String mimeType,
            long sizeBytes, String contentUri, String studyInstruction) {
        this.id=safe(id);
        this.kind=kind==null?Kind.OTHER:kind;
        this.displayName=safe(displayName);
        this.mimeType=safe(mimeType).toLowerCase(java.util.Locale.ROOT);
        this.sizeBytes=Math.max(0L,sizeBytes);
        this.contentUri=safe(contentUri);
        this.studyInstruction=safe(studyInstruction);
    }

    private static String safe(String value){return value==null?"":value.trim();}
}
