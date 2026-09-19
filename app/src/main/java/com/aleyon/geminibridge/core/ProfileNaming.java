package com.aleyon.geminibridge.core;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Naming is UX; profile id is identity. Aleyon owns identity; provider-side titles are only navigation UX. */
public final class ProfileNaming {
    public static final String CHAT_PREFIX = "ALEYON — ";
    private ProfileNaming() {}

    public static String profileId(String label) {
        String key = normalizedKey(label).replace(' ', '-');
        if (!key.isEmpty() && key.matches("[a-z0-9-]+")) return "lang-" + key;
        return "lang-u-" + shortHash(label == null ? "" : label);
    }

    public static boolean isValidProfileId(String value) {
        if(value==null)return false;
        String v=value.trim();
        return v.matches("lang-[a-z0-9-]{1,120}") || v.matches("lang-u-[0-9a-f]{8,64}");
    }

    public static String normalizedKey(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) return "Idioma";
        return value.substring(0,1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String shortHash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i=0;i<6;i++) b.append(String.format(Locale.ROOT, "%02x", d[i]));
            return b.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
