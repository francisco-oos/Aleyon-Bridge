package com.aleyon.geminibridge.core;

/**
 * Pure policy for provider-side conversation reuse.
 *
 * Bridge owns memory. A Gemini conversation is only a useful cognitive cache:
 * it may be reused when present, and reconstructed from local state when lost.
 */
public final class CanonicalConversationPolicy {
    public enum Resolution { REUSE, REBUILD }

    private CanonicalConversationPolicy() {}

    public static String title(String targetLanguage) {
        String value=targetLanguage==null?"":targetLanguage.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");
        if(value.isEmpty()) value="Idioma";
        if(value.length()>80)value=value.substring(0,80).trim();
        return "ALEYON — "+value;
    }

    public static Resolution resolve(boolean canonicalConversationFound) {
        return canonicalConversationFound?Resolution.REUSE:Resolution.REBUILD;
    }
}
