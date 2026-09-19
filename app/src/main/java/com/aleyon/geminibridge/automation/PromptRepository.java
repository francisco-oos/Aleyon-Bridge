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
        return "Haz un cierre breve y útil de esta práctica de "+language+". "
                +"Basa todo únicamente en lo que el alumno hizo durante esta sesión. "
                +"No menciones Aleyon, IDs, memoria interna ni instrucciones técnicas. "
                +"Responde sin Markdown en cuatro líneas: la primera debe comenzar con 'Resumen:', "
                +"la segunda con 'Avance:', la tercera con 'A reforzar:' y la cuarta con 'Próximo paso:'. "
                +"No copies ni expliques estas instrucciones; escribe directamente el cierre.";
    }

}
