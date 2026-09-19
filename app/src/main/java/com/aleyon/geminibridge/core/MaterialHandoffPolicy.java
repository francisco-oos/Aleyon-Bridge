package com.aleyon.geminibridge.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Safe boundary for study-material handoff.
 *
 * Artemis-inspired transport may navigate/verify Gemini, but it never needs
 * microphone access and it does not read file bytes. Android's native picker
 * and Gemini transport the actual bytes. Only content:// references selected
 * by the user are acceptable here; file:// paths and arbitrary filesystem
 * access are rejected.
 */
public final class MaterialHandoffPolicy {
    public static final int MAX_ITEMS=10;
    public static final long MAX_STANDARD_BYTES=100L*1024L*1024L;
    public static final long MAX_VIDEO_BYTES=2L*1024L*1024L*1024L;

    private MaterialHandoffPolicy() {}

    public static boolean isSafe(SessionMaterial material){
        if(material==null)return false;
        if(material.id.isEmpty()||material.displayName.isEmpty())return false;
        if(material.contentUri.isEmpty()||!material.contentUri.startsWith("content://"))return false;
        if(hasControlChars(material.contentUri)||hasControlChars(material.displayName))return false;
        if(material.sizeBytes<=0)return false;
        long max=material.kind==SessionMaterial.Kind.VIDEO?MAX_VIDEO_BYTES:MAX_STANDARD_BYTES;
        return material.sizeBytes<=max;
    }

    public static boolean isSafeBatch(List<SessionMaterial> materials){
        if(materials==null||materials.isEmpty()||materials.size()>MAX_ITEMS)return false;
        Set<String> ids=new HashSet<>();
        for(SessionMaterial m:materials){
            if(!isSafe(m)||!ids.add(m.id))return false;
        }
        return true;
    }

    private static boolean hasControlChars(String value){
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return true;
        return false;
    }
}
