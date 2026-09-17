package com.aleyon.geminibridge.automation;

import android.content.Context;
import com.aleyon.geminibridge.core.ContextCapsuleBuilder;
import com.aleyon.geminibridge.core.LearningLedger;
import com.aleyon.geminibridge.core.ProtocolContract;

/** Creates compact, session-scoped prompts. No notebook prompts exist in 0.4. */
public final class PromptRepository {
    @SuppressWarnings("unused") private final Context context;
    public PromptRepository(Context c){context=c.getApplicationContext();}

    public String contextCapsule(ProfileSpec p, LearningLedger ledger, String sessionId, boolean liveMode){
        return ContextCapsuleBuilder.build(p,ledger,sessionId,liveMode);
    }

    public String sessionReport(ProfileSpec p,String sessionId){
        return "ALEYON_SESSION_REPORT_REQUEST\\n"
                +"SESSION_ID="+sessionId+"\\nPROFILE_ID="+p.id+"\\nSCHEMA_VERSION="+ProtocolContract.SCHEMA_VERSION+"\\n"
                +"Analiza EXCLUSIVAMENTE la interacción del alumno posterior a la cápsula que contiene este SESSION_ID. "
                +"No uses errores o aciertos de sesiones anteriores como evidencia nueva. Devuelve exactamente este formato, sin Markdown extra:\\n"
                +"START_MARKER_FORMAT="+ProtocolContract.REPORT_BEGIN+" SESSION_ID=<SESSION_ID_ACTUAL> PROFILE_ID=<PROFILE_ID_ACTUAL> SCHEMA_VERSION=<SCHEMA_VERSION_ACTUAL>\\n"
                +"SUMMARY|resumen breve y útil de la sesión\\n"
                +"NEXT|un próximo objetivo concreto\\n"
                +"FEEDBACK|consejo breve que el alumno pueda ver al cerrar\\n"
                +"EVENT|categoria|habilidad|estado|evidencia textual exacta del alumno\\n"
                +"Máximo 6 EVENT. Si no hay evidencia suficiente, no inventes EVENT.\\n"
                +"END_MARKER_FORMAT="+ProtocolContract.REPORT_END+" SESSION_ID=<SESSION_ID_ACTUAL> PROFILE_ID=<PROFILE_ID_ACTUAL>";
    }
}
