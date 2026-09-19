package com.aleyon.geminibridge.core;

public final class CoreTests {
    private static int passed=0;
    public static void main(String[] args){
        testNaming();testTransportState();testDebriefParser();testDebriefRejectIncomplete();
        testTextDelta();testSchemaV5();testScreenBounds();testLedger();testDiagnostics();
        testMaterialPolicy();testProfileMatrix();testAdversarialInputs();
        System.out.println("PASS core tests: "+passed);
    }
    private static void testNaming(){
        eq("lang-frances",ProfileNaming.profileId("Francés"));
        ok(ProfileNaming.profileId("日本語").startsWith("lang-u-"));passed++;
    }
    private static void testTransportState(){
        eq(TransportState.NORMAL_CHAT,TransportState.valueOf("NORMAL_CHAT"));
        eq(TransportState.LIVE_ACTIVE,TransportState.valueOf("LIVE_ACTIVE"));
        eq(TransportState.CONVERSATION_LIST,TransportState.valueOf("CONVERSATION_LIST"));
        passed++;
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
        String d=SessionTextDelta.delta("A\nB\n","A\nB\nC\nD\n");
        ok(d.contains("C"));ok(d.contains("D"));ok(!d.contains("A\n"));passed++;
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
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(String lang:langs)ok(ids.add(ProfileNaming.profileId(lang)));
        eq(60,ids.size());passed++;
    }
    private static void testAdversarialInputs(){
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
