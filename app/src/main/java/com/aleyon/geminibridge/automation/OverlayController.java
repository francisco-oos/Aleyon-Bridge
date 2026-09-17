package com.aleyon.geminibridge.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimal global Aleyon bubble shown while an Aleyon-managed Chat or Live session is active.
 * TYPE_ACCESSIBILITY_OVERLAY does not require the broad SYSTEM_ALERT_WINDOW
 * permission; it is tied to the explicitly-enabled accessibility service.
 */
public final class OverlayController {
    public interface Listener {
        void onReturnToGemini();
        void onCloseRequested();
    }

    private final AccessibilityService service;
    private final WindowManager wm;
    private final Listener listener;
    private View bubble;
    private View panel;
    private String label = "";
    private String objective = "";
    private String starter = "";

    public OverlayController(AccessibilityService service, Listener listener) {
        this.service = service;
        this.listener = listener;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(String profileLabel, String objectiveToday, String starterPhrase) {
        label = profileLabel == null ? "" : profileLabel;
        objective = objectiveToday == null ? "" : objectiveToday;
        starter = starterPhrase == null ? "" : starterPhrase;
        if (bubble != null) return;

        Button b = new Button(service);
        b.setText("A");
        b.setTextColor(Color.WHITE);
        b.setTextSize(18f);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(38, 90, 197));
        bg.setShape(GradientDrawable.OVAL);
        b.setBackground(bg);
        b.setOnClickListener(v -> togglePanel());
        bubble = b;

        WindowManager.LayoutParams lp = baseParams(64, 64);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.x = 14;
        wm.addView(bubble, lp);
    }

    public void updateHints(String profileLabel, String objectiveToday, String starterPhrase) {
        label = profileLabel == null ? "" : profileLabel;
        objective = objectiveToday == null ? "" : objectiveToday;
        starter = starterPhrase == null ? "" : starterPhrase;
        if (panel != null) {
            hidePanel();
            showPanel();
        }
    }

    public void hide() {
        hidePanel();
        if (bubble != null) {
            wm.removeView(bubble);
            bubble = null;
        }
    }

    private void togglePanel() {
        if (panel == null) showPanel();
        else hidePanel();
    }

    private void showPanel() {
        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 22, 28, 22);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(245, 255, 255, 255));
        bg.setCornerRadius(28);
        bg.setStroke(2, Color.rgb(218, 225, 237));
        box.setBackground(bg);

        TextView title = new TextView(service);
        title.setText("Aleyon — " + label);
        title.setTextSize(17f);
        title.setTextColor(Color.rgb(23, 32, 51));
        box.addView(title);

        if (objective != null && !objective.trim().isEmpty()) {
            TextView o = new TextView(service);
            o.setText("Objetivo: " + objective);
            o.setTextSize(13f);
            o.setTextColor(Color.rgb(70, 82, 105));
            o.setPadding(0, 12, 0, 4);
            box.addView(o);
        }
        if (starter != null && !starter.trim().isEmpty()) {
            TextView s = new TextView(service);
            s.setText("Puedes decir: " + starter);
            s.setTextSize(13f);
            s.setTextColor(Color.rgb(70, 82, 105));
            s.setPadding(0, 6, 0, 12);
            box.addView(s);
        }

        Button back = new Button(service);
        back.setText("Volver a Gemini");
        back.setOnClickListener(v -> listener.onReturnToGemini());
        box.addView(back);

        Button close = new Button(service);
        close.setText("Cerrar sesión");
        close.setOnClickListener(v -> listener.onCloseRequested());
        box.addView(close);

        panel = box;
        WindowManager.LayoutParams lp = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.x = 90;
        wm.addView(panel, lp);
    }

    private void hidePanel() {
        if (panel != null) {
            wm.removeView(panel);
            panel = null;
        }
    }

    private static WindowManager.LayoutParams baseParams(int width, int height) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        return lp;
    }
}
