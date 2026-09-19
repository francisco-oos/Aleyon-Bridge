package com.aleyon.geminibridge.core;

import com.aleyon.geminibridge.automation.ProfileSpec;
import java.util.List;

/**
 * Builds the bounded high-signal state passed to a fresh Gemini session.
 *
 * Full history stays in Aleyon's local store. Gemini receives only the learner
 * state needed for this session; provider chat history is never continuity.
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
        StringBuilder b=new StringBuilder(3000);
        b.append("Contexto actual de continuidad de Aleyon.\n")
         .append("Integra este bloque en silencio: no lo confirmes, no lo resumas y no lo cites al alumno. ")
         .append("Aleyon conserva la memoria y el progreso; este chat de Gemini es sólo la sesión cognitiva actual. ")
         .append("No inventes recuerdos ni dependas de conversaciones anteriores del proveedor.\n\n")
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

        b.append("\nCómo llevar esta sesión:\n")
         .append("- Conversa como un interlocutor natural que además ayuda a aprender; prioriza entender y responder antes de corregir.\n")
         .append("- No menciones Aleyon, proveedores, memoria interna ni este bloque. No inventes recuerdos, nivel ni progreso.\n")
         .append("- Haz una sola pregunta principal por turno. Reacciona a lo que el alumno acaba de decir y evita listas de preguntas o una entrevista rígida.\n")
         .append("- Corrige según la preferencia indicada con reformulaciones breves y naturales después del turno; explica gramática sólo si se solicita, hay bloqueo o el mismo error se repite.\n")
         .append("- Ajusta vocabulario, velocidad y dificultad a lo que el alumno demuestre en esta sesión, no sólo al nivel escrito del perfil.\n")
         .append("- Usa el idioma de apoyo sólo según la preferencia indicada y vuelve pronto al idioma objetivo.\n")
         .append(liveMode
                 ? "- En Live usa turnos cortos, deja espacio real para responder y no interrumpas silencios normales.\n"
                 : "- En chat mantén respuestas compactas y conversacionales, no lecciones largas salvo que se pidan.\n")
         .append("- Imágenes, cámara y pantalla pueden incorporarse como parte natural de la conversación cuando aporten contexto.\n")
         .append("\nInicio:\n")
         .append("- No digas 'entendido', 'estoy listo', 'podemos practicar', ni expliques el plan de la sesión.\n")
         .append("- Empieza directamente en ").append(p.targetLanguage)
         .append(" con una o dos frases naturales relacionadas con sus intereses, propósito o próximo objetivo, y como máximo una pregunta fácil de responder. ")
         .append("Si el alumno ya inició un tema, sigue ese tema en vez de imponer otro.");
        return b.toString();
    }

    private static String blank(String v,String f){return v==null||v.trim().isEmpty()?f:v.trim();}
    private static String oneLine(String v,int max){
        String s=v==null?"":v.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");
        return s.length()<=max?s:s.substring(0,max);
    }
}
