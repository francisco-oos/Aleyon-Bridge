package com.aleyon.geminibridge.core;

public final class CoreTests {
    private static int passed=0;
    public static void main(String[] args){
        testNaming();testCanonicalConversation();testCanonicalChatRouting();testAdaptiveNavigation();testTransportState();testRecovery();testReconciliation();testDebriefParser();
        testDebriefRejectIncomplete();testTextDelta();testSchemaV5();testScreenBounds();testLedger();testDiagnostics();
        testMaterialPolicy();testProfileMatrix();testAdversarialInputs();
        System.out.println("PASS core tests: "+passed);
    }
    private static void testNaming(){
        eq("lang-frances",ProfileNaming.profileId("Francés"));
        ok(ProfileNaming.profileId("日本語").startsWith("lang-u-"));passed++;
    }
    private static void testCanonicalConversation(){
        eq("ALEYON — Inglés",CanonicalConversationPolicy.title("Inglés"));
        eq(CanonicalConversationPolicy.Resolution.REUSE,CanonicalConversationPolicy.resolve(true));
        eq(CanonicalConversationPolicy.Resolution.REBUILD,CanonicalConversationPolicy.resolve(false));passed++;
    }
    private static void testCanonicalChatRouting(){
        eq(CanonicalChatRoutingPolicy.Action.REBUILD_DIRECT,
                CanonicalChatRoutingPolicy.decide(false,false,false));
        eq(CanonicalChatRoutingPolicy.Action.OPEN_VISIBLE,
                CanonicalChatRoutingPolicy.decide(true,true,false));
        eq(CanonicalChatRoutingPolicy.Action.SEARCH_KNOWN_ONCE,
                CanonicalChatRoutingPolicy.decide(true,false,false));
        eq(CanonicalChatRoutingPolicy.Action.REBUILD_AFTER_SEARCH,
                CanonicalChatRoutingPolicy.decide(true,false,true));
        passed++;
    }
    private static void testAdaptiveNavigation(){
        eq(AdaptiveNavigationPlanner.Action.OPEN_CONVERSATION_LIST,
                AdaptiveNavigationPlanner.next(TransportState.NORMAL_CHAT,false,false,false,false,0,false,false));
        eq(AdaptiveNavigationPlanner.Action.CREATE_NORMAL_CHAT,
                AdaptiveNavigationPlanner.next(TransportState.CONVERSATION_LIST,false,false,false,false,0,false,false));
        eq(AdaptiveNavigationPlanner.Action.OPEN_SEARCH,
                AdaptiveNavigationPlanner.next(TransportState.CONVERSATION_LIST,true,false,false,false,0,false,false));
        eq(AdaptiveNavigationPlanner.Action.TYPE_SEARCH_QUERY,
                AdaptiveNavigationPlanner.next(TransportState.CONVERSATION_SEARCH,true,false,true,false,0,false,false));
        eq(AdaptiveNavigationPlanner.Action.WAIT,
                AdaptiveNavigationPlanner.next(TransportState.CONVERSATION_SEARCH,true,false,true,true,2,false,false));
        eq(AdaptiveNavigationPlanner.Action.BACK,
                AdaptiveNavigationPlanner.next(TransportState.CONVERSATION_SEARCH,true,false,true,true,3,false,false));
        eq(AdaptiveNavigationPlanner.Action.COMPLETE_REUSE,
                AdaptiveNavigationPlanner.next(TransportState.NORMAL_CHAT,true,false,true,true,0,true,false));
        eq(AdaptiveNavigationPlanner.Action.COMPLETE_REBUILD,
                AdaptiveNavigationPlanner.next(TransportState.NORMAL_CHAT,false,false,false,false,0,false,true));
        eq(AdaptiveNavigationPlanner.Action.FAIL_CLOSED,
                AdaptiveNavigationPlanner.next(TransportState.UNKNOWN,true,false,false,false,0,false,false));
        passed++;
    }
    private static void testTransportState(){
        eq(TransportState.NORMAL_CHAT,TransportState.valueOf("NORMAL_CHAT"));
        eq(TransportState.CONVERSATION_SEARCH,TransportState.valueOf("CONVERSATION_SEARCH"));
        passed++;
    }
    private static void testRecovery(){
        eq(RecoveryPlanner.RecoveryAction.NONE,RecoveryPlanner.plan(SessionStage.READY,false));
        eq(RecoveryPlanner.RecoveryAction.RETRY_START,RecoveryPlanner.plan(SessionStage.CONTEXT_INJECTING,false));
        eq(RecoveryPlanner.RecoveryAction.RESTORE_LIVE_OVERLAY,RecoveryPlanner.plan(SessionStage.LIVE_ACTIVE,true));
        eq(RecoveryPlanner.RecoveryAction.FINISH_CLOSE,RecoveryPlanner.plan(SessionStage.LIVE_ACTIVE,false));
        eq(RecoveryPlanner.RecoveryAction.RESTORE_CHAT_OVERLAY,RecoveryPlanner.plan(SessionStage.CHAT_ACTIVE,false));passed++;
    }
    private static void testReconciliation(){
        eq(RecoveryPlanner.RecoveryAction.RETRY_START,
                RecoveryPlanner.reconcile(SessionStage.AMBIGUOUS_USER_REQUIRED,SessionStage.OPENING_SESSION_CHAT,false));
        eq(RecoveryPlanner.RecoveryAction.ASK_USER,
                RecoveryPlanner.reconcile(SessionStage.AMBIGUOUS_USER_REQUIRED,null,false));passed++;
    }
    private static void testDebriefParser(){
        String t="Resumen: Practicamos una presentación profesional.\n"
                +"Avance: Respondiste con frases más completas.\n"
                +"A reforzar: Mantener el pasado de forma consistente.\n"
                +"Próximo paso: Simular una entrevista técnica breve.";
        SessionReportParser.Report r=SessionReportParser.parseDebrief(t);
        ok(r!=null);eq("Practicamos una presentación profesional.",r.summary);
        ok(r.feedback.contains("frases más completas"));
        eq("Simular una entrevista técnica breve.",r.nextObjective);eq(2,r.events.size());
        ok(r.events.get(0).evidence.contains("frases más completas"));
        ok(r.events.get(1).evidence.contains("pasado"));passed++;
    }
    private static void testDebriefRejectIncomplete(){
        ok(SessionReportParser.parseDebrief("Resumen: Sólo una línea") == null);passed++;
    }
    private static void testTextDelta(){
        String d=SessionTextDelta.delta("A\nB\n","A\nB\nC\nD\n");ok(d.contains("C"));ok(d.contains("D"));ok(!d.contains("A\n"));passed++;
    }
    private static void testSchemaV5(){eq(6,ProtocolContract.SCHEMA_VERSION);passed++;}
    private static void testScreenBounds(){
        ok(ScreenBoundsPolicy.isActionableRect(10,10,50,50,0,0,100,100));
        ok(!ScreenBoundsPolicy.isActionableRect(10,150,50,200,0,0,100,100));
        ok(!ScreenBoundsPolicy.isActionableRect(-10,10,-1,20,0,0,0,0));passed++;
    }
    private static void testLedger(){
        LearningLedger l=new LearningLedger();l.setLastSessionSummary("x");l.setNextObjective("y");
        l.addVerified(new LearningEvent("grammar","past","learning","I went",1));
        l.addVerified(new LearningEvent("grammar","past","learning","",2));
        eq(1,l.snapshot().size());eq("x",l.getLastSessionSummary());passed++;
    }
    private static void testDiagnostics(){
        AutomationDiagnostics d=new AutomationDiagnostics("r","s","b","a",1L,"p",true,2,"sel",0,"ok","focused",2,"windows");
        ok(d.toJsonString().contains("\"rootSource\":\"focused\""));passed++;
    }

