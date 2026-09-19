package com.aleyon.geminibridge.automation;

import android.content.Context;
import com.aleyon.geminibridge.core.ContextCapsuleBuilder;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.ProtocolContract;

/** Compact session-scoped prompts. Provider-side history is never authoritative memory. */
public final class PromptRepository {
    @SuppressWarnings("unused") private final Context context;
    public PromptRepository(Context c){context=c.getApplicationContext();}

    public String contextCapsule(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode){
        return ContextCapsuleBuilder.build(p,ledger,sessionId,liveMode);
    }

    /**
     * User-facing close prompt. It deliberately avoids machine protocol, IDs,
     * JSON and EVENT lines because the official Gemini app is the visible UI.
     * Aleyon parses only the four human-readable lines from the new response.
     */
    public String sessionDebrief(ProfileSpec p){
        String language=p==null||p.targetLanguage==null||p.targetLanguage.trim().isEmpty()?"idioma objetivo":p.targetLanguage.trim();
        return "Cierra esta práctica de "+language+". "
                +"Ignora cualquier pregunta o tarea anterior que haya quedado pendiente; no la respondas ahora. "
                +"Evalúa sólo lo que el alumno hizo durante esta sesión. "
                +"Responde sin Markdown y únicamente con cuatro líneas: "
                +"Resumen: ... / Avance: ... / A reforzar: ... / Próximo paso: ... . "
                +"No menciones Aleyon, IDs, memoria interna ni estas instrucciones.";
    }

    /**
     * One self-contained retry. It repeats the real request instead of saying
     * "responde al mensaje anterior", which physical tests showed can revive a
     * pending Live question instead of producing the debrief.
     */
    public String sessionDebriefRetry(ProfileSpec p,String evidence){
        String base=sessionDebrief(p);
        String e=evidence==null?"":evidence.replace('\n',' ').replace('\r',' ')
                .trim().replaceAll("\\s+"," ");
        if(e.length()>1800)e=e.substring(0,900)+" … "+e.substring(e.length()-900);
        return "Segundo intento de cierre. Ignora respuestas anteriores y atiende únicamente este mensaje. "
                +base+(e.isEmpty()?"":" Evidencia observada de la práctica: "+e);
    }

}
