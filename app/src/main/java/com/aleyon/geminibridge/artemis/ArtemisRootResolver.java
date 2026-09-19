/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Adapted for Aleyon Bridge from Google Artemis
 * packages/artemis-accessibility-helper/.../HierarchyDumper.java.
 * Changes: removed HTTP/ADB/screenshot surfaces, retained only bounded
 * multi-window root discovery and focused-node recovery, and added a
 * package verifier so Bridge can never resolve outside Gemini.
 */
package com.aleyon.geminibridge.artemis;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Narrow in-process port of Artemis hierarchy root discovery.
 *
 * The returned node remains live for the caller. No server, shell, ADB,
 * screenshot or gesture authority is exposed.
 */
public final class ArtemisRootResolver {
    private static final int PREFETCH_DESCENDANTS_HYBRID = 1 << 3;
    private static final long[] RETRY_BACKOFF_MS = {40L,80L,120L,160L,220L,300L};

    public interface Verifier {
        boolean trusted(AccessibilityNodeInfo node);
    }

    public static final class Result {
        public final AccessibilityNodeInfo root;
        public final String source;
        public final int windowCount;
        public final String summary;

        Result(AccessibilityNodeInfo root,String source,int windowCount,String summary){
            this.root=root;this.source=source;this.windowCount=windowCount;this.summary=summary;
        }
    }

    private ArtemisRootResolver(){}

    public static Result resolve(AccessibilityService service,Verifier verifier){
        if(service==null||verifier==null)return new Result(null,"none",0,"service/verifier unavailable");
        Result last=new Result(null,"none",0,"");
        for(int attempt=0;attempt<=RETRY_BACKOFF_MS.length;attempt++){
            last=resolveOnce(service,verifier);
            if(last.root!=null)return last;
            if(attempt<RETRY_BACKOFF_MS.length)SystemClock.sleep(RETRY_BACKOFF_MS[attempt]);
        }
        return last;
    }

    private static Result resolveOnce(AccessibilityService service,Verifier verifier){
        List<AccessibilityWindowInfo> windows=null;
        try{windows=service.getWindows();}catch(Throwable ignored){}
        int windowCount=windows==null?0:windows.size();
        StringBuilder summary=new StringBuilder();

        AccessibilityNodeInfo focusedTrusted=null;
        AccessibilityNodeInfo activeTrusted=null;
        AccessibilityNodeInfo soleTrusted=null;
        int trustedAppWindows=0;

        if(windows!=null&&!windows.isEmpty()){
            List<AccessibilityWindowInfo> sorted=new ArrayList<>(windows);
            Collections.sort(sorted,new Comparator<AccessibilityWindowInfo>(){
                @Override public int compare(AccessibilityWindowInfo a,AccessibilityWindowInfo b){
                    return Integer.compare(b.getLayer(),a.getLayer());
                }
            });

            for(AccessibilityWindowInfo w:sorted){
                if(w==null)continue;
                AccessibilityNodeInfo root=null;
                try{root=windowRoot(w);}catch(Throwable ignored){}
                boolean trusted=root!=null&&verifier.trusted(root);
                if(summary.length()>0)summary.append(" | ");
                summary.append("type=").append(w.getType())
                        .append(",layer=").append(w.getLayer())
                        .append(",active=").append(w.isActive())
                        .append(",focused=").append(w.isFocused())
                        .append(",trusted=").append(trusted);
                if(!trusted||w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                trustedAppWindows++;
                soleTrusted=root;
                if(w.isFocused())focusedTrusted=root;
                if(w.isActive())activeTrusted=root;
            }
        }

        if(focusedTrusted!=null)return new Result(focusedTrusted,"artemis-window-focused",windowCount,summary.toString());
        if(activeTrusted!=null)return new Result(activeTrusted,"artemis-window-active",windowCount,summary.toString());

        AccessibilityNodeInfo active=null;
        try{active=service.getRootInActiveWindow();}catch(Throwable ignored){}
        if(active!=null&&verifier.trusted(active))
            return new Result(active,"artemis-active-root",windowCount,summary.toString());

        if(trustedAppWindows==1&&soleTrusted!=null)
            return new Result(soleTrusted,"artemis-sole-application",windowCount,summary.toString());

        AccessibilityNodeInfo focus=null;
        try{focus=service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);}catch(Throwable ignored){}
        if(focus==null)try{focus=service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);}catch(Throwable ignored){}
        if(focus!=null){
            try{
                AccessibilityNodeInfo current=focus;
                AccessibilityNodeInfo parent=current.getParent();
                while(parent!=null){current=parent;parent=current.getParent();}
                if(verifier.trusted(current))
                    return new Result(current,"artemis-focused-backtrack",windowCount,summary.toString());
            }catch(Throwable ignored){}
        }
        return new Result(null,"none",windowCount,summary.toString());
    }

    private static AccessibilityNodeInfo windowRoot(AccessibilityWindowInfo window){
        if(Build.VERSION.SDK_INT>=33){
            try{return window.getRoot(PREFETCH_DESCENDANTS_HYBRID);}catch(Throwable ignored){}
        }
        return window.getRoot();
    }
}
