package com.aleyon.geminibridge.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the report block tagged with the current session/profile/schema. */
public final class SessionReportParser {
    public static final class Report {
        public String summary="", nextObjective="", feedback="";
        public final List<LearningEvent> events=new ArrayList<>();
    }
    private SessionReportParser() {}

    public static boolean hasSessionReady(String text,String sessionId,String profileId){
        return tagged(text, ProtocolContract.SESSION_READY, sessionId, profileId);
    }

    public static Report parse(String text,String sessionId,String profileId){ return parse(text,sessionId,profileId,null); }

    public static Report parse(String text,String sessionId,String profileId,String evidenceText){
        String block=reportBlock(text,sessionId,profileId);
        if(block==null) return null;
        Report r=new Report();
        for(String raw:block.split("\\r?\\n")){
            String line=raw.trim();
            if(line.startsWith("SUMMARY|")) r.summary=line.substring(8).trim();
            else if(line.startsWith("NEXT|")) r.nextObjective=line.substring(5).trim();
            else if(line.startsWith("FEEDBACK|")) r.feedback=line.substring(9).trim();
            else if(line.startsWith("EVENT|")){
                String[] p=line.split("\\|",5);
                if(p.length==5 && !p[4].trim().isEmpty()) {
                    String evidence=p[4].trim();
                    if(evidenceText==null || evidenceText.contains(evidence)) r.events.add(new LearningEvent(p[1],p[2],p[3],evidence,System.currentTimeMillis()));
                }
            }
        }
        return r;
    }

    private static boolean tagged(String text,String marker,String sid,String pid){
        if(text==null) return false;
        String q="(?m)^"+Pattern.quote(marker)+"\\s+SESSION_ID="+Pattern.quote(sid)+"\\s+PROFILE_ID="+Pattern.quote(pid)+"\\s+SCHEMA_VERSION="+ProtocolContract.SCHEMA_VERSION+"\\s*$";
        return Pattern.compile(q).matcher(text).find();
    }
    private static String reportBlock(String text,String sid,String pid){
        if(text==null) return null;
        String head=ProtocolContract.REPORT_BEGIN+" SESSION_ID="+sid+" PROFILE_ID="+pid+" SCHEMA_VERSION="+ProtocolContract.SCHEMA_VERSION;
        String tail=ProtocolContract.REPORT_END+" SESSION_ID="+sid+" PROFILE_ID="+pid;
        int a=text.lastIndexOf(head); if(a<0)return null; int b=text.indexOf(tail,a+head.length()); if(b<0)return null;
        return text.substring(a+head.length(),b);
    }
}
