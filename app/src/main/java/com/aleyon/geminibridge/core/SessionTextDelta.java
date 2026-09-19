package com.aleyon.geminibridge.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Best-effort line multiset delta between the visible chat before and after a session. */
public final class SessionTextDelta {
    private SessionTextDelta() {}
    public static String delta(String before,String after){
        if(after==null||after.trim().isEmpty())return "";
        Map<String,Integer> old=new LinkedHashMap<>();
        if(before!=null)for(String raw:before.split("\\r?\\n")){String l=raw.trim();if(!l.isEmpty())old.put(l,old.getOrDefault(l,0)+1);}
        StringBuilder out=new StringBuilder();
        for(String raw:after.split("\\r?\\n")){
            String l=raw.trim();if(l.isEmpty())continue;Integer n=old.get(l);
            if(n!=null&&n>0){if(n==1)old.remove(l);else old.put(l,n-1);continue;}
            out.append(l).append('\n');
        }
        return out.toString();
    }
    /**
     * Fail-closed proof that the visible conversation still contains real
     * evidence from the session being closed. This is an ephemeral local guard,
     * not a provider conversation id and not a history/recovery mechanism.
     */
    public static boolean containsConversationEvidence(String evidence,String current){
        return containsConversationEvidence(evidence,current,1);
    }

    public static boolean containsConversationEvidence(String evidence,String current,int minimumMatches){
        if(evidence==null||current==null||evidence.trim().isEmpty()||current.trim().isEmpty())return false;
        String hay=norm(current);
        List<String> signals=new ArrayList<>();
        Set<String> seen=new LinkedHashSet<>();
        for(String raw:evidence.split("\\r?\\n")){
            String s=norm(raw);
            if(!distinctive(s)||!seen.add(s))continue;
            signals.add(s);
        }
        if(signals.isEmpty())return false;
        Collections.sort(signals,Comparator.comparingInt(String::length).reversed());
        if(signals.size()>8)signals=new ArrayList<>(signals.subList(0,8));
        int required=Math.max(1,Math.min(Math.max(1,minimumMatches),signals.size()));
        int hits=0;
        for(String s:signals)if(hay.contains(s)&&++hits>=required)return true;
        return false;
    }

    private static boolean distinctive(String s){
        if(s==null||s.length()<8)return false;
        return !s.equals("gemini es una ia y puede cometer errores.")
                &&!s.equals("gemini can make mistakes.")
                &&!s.equals("preguntale...")
                &&!s.equals("ask gemini")
                &&!s.equals("gemini pro");
    }

    private static String norm(String s){
        if(s==null)return "";
        return s.replace('\r',' ').replace('\n',' ').trim().replaceAll("\\s+"," ")
                .toLowerCase(Locale.ROOT);
    }

}
