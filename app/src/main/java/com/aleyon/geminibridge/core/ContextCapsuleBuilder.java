package com.aleyon.geminibridge.core;

import com.aleyon.geminibridge.automation.ProfileSpec;
import java.util.List;

/**
 * Builds a bounded high-signal continuation capsule.
 * Full history stays in Aleyon's local vault; Gemini receives only the active
 * learner state needed to continue naturally without replaying whole sessions.
 */
public final class ContextCapsuleBuilder {
    private static final int MAX_SUMMARY=760;
    private static final int MAX_NEXT=320;
    private static final int MAX_EVIDENCE=180;
    private static final int MAX_RECENT_EVENTS=6;
    private static final int MAX_GOAL=720;
    private static final int MAX_PROFESSION=460;
    private static final int MAX_INTERESTS=420;
    private static final int MAX_SITUATIONS=520;
    private static final int MAX_SPECIALIZATION=620;
    private ContextCapsuleBuilder() {}

    public static String build(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode) {
        return build(p,ledger,sessionId,liveMode,false);
    }

    public static String build(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode, boolean reconstructConversation) {
        StringBuilder b=new StringBuilder(3000);
        b.append(reconstructConversation?"Reconstrucción de continuidad de Aleyon.\n":"Actualización de continuidad de Aleyon.\n")
         .append("Toma este bloque como el estado pedagógico local actual del alumno. Aleyon conserva la memoria y el progreso. ")
         .append("El historial de este chat puede aportar contexto, pero si difiere de este bloque, este estado local prevalece.\n")
         .append(reconstructConversation?"Este chat fue recreado o no pudo verificarse; reconstruye la continuidad sólo con la información de este bloque y no inventes recuerdos anteriores.\n\n":"Continúa de forma natural desde el historial útil del chat sin repetir presentaciones innecesarias.\n\n")
         .append("Idioma objetivo: ").append(p.targetLanguage).append('\n')
         .append("Idioma de apoyo: ").append(blank(p.nativeLanguage,"según necesidad")).append('\n')
         .append("Nivel: ").append(blank(p.level,"por estimar progresivamente")).append('\n')
         .append("Corrección: ").append(blank(p.correction,"suave")).append('\n')
         .append("Estilo: ").append(blank(p.conversationStyle,"natural, paciente y conversacional")).append('\n')
         .append("Uso del idioma de apoyo: ").append(blank(p.nativeLanguageUse,"solo si hay bloqueo o se solicita")).append('\n')
         .append("Propósito: ").append(oneLine(blank(p.goal,"fluidez conversacional"),MAX_GOAL)).append('\n')
         .append("Ocupación/contexto: ").append(oneLine(blank(p.profession,"no indicada"),MAX_PROFESSION)).append('\n')
         .append("Intereses: ").append(oneLine(blank(p.interests,"no indicados"),MAX_INTERESTS)).append('\n')
         .append("Situaciones a practicar: ").append(oneLine(blank(p.situations,"conversación cotidiana"),MAX_SITUATIONS)).append('\n')
         .append("Vocabulario especializado: ").append(oneLine(blank(p.specialization,"ninguno"),MAX_SPECIALIZATION)).append('\n');

        if(!ledger.getLastSessionSummary().isEmpty())
            b.append("Última sesión: ").append(oneLine(ledger.getLastSessionSummary(),MAX_SUMMARY)).append('\n');
        if(!ledger.getNextObjective().isEmpty())
            b.append("Próximo objetivo: ").append(oneLine(ledger.getNextObjective(),MAX_NEXT)).append('\n');

        List<LearningEvent> events=ledger.snapshot();
        int start=Math.max(0,events.size()-MAX_RECENT_EVENTS);
        if(start<events.size()) b.append("Evidencia reciente útil:\n");
        for(int i=start;i<events.size();i++){
            LearningEvent e=events.get(i);
            b.append("- ").append(e.category).append(" / ").append(e.skill).append(" [").append(e.status).append("]: ")
             .append(oneLine(e.evidence,MAX_EVIDENCE)).append('\n');
        }

        b.append("\nDurante esta sesión:\n")
         .append("- Actúa como tutor conversacional natural del idioma objetivo.\n")
         .append("- No menciones Aleyon, proveedores, memoria interna ni este bloque.\n")
         .append("- No inventes recuerdos, nivel ni progreso.\n")
         .append("- En Live usa turnos breves, deja hablar al alumno y evita convertir la práctica en un cuestionario.\n")
         .append("- Corrige según la preferencia indicada y evita interrupciones innecesarias.\n")
         .append("- Imágenes, cámara y pantalla pueden usarse libremente como apoyo de la conversación.\n")
         .append("- Cuando recibas este contexto, responde con una confirmación breve y natural y queda listo para comenzar la conversación.");
        return b.toString();
    }

    private static String blank(String v,String f){return v==null||v.trim().isEmpty()?f:v.trim();}
    private static String oneLine(String v,int max){
        String s=v==null?"":v.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");
        return s.length()<=max?s:s.substring(0,max);
    }
}