    private static void testMaterialPolicy(){
        SessionMaterial pdf=new SessionMaterial("m1",SessionMaterial.Kind.DOCUMENT,"manual.pdf","application/pdf",1024L,"content://docs/manual","pregúntame sobre el manual");
        SessionMaterial image=new SessionMaterial("m2",SessionMaterial.Kind.IMAGE,"foto.jpg","image/jpeg",2048L,"content://photos/1","describe la imagen");
        ok(MaterialHandoffPolicy.isSafe(pdf));ok(MaterialHandoffPolicy.isSafe(image));
        ok(MaterialHandoffPolicy.isSafeBatch(java.util.Arrays.asList(pdf,image)));
        ok(!MaterialHandoffPolicy.isSafe(new SessionMaterial("x",SessionMaterial.Kind.DOCUMENT,"bad.pdf","application/pdf",100,"file:///sdcard/bad.pdf","")));
        ok(!MaterialHandoffPolicy.isSafe(new SessionMaterial("x",SessionMaterial.Kind.DOCUMENT,"huge.pdf","application/pdf",MaterialHandoffPolicy.MAX_STANDARD_BYTES+1,"content://docs/huge","")));
        passed++;
    }
    private static void testProfileMatrix(){
        String[] langs={"Inglés","Francés","Alemán","Italiano","Portugués","Japonés","Coreano","Mandarín","Cantonés","Árabe","Hindi","Bengalí","Ruso","Ucraniano","Polaco","Checo","Eslovaco","Húngaro","Rumano","Búlgaro","Griego","Turco","Hebreo","Persa","Urdu","Punjabi","Tamil","Telugu","Maratí","Gujarati","Vietnamita","Tailandés","Indonesio","Malayo","Tagalo","Suajili","Afrikáans","Neerlandés","Sueco","Noruego","Danés","Finés","Islandés","Irlandés","Galés","Catalán","Gallego","Euskera","Maya yucateco","Tseltal","Náhuatl","Quechua","Guaraní","Esperanto","Latín","Serbio","Croata","Esloveno","Estonio","Letón"};
        java.util.HashSet<String> ids=new java.util.HashSet<>();java.util.HashSet<String> titles=new java.util.HashSet<>();
        for(String lang:langs){ok(ids.add(ProfileNaming.profileId(lang)));ok(titles.add(CanonicalConversationPolicy.title(lang)));}
        eq(60,ids.size());eq(60,titles.size());passed++;
    }

