package com.aleyon.geminibridge.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only pedagogical state derived from verified local evidence.
 *
 * It is deliberately separate from the learner-declared profile: Aleyon may
 * observe progress and possible patterns, but never silently rewrites what the
 * learner declared about themselves.
 */
public final class PedagogicalState {
    public static final class Pattern {
        public final String text;
        public final int evidenceCount;
        public final boolean confirmed;
        public Pattern(String text,int evidenceCount,boolean confirmed){
            this.text=safe(text);this.evidenceCount=Math.max(1,evidenceCount);this.confirmed=confirmed;
        }
    }

    private final List<String> progress;
    private final List<Pattern> reinforcement;
    private final List<String> vocabulary;
    private final String levelEstimate;
    private final String nextObjective;
    private final String lastSummary;
    private final int evidenceCount;

    public PedagogicalState(List<String> progress,List<Pattern> reinforcement,
                            List<String> vocabulary,String levelEstimate,
                            String nextObjective,String lastSummary,int evidenceCount){
        this.progress=immutable(progress);
        this.reinforcement=reinforcement==null
                ?Collections.emptyList()
                :Collections.unmodifiableList(new ArrayList<>(reinforcement));
        this.vocabulary=immutable(vocabulary);
        this.levelEstimate=safe(levelEstimate);
        this.nextObjective=safe(nextObjective);
        this.lastSummary=safe(lastSummary);
        this.evidenceCount=Math.max(0,evidenceCount);
    }

    public List<String> progress(){return progress;}
    public List<Pattern> reinforcement(){return reinforcement;}
    public List<String> vocabulary(){return vocabulary;}
    public String levelEstimate(){return levelEstimate;}
    public String nextObjective(){return nextObjective;}
    public String lastSummary(){return lastSummary;}
    public int evidenceCount(){return evidenceCount;}
    public boolean isEmpty(){
        return progress.isEmpty()&&reinforcement.isEmpty()&&vocabulary.isEmpty()
                &&levelEstimate.isEmpty()&&nextObjective.isEmpty()&&lastSummary.isEmpty();
    }

    /**
     * High-signal representation for the provider capsule. It never emits raw
     * history and enforces its own independent character budget.
     */
    public String compactText(int maxChars){
        if(maxChars<=0||isEmpty())return "";
        StringBuilder b=new StringBuilder(Math.min(maxChars,800));
        appendLine(b,"Avances recientes",join(progress),maxChars);
        if(!reinforcement.isEmpty()){
            List<String> items=new ArrayList<>();
            for(Pattern p:reinforcement){
                items.add(p.text+(p.confirmed?" [patrón confirmado x"+p.evidenceCount+"]":" [observación]"));
            }
            appendLine(b,"A reforzar",join(items),maxChars);
        }
        appendLine(b,"Vocabulario activo",join(vocabulary),maxChars);
        appendLine(b,"Nivel estimado",levelEstimate,maxChars);
        appendLine(b,"Próximo objetivo",nextObjective,maxChars);
        if(b.length()<maxChars/2)appendLine(b,"Continuidad",lastSummary,maxChars);
        String out=b.toString().trim();
        return out.length()<=maxChars?out:clipAtBoundary(out,maxChars);
    }

    private static void appendLine(StringBuilder b,String label,String value,int max){
        if(value==null||value.trim().isEmpty()||b.length()>=max)return;
        String line=label+": "+value.trim();
        int remaining=max-b.length()-(b.length()==0?0:1);
        if(remaining<=0)return;
        if(line.length()>remaining)line=clipAtBoundary(line,remaining);
        if(b.length()>0)b.append('\n');
        b.append(line);
    }
    private static String join(List<String> values){
        StringBuilder b=new StringBuilder();
        for(String v:values){
            if(v==null||v.trim().isEmpty())continue;
            if(b.length()>0)b.append(" | ");
            b.append(v.trim());
        }
        return b.toString();
    }
    private static List<String> immutable(List<String> values){
        return values==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(values));
    }
    private static String clipAtBoundary(String s,int max){
        if(s==null||max<=0)return "";
        if(s.length()<=max)return s;
        int cut=Math.max(0,max-1);
        int space=s.lastIndexOf(' ',cut);
        if(space>Math.max(8,cut-40))cut=space;
        return s.substring(0,cut).trim()+"…";
    }
    private static String safe(String v){return v==null?"":v.trim();}
}
