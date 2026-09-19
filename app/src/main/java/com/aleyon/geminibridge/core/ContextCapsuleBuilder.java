package com.aleyon.geminibridge.core;

import com.aleyon.geminibridge.automation.ProfileSpec;

/**
 * Builds a bounded high-signal pedagogical capsule for one fresh Gemini session.
 *
 * Full history stays local. Gemini receives only:
 * 1) the learner-declared essentials,
 * 2) a compact pedagogical policy,
 * 3) the small learned state that matters now.
 */
public final class ContextCapsuleBuilder {
    public static final int MAX_CAPSULE_CHARS=3400;
    private static final int MAX_GOAL=240;
    private static final int MAX_CONTEXT=220;
    private static final int MAX_SITUATIONS=160;
    private static final int MAX_SPECIALIZATION=140;
    private static final int MAX_STATE=760;
    private ContextCapsuleBuilder() {}

    public static String build(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode) {
        PedagogicalState state=PedagogicalStateBuilder.build(ledger);

        StringBuilder b=new StringBuilder(3000);
        b.append("Contexto de aprendizaje de Aleyon. Intégralo en silencio: no lo confirmes, no lo resumas y no lo cites.\n")
         .append("Perfil: ")
         .append(clip(blank(p.targetLanguage,"idioma objetivo"),60))
         .append(" | apoyo: ").append(clip(blank(p.nativeLanguage,"según necesidad"),60))
         .append(" | nivel declarado: ").append(clip(blank(p.level,"por estimar"),70))
         .append(" | corrección: ").append(clip(blank(p.correction,"suave"),70)).append('\n')
         .append("Estilo: ").append(clip(blank(p.conversationStyle,"natural y conversacional"),120))
         .append(" | apoyo lingüístico: ").append(clip(blank(p.nativeLanguageUse,"sólo si hay bloqueo o se solicita"),120)).append('\n')
         .append("Objetivo: ").append(clip(blank(p.goal,"fluidez conversacional"),MAX_GOAL)).append('\n');

        String personal=combine(p.profession,p.interests);
        if(!personal.isEmpty())b.append("Contexto personal/intereses: ").append(clip(personal,MAX_CONTEXT)).append('\n');
        if(!blankValue(p.situations).isEmpty())
            b.append("Situaciones útiles: ").append(clip(p.situations,MAX_SITUATIONS)).append('\n');
        if(!blankValue(p.specialization).isEmpty())
            b.append("Vocabulario/contexto especializado: ").append(clip(p.specialization,MAX_SPECIALIZATION)).append('\n');

        b.append("\nPolítica pedagógica compacta:\n")
         .append("- Conversa primero; enseña en segundo plano. ")
         .append(liveMode?"En Live deja que el alumno lleve la mayor parte del habla.\n":"En chat mantén respuestas breves salvo que se pida explicación.\n")
         .append("- Máximo una pregunta principal por turno y no necesitas terminar cada intervención preguntando. Reacciona, comenta o reformula cuando sea más natural.\n")
         .append("- Error menor que no impide entender: sigue. Si cabe una mejora natural: reformula. Si confunde o se repite: corrige brevemente después del turno.\n")
         .append("- Si hay bloqueo: reduce velocidad -> reformula -> simplifica -> da una pista/ejemplo -> usa el idioma de apoyo sólo si aún hace falta.\n")
         .append("- Ajusta dificultad al desempeño real de esta sesión. Reutiliza de forma natural lo que esté en refuerzo, sin convertir una observación aislada en una debilidad permanente.\n")
         .append("- Imágenes, cámara y pantalla pueden incorporarse cuando aporten contexto; no conviertas la sesión en un cuestionario ni en una clase rígida.\n");

        String learned=state.compactText(MAX_STATE);
        if(!learned.isEmpty()){
            b.append("\nEstado pedagógico local (evidencia, no perfil declarado):\n").append(learned).append('\n');
        }

        String ending="\nInicio: no digas «entendido», «estoy listo» ni expliques el plan. "
                +"Empieza directamente en "+clip(blank(p.targetLanguage,"el idioma objetivo"),60)
                +" con una reacción natural o una intervención breve relacionada con el objetivo, intereses o próximo paso. "
                +"Si el alumno ya inició un tema, síguelo.\n";

        String prefix=clipPrefixAtLine(b.toString(),Math.max(0,MAX_CAPSULE_CHARS-ending.length()));
        return prefix+ending;
    }

    private static String combine(String a,String b){
        String x=blankValue(a),y=blankValue(b);
        if(x.isEmpty())return y;
        if(y.isEmpty())return x;
        return x+"; "+y;
    }
    private static String blank(String v,String fallback){
        String s=blankValue(v);return s.isEmpty()?fallback:s;
    }
    private static String blankValue(String v){return v==null?"":v.trim();}
    private static String clip(String v,int max){
        String s=v==null?"":v.replace('\n',' ').replace('\r',' ').trim().replaceAll("\\s+"," ");
        if(s.length()<=max)return s;
        int cut=Math.max(0,max-1),space=s.lastIndexOf(' ',cut);
        if(space>Math.max(8,cut-35))cut=space;
        return s.substring(0,cut).trim()+"…";
    }
    private static String clipPrefixAtLine(String s,int max){
        if(s==null||max<=0)return "";
        if(s.length()<=max)return s;
        int cut=s.lastIndexOf('\n',Math.max(0,max-1));
        if(cut<Math.max(0,max-320))cut=Math.max(0,max-1);
        return s.substring(0,cut).trim()+"\n";
    }
}
