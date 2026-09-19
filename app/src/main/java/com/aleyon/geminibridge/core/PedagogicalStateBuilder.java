package com.aleyon.geminibridge.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Derives a compact pedagogical state only from Aleyon's verified local ledger.
 *
 * No LLM call is made here. A single reinforcement observation remains an
 * observation; only repeated equivalent evidence becomes a confirmed pattern.
 */
public final class PedagogicalStateBuilder {
    private static final int MAX_PROGRESS=2;
    private static final int MAX_REINFORCEMENT=2;
    private static final int MAX_VOCABULARY=4;
    private static final int LOOKBACK_EVENTS=40;
    private PedagogicalStateBuilder(){}

    public static PedagogicalState build(LearningLedger ledger){
        if(ledger==null)return new PedagogicalState(null,null,null,"","","",0);
        List<LearningEvent> events=ledger.snapshot();
        Map<String,Integer> reinforcementCounts=new HashMap<>();
        for(LearningEvent e:events){
            if(isReinforcement(e)){
                String key=semanticKey(e.evidence);
                if(!key.isEmpty())reinforcementCounts.put(key,reinforcementCounts.getOrDefault(key,0)+1);
            }
        }

        List<String> progress=new ArrayList<>();
        List<PedagogicalState.Pattern> reinforce=new ArrayList<>();
        List<String> vocabulary=new ArrayList<>();
        Set<String> seenProgress=new HashSet<>(),seenReinforce=new HashSet<>(),seenVocabulary=new HashSet<>();
        String level="";
        int inspected=0;
        for(int i=events.size()-1;i>=0&&inspected<LOOKBACK_EVENTS;i--,inspected++){
            LearningEvent e=events.get(i);
            if(progress.size()<MAX_PROGRESS&&isProgress(e)){
                addUnique(progress,seenProgress,clip(e.evidence,180),MAX_PROGRESS);
            }
            if(reinforce.size()<MAX_REINFORCEMENT&&isReinforcement(e)){
                String text=clip(e.evidence,180),key=semanticKey(text);
                if(!key.isEmpty()&&seenReinforce.add(key)){
                    int count=reinforcementCounts.getOrDefault(key,1);
                    reinforce.add(new PedagogicalState.Pattern(text,count,count>=2));
                }
            }
            if(vocabulary.size()<MAX_VOCABULARY&&isVocabulary(e)){
                String word=!e.skill.isEmpty()?e.skill:e.evidence;
                addUnique(vocabulary,seenVocabulary,clip(word,80),MAX_VOCABULARY);
            }
            if(level.isEmpty()&&isLevelEstimate(e)){
                level=clip(!e.evidence.isEmpty()?e.evidence:e.skill,80);
            }
        }
        return new PedagogicalState(progress,reinforce,vocabulary,level,
                clip(ledger.getNextObjective(),220),clip(ledger.getLastSessionSummary(),220),events.size());
    }

    private static boolean isProgress(LearningEvent e){
        return e!=null&&("session-progress".equalsIgnoreCase(e.category)
                ||"progress".equalsIgnoreCase(e.category));
    }
    private static boolean isReinforcement(LearningEvent e){
        return e!=null&&("session-reinforcement".equalsIgnoreCase(e.category)
                ||"reinforcement".equalsIgnoreCase(e.category)
                ||"needs-practice".equalsIgnoreCase(e.status));
    }
    private static boolean isVocabulary(LearningEvent e){
        if(e==null)return false;
        String c=(e.category+" "+e.skill).toLowerCase(Locale.ROOT);
        return c.contains("vocab")||c.contains("lexicon")||c.contains("word-learning");
    }
    private static boolean isLevelEstimate(LearningEvent e){
        if(e==null)return false;
        String c=(e.category+" "+e.skill).toLowerCase(Locale.ROOT);
        return c.contains("level-estimate")||c.contains("nivel-estim");
    }
    private static void addUnique(List<String> out,Set<String> seen,String value,int max){
        String key=semanticKey(value);
        if(value==null||value.isEmpty()||key.isEmpty()||out.size()>=max||!seen.add(key))return;
        out.add(value);
    }
    private static String semanticKey(String s){
        if(s==null)return "";
        String n=Normalizer.normalize(s,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+"," ")
                .trim().replaceAll("\\s+"," ");
        return n.length()>140?n.substring(0,140):n;
    }
    private static String clip(String v,int max){
        String s=v==null?"":v.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");
        if(s.length()<=max)return s;
        int cut=Math.max(0,max-1),space=s.lastIndexOf(' ',cut);
        if(space>Math.max(8,cut-35))cut=space;
        return s.substring(0,cut).trim()+"…";
    }
}