    private static void testAdversarialInputs(){
        eq("ALEYON — Inglés avanzado",CanonicalConversationPolicy.title("  Inglés\n avanzado  "));
        ok(CanonicalConversationPolicy.title("x".repeat(200)).length()<=89);
        ok(ProfileNaming.isValidProfileId("lang-ingles"));
        ok(ProfileNaming.isValidProfileId("lang-u-0123456789ab"));
        ok(!ProfileNaming.isValidProfileId("../lang-ingles"));
        ok(!ProfileNaming.isValidProfileId("lang-ingles\nother"));
        for(int i=0;i<200;i++){
            SessionMaterial good=new SessionMaterial("id"+i,SessionMaterial.Kind.DOCUMENT,"doc"+i+".pdf","application/pdf",1+i,"content://provider/doc/"+i,"");
            ok(MaterialHandoffPolicy.isSafe(good));
            SessionMaterial bad=new SessionMaterial("id"+i,SessionMaterial.Kind.DOCUMENT,"doc\n"+i,"application/pdf",1+i,"content://provider/doc/"+i,"");
            ok(!MaterialHandoffPolicy.isSafe(bad));
        }
        passed++;
    }
    private static void ok(boolean x){if(!x)throw new AssertionError("Expected true");}
    private static void eq(Object a,Object b){if(a==null?b!=null:!a.equals(b))throw new AssertionError("Expected ["+a+"] got ["+b+"]");}
}
