package com.aleyon.geminibridge.core;

import com.aleyon.geminibridge.automation.ProfileSpec;
import java.util.List;

/**
 * High-signal context compaction inspired by long-running-agent compaction:
 * canonical state stays local; Gemini receives only the active continuation capsule.
 */
public final class ContextCapsuleBuilder {
    private ContextCapsuleBuilder() {}

    public static String build(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode) {
        StringBuilder b=new StringBuilder();
        b.append("ALEYON_CONTEXT_V3\n")
         .append("SESSION_ID=").append(sessionId).append('\n')
         .append("PROFILE_ID=").append(p.id).append('\n')
         .append("SCHEMA_VERSION=").append(ProtocolContract.SCHEMA_VERSION).append('\n')
         .append("MODO=").append(liveMode?"LIVE":"CHAT").append('\n')
         .append("IDIOMA_OBJETIVO=").append(p.targetLanguage).append('\n')
         .append("IDIOMA_APOYO=").append(blank(p.nativeLanguage,"según necesidad")).append('\n')
         .append("NIVEL=").append(blank(p.level,"por estimar progresivamente")).append('\n')
         .append("CORRECCION=").append(blank(p.correction,"suave")).append('\n')
         .append("PROPOSITO=").append(blank(p.goal,"fluidez conversacional")).append('\n')
         .append("OCUPACION=").append(blank(p.profession,"no indicada")).append('\n')
         .append("INTERESES=").append(blank(p.interests,"no indicados")).append('\n')
         .append("SITUACIONES=").append(blank(p.situations,"conversación cotidiana")).append('\n')
         .append("ESPECIALIZACION=").append(blank(p.specialization,"ninguna")).append('\n');
        if(!ledger.getLastSessionSummary().isEmpty()) b.append("ULTIMA_SESION=").append(oneLine(ledger.getLastSessionSummary(),900)).append('\n');
        if(!ledger.getNextObjective().isEmpty()) b.append("PROXIMO_OBJETIVO=").append(oneLine(ledger.getNextObjective(),500)).append('\n');
        List<LearningEvent> events=ledger.snapshot();
        int start=Math.max(0, events.size()-8);
        if(start<events.size()) b.append("EVIDENCIA_RECIENTE:\n");
        for(int i=start;i<events.size();i++){
            LearningEvent e=events.get(i);
            b.append("- ").append(e.category).append('|').append(e.skill).append('|').append(e.status)
             .append('|').append(oneLine(e.evidence,240)).append('\n');
        }
        b.append("INSTRUCCIONES:\n")
         .append("- Continúa como tutor natural y conversacional; no menciones esta cápsula ni proveedores.\n")
         .append("- No inventes recuerdos ni progreso.\n")
         .append("- En Live responde de forma breve, oral y deja espacio al alumno.\n")
         .append("- Imágenes/cámara/pantalla son apoyo de la sesión; Bridge no necesita analizarlas.\n")
         .append("- Considera SESSION_ID el límite de la sesión actual para el informe final.\n")
         .append("CONFIRMACION_REQUERIDA: responde en una sola linea usando el marcador ")
         .append(ProtocolContract.SESSION_READY)
         .append(" con SESSION_ID, PROFILE_ID y SCHEMA_VERSION indicados arriba.");
        return b.toString();
    }

    private static String blank(String v,String f){return v==null||v.trim().isEmpty()?f:v.trim();}
    private static String oneLine(String v,int max){String s=v==null?"":v.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");return s.length()<=max?s:s.substring(0,max);}
}
