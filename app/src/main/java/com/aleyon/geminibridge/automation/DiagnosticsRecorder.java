package com.aleyon.geminibridge.automation;

import android.content.Context;
import android.content.SharedPreferences;

import com.aleyon.geminibridge.core.AutomationDiagnostics;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bounded, best-effort ring buffer of recent {@link AutomationDiagnostics}
 * entries, kept for support/debugging after a field report of the automation
 * silently getting stuck. Diagnostics must never affect automation behaviour:
 * every failure here is swallowed.
 */
public final class DiagnosticsRecorder {
    private static final String PREFS = "aleyon_diagnostics";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_ENTRIES = 40;

    private final SharedPreferences prefs;

    public DiagnosticsRecorder(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void record(AutomationDiagnostics event) {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_RECENT, "[]"));
            arr.put(new JSONObject(event.toJsonString()));
            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, arr.length() - MAX_ENTRIES);
            for (int i = start; i < arr.length(); i++) {
                trimmed.put(arr.get(i));
            }
            prefs.edit().putString(KEY_RECENT, trimmed.toString()).apply();
        } catch (Exception ignored) {
            // Best-effort only; never let diagnostics break automation.
        }
    }

    public String recentJson() {
        return prefs.getString(KEY_RECENT, "[]");
    }
}
