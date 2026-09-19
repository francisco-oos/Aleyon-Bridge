package com.aleyon.geminibridge.transport;

import com.aleyon.geminibridge.core.TransportState;

/** Immutable semantic observation returned by the Android transport observer. */
public final class TransportObservation {
    public final TransportState state;
    public final boolean composerReady;
    public final boolean liveAvailable;
    public final String evidence;

    public TransportObservation(TransportState state, boolean composerReady,
            boolean liveAvailable, String evidence) {
        this.state=state==null?TransportState.UNKNOWN:state;
        this.composerReady=composerReady;
        this.liveAvailable=liveAvailable;
        this.evidence=evidence==null?"":evidence;
    }
}
