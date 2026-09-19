package com.aleyon.geminibridge.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses only user-facing session debrief text. Machine protocol is intentionally
 * not rendered into the visible Gemini conversation.
 */
public final class SessionReportParser {
    public static final class Report {
        public String summary="", nextObjective="", feedback="";
        public final List<LearningEvent> events=new ArrayList<>();
    }
    private SessionReportParser() {}

    public static boolean hasSessionReady(String text,String sessionId,String profileId){
        if(text==null) return false;
        String q="(?m)^"+Pattern.quote(ProtocolContract.SESSION_READY)
                +"\\s+SESSION_ID="+Pattern.quote(sessionId)
                +"\\s+PROFILE_ID="+Pattern.quote(profileId)
                +"\\s+SCHEMA_VERSION="+ProtocolContract.SCHEMA_VERSION+"\\s*$";
        return Pattern.compile(q).matcher(text).find();
    }

    /**
     * Expected visible response:
     * Resumen: ...
     * Avance: ...
     * A reforzar: ...
     * Próximo paso: ...
     */
    public static Report parseDebrief(String text){
        if(text==null||text.trim().isEmpty()) return null;
        String summary=value(text,"Resumen");
        String progress=value(text,"Avance");
        String reinforce=value(text,"A\\s+reforzar");
        String next=value(text,"Pr[oó]ximo\\s+paso");
        if(summary.isEmpty()||progress.isEmpty()||reinforce.isEmpty()||next.isEmpty()) return null;
        Report r=new Report();
        r.summary=summary;
        r.feedback="Avance: "+progress+"\nA reforzar: "+reinforce;
        r.nextObjective=next;
        long now=System.currentTimeMillis();
        r.events.add(new LearningEvent("session-progress","progreso-observado","observed",progress,now));
        r.events.add(new LearningEvent("session-reinforcement","a-reforzar","needs-practice",reinforce,now));
        return r;
    }

    private static String value(String text,String labelRegex){
        Pattern p=Pattern.compile("(?im)^\\s*(?:[-•*]\\s+)?(?:\\*\\*|__)?"
                +labelRegex+"(?:\\*\\*|__)?\\s*:\\s*(?:\\*\\*|__)?\\s*(.+?)\\s*$");
        Matcher m=p.matcher(text);
        String last="";
        while(m.find()) last=m.group(1).trim();
        return last;
    }
}
