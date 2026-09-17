package com.aleyon.geminibridge.automation;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tiny persistent command mailbox between MainActivity and AccessibilityService.
 * Persistence means a user may enable Accessibility after pressing a command and
 * the service can still pick it up when Android binds it.
 */
public final class AutomationCommandBus {
    private static final String PREFS = "aleyon_command_bus";
    private static final String KEY_PENDING = "pending";

    private AutomationCommandBus() {}

    public static void enqueue(Context c, AutomationRequest request) throws Exception {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING, request.toJson()).apply();
    }

    public static AutomationRequest consume(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_PENDING, null);
        if (raw == null) return null;
        p.edit().remove(KEY_PENDING).apply();
        try {
            return AutomationRequest.fromJson(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
