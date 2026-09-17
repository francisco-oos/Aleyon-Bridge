package com.aleyon.geminibridge.core;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
