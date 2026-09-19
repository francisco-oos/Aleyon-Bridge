package com.aleyon.geminibridge.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aleyon.geminibridge.R;

/**
 * Lightweight floating control shown only while Aleyon is running a
 * session/automation on Gemini. TYPE_ACCESSIBILITY_OVERLAY does not require
 * the broad SYSTEM_ALERT_WINDOW permission; it is tied to the
 * explicitly-enabled accessibility service.
 *
 * Deliberately static: no permanent animation, no polling. The bubble only
 * repaints in response to an explicit call from the service (a stage
 * transition or the user opening the panel), and the panel only reads
 * current status when it is opened - never on a timer.
 */
public final class OverlayController {
    /** A snapshot of what the panel should show, read fresh each time it opens. */
    public static final class Status {
        public final String stageLabel;
        public final String detail;
        public final boolean recoveryPending;

        public Status(String stageLabel, String detail, boolean recoveryPending) {
            this.stageLabel = stageLabel == null ? "" : stageLabel;
            this.detail = detail == null ? "" : detail;
            this.recoveryPending = recoveryPending;
        }
    }

    public interface Listener {
        void onReturnToGemini();
        void onCloseRequested();
        void onRecoverRequested();
        /** Read fresh from the journal every time the panel opens - never cached. */
        Status currentStatus();
    }

    private static final int NORMAL_COLOR = Color.rgb(38, 90, 197);
    private static final int ATTENTION_COLOR = Color.rgb(191, 111, 22);
    private static final int DRAG_SLOP_PX = 18;
    private static final int BUBBLE_DP = 54;
    private static final int EDGE_MARGIN_DP = 10;

    private final AccessibilityService service;
    private final WindowManager wm;
    private final Listener listener;
    private View bubble;
    private ImageView bubbleIcon;
    private ProgressBar bubbleSpinner;
    private GradientDrawable bubbleBackground;
    private WindowManager.LayoutParams bubbleParams;
    private View panel;
    private View notice;
    private View workingCaption;
    private TextView workingCaptionText;
    private WindowManager.LayoutParams workingCaptionParams;
    private String label = "";
    private String objective = "";
    private String starter = "";
    private boolean needsAttention = false;
    private boolean working = false;
    private String workingPhaseLabel = "";

    public OverlayController(AccessibilityService service, Listener listener) {
        this.service = service;
        this.listener = listener;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(String profileLabel, String objectiveToday, String starterPhrase) {
        label = profileLabel == null ? "" : profileLabel;
        objective = objectiveToday == null ? "" : objectiveToday;
        starter = starterPhrase == null ? "" : starterPhrase;
        needsAttention = false;
        // show() is the entry point for "session is actually ready to use"
        // done its job by now - the user should see Gemini/Live itself.
        ensureBubbleCreated();
        if (bubbleBackground != null) bubbleBackground.setColor(NORMAL_COLOR);
        setWorking(false, "");
    }

    /**
     * Lightweight "still working" indicator (2026-09-06, requested by the
     * user after a SETUP run with no visible feedback left them unsure
     * whether to touch the phone). Shown for the whole duration of any
     * automation - not just once Live is active - so the bubble is always
     * present as soon as Aleyon starts doing anything on Gemini. Reuses the
     * same bubble instance/position as show(); only the small indeterminate
     * spinner in place of the icon changes. A system ProgressBar is a
     * platform-drawn, hardware-accelerated spinner - not a custom timer
     * loop reimplemented here - so this does not reintroduce the polling/
     * animation cost this class otherwise avoids.
     *
     * phaseLabel is a short caption (e.g. "Preparando apertura...") shown
     * next to the spinner so a multi-minute run never reads as stuck or
     * looped (2026-09-06, user report) - the caller derives it from which
     * automation Mode is running, never hardcoded per-profile here.
     */
    public void showWorking(String profileLabel, String phaseLabel) {
        label = profileLabel == null ? "" : profileLabel;
        ensureBubbleCreated();
        if (!needsAttention && bubbleBackground != null) bubbleBackground.setColor(NORMAL_COLOR);
        setWorking(true, phaseLabel == null ? "" : phaseLabel);
    }

    private void ensureBubbleCreated() {
        if (bubble != null) return;

        FrameLayout container = new FrameLayout(service);
        bubbleBackground = new GradientDrawable();
        bubbleBackground.setColor(NORMAL_COLOR);
        bubbleBackground.setShape(GradientDrawable.OVAL);
        container.setBackground(bubbleBackground);

        // Mascot icon (2026-09-06, user request) in place of the plain "A"
        // letter - reuses the same PNG already shipped as the launcher icon,
        // so no new asset is introduced.
        ImageView icon = new ImageView(service);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        iconLp.gravity = Gravity.CENTER;
        container.addView(icon, iconLp);
        bubbleIcon = icon;

        ProgressBar spinner = new ProgressBar(service);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams spinnerLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        spinnerLp.gravity = Gravity.CENTER;
        container.addView(spinner, spinnerLp);
        bubbleSpinner = spinner;

        bubble = container;
        // Use density-independent sizing so the same bubble feels consistent
        // on the Nubia and Samsung instead of 82 raw pixels meaning a different
        // physical size on each panel.
        bubbleParams = baseParams(dp(BUBBLE_DP), dp(BUBBLE_DP));
        bubbleParams.gravity = Gravity.TOP | Gravity.END;
        bubbleParams.x = dp(EDGE_MARGIN_DP);
        bubbleParams.y = dp(96);
        container.setOnTouchListener(this::onBubbleTouch);
        wm.addView(bubble, bubbleParams);
    }

    private void setWorking(boolean w, String phaseLabel) {
        working = w;
        workingPhaseLabel = w && phaseLabel != null ? phaseLabel.trim() : "";
        if (bubbleIcon != null) bubbleIcon.setVisibility(w ? View.GONE : View.VISIBLE);
        if (bubbleSpinner != null) bubbleSpinner.setVisibility(w ? View.VISIBLE : View.GONE);
        if (w && !workingPhaseLabel.isEmpty() && panel == null) {
            showWorkingCaption(workingPhaseLabel);
        } else {
            hideWorkingCaption();
        }
    }

    /**
     * Small non-interactive caption anchored below the bubble, visible only
     * while working=true. Recreated on every call (cheap - a couple of
     * TextViews) rather than mutated in place, matching the rest of this
     * class's "no persistent animation/timer" approach: it only changes in
     * response to an explicit showWorking() call, never on its own.
     */
    private void showWorkingCaption(String phaseLabel) {
        hideWorkingCaption();
        if (bubbleParams == null) return;

        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 10, 20, 10);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(230, 38, 90, 197));
        bg.setCornerRadius(18);
        box.setBackground(bg);

        TextView text = new TextView(service);
        text.setText(phaseLabel);
        text.setTextSize(12f);
        text.setTextColor(Color.WHITE);
        box.addView(text);
        workingCaptionText = text;
        workingCaption = box;

        WindowManager.LayoutParams lp = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        boolean left = isBubbleOnLeft();
        lp.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        lp.x = dp(EDGE_MARGIN_DP + BUBBLE_DP + 6);
        lp.y = bubbleParams.y + dp(8);
        workingCaptionParams = lp;
        wm.addView(workingCaption, lp);
    }

    private void hideWorkingCaption() {
        if (workingCaption != null) {
            wm.removeView(workingCaption);
            workingCaption = null;
            workingCaptionText = null;
            workingCaptionParams = null;
        }
    }

    /**
     * Drag-to-move: a touch that moves more than a small slop is treated as
     * a drag (updates the overlay position live) instead of a tap; a touch
     * that stays within the slop opens/closes the panel on ACTION_UP. No
     * animation, no velocity/fling handling - just direct 1:1 following,
     * which is cheap and keeps the bubble out of the way with one gesture.
     */
    private boolean onBubbleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = bubbleParams.x;
                dragStartY = bubbleParams.y;
                dragged = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float dx = event.getRawX() - dragStartRawX;
                float dy = event.getRawY() - dragStartRawY;
                if (!dragged && (Math.abs(dx) > DRAG_SLOP_PX || Math.abs(dy) > DRAG_SLOP_PX)) {
                    dragged = true;
                    // BUG (2026-09-06, reported by user): showPanel() sets
                    // the panel's position once, from the bubble's position
                    // at that exact moment - it is a separate overlay
                    // window, not a child view of the bubble, so it never
                    // hears about later bubble moves. Dragging the bubble
                    // while the panel was open left the panel visually
                    // stranded at the old position, disconnected from the
                    // bubble. Close it the moment a drag starts, matching
                    // the usual floating-bubble pattern (e.g. chat heads):
                    // a panel makes sense anchored to a resting bubble, not
                    // one currently being moved.
                    hidePanel();
                }
                if (dragged) {
                    // Bubble is gravity END/TOP: x grows leftward from the
                    // right edge, y grows downward from the top - invert dx.
                    bubbleParams.x = Math.max(0, (int) (dragStartX - dx));
                    bubbleParams.y = Math.max(0, (int) (dragStartY + dy));
                    wm.updateViewLayout(bubble, bubbleParams);
                    // Same class of bug as the panel above: keep the
                    // working-caption anchored to the bubble instead of
                    // letting it strand behind while dragging.
                    repositionWorkingCaption();
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged) snapBubbleToEdge();
                else togglePanel();
                return true;
            }
            default -> { return false; }
        }
    }

    private float dragStartRawX, dragStartRawY;
    private int dragStartX, dragStartY;
    private boolean dragged;

    private int dp(int value) {
        float density = service.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * (density <= 0f ? 1f : density)));
    }

    private int screenWidth() { return service.getResources().getDisplayMetrics().widthPixels; }
    private int screenHeight() { return service.getResources().getDisplayMetrics().heightPixels; }

    private boolean isBubbleOnLeft() {
        if (bubbleParams == null) return false;
        int size = dp(BUBBLE_DP);
        int left = screenWidth() - size - bubbleParams.x; // END gravity
        return left + size / 2 < screenWidth() / 2;
    }

    private void snapBubbleToEdge() {
        if (bubble == null || bubbleParams == null) return;
        int size = dp(BUBBLE_DP), margin = dp(EDGE_MARGIN_DP);
        int left = screenWidth() - size - bubbleParams.x;
        boolean toLeft = left + size / 2 < screenWidth() / 2;
        bubbleParams.x = toLeft ? Math.max(margin, screenWidth() - size - margin) : margin;
        bubbleParams.y = Math.max(margin, Math.min(bubbleParams.y, Math.max(margin, screenHeight() - size - margin)));
        wm.updateViewLayout(bubble, bubbleParams);
        repositionWorkingCaption();
    }

    private void repositionWorkingCaption() {
        if (workingCaption == null || workingCaptionParams == null || bubbleParams == null) return;
        boolean left = isBubbleOnLeft();
        workingCaptionParams.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        workingCaptionParams.x = dp(EDGE_MARGIN_DP + BUBBLE_DP + 6);
        workingCaptionParams.y = bubbleParams.y + dp(8);
        wm.updateViewLayout(workingCaption, workingCaptionParams);
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

    /**
     * Flips the bubble into a visibly different (not animated) state so a
     * failure mid-Live/Close never disappears silently - the journal still
     * holds enough state (recoverableStage/error) to continue, and this is
     * the only signal the user needs before opening the panel.
     */
    public void markNeedsAttention() {
        needsAttention = true;
        if (bubbleBackground != null) bubbleBackground.setColor(ATTENTION_COLOR);
        // A failure means Aleyon is now waiting on the user, not actively
        // working - a spinner stuck on screen would misreport that.
        setWorking(false, "");
        // A failure needs the user to actually SEE Gemini's real screen to
    }

    public void hide() {
        hideNotice();
        hidePanel();
        hideWorkingCaption();
        if (bubble != null) {
            wm.removeView(bubble);
            bubble = null;
            bubbleIcon = null;
            bubbleSpinner = null;
            bubbleBackground = null;
            bubbleParams = null;
            working = false;
        }
    }

    /**
     * Non-interactive banner used when Gemini itself requires a human decision
     * (for example, a Connected Apps consent dialog). Aleyon deliberately does
     * not accept legal/permission choices on the user's behalf.
     */
    public void showNotice(String message) {
        hideNotice();
        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 18, 24, 18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(245, 255, 255, 255));
        bg.setCornerRadius(24);
        bg.setStroke(2, Color.rgb(218, 225, 237));
        box.setBackground(bg);

        TextView text = new TextView(service);
        text.setText(message == null ? "" : message);
        text.setTextSize(14f);
        text.setTextColor(Color.rgb(23, 32, 51));
        box.addView(text);
        notice = box;

        WindowManager.LayoutParams lp = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.x = 90;
        wm.addView(notice, lp);
    }

    public void hideNotice() {
        if (notice != null) {
            wm.removeView(notice);
            notice = null;
        }
    }

    private void togglePanel() {
        if (panel == null) showPanel();
        else hidePanel();
    }

    private void showPanel() {
        hideWorkingCaption();
        // Status is pulled fresh right now, not cached from show()/session
        // start - the whole point (D-047) is that the journal can move on
        // (reconciliation, a later fail()) after the bubble first appeared.
        Status status = listener.currentStatus();

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

        if (status != null && !status.stageLabel.isEmpty()) {
            TextView stageLine = new TextView(service);
            stageLine.setText("Estado: " + status.stageLabel);
            stageLine.setTextSize(13f);
            stageLine.setTextColor(status.recoveryPending
                    ? ATTENTION_COLOR : Color.rgb(70, 82, 105));
            stageLine.setPadding(0, 10, 0, 0);
            box.addView(stageLine);
            if (!status.detail.isEmpty()) {
                TextView detailLine = new TextView(service);
                detailLine.setText(status.detail);
                detailLine.setTextSize(12f);
                detailLine.setTextColor(Color.rgb(70, 82, 105));
                detailLine.setPadding(0, 2, 0, 4);
                box.addView(detailLine);
            }
        }

        if (!objective.trim().isEmpty()) {
            TextView o = new TextView(service);
            o.setText("Objetivo: " + objective);
            o.setTextSize(13f);
            o.setTextColor(Color.rgb(70, 82, 105));
            o.setPadding(0, 12, 0, 4);
            box.addView(o);
        }
        if (!starter.trim().isEmpty()) {
            TextView s = new TextView(service);
            s.setText("Puedes decir: " + starter);
            s.setTextSize(13f);
            s.setTextColor(Color.rgb(70, 82, 105));
            s.setPadding(0, 6, 0, 12);
            box.addView(s);
        }

        Button back = new Button(service);
        back.setAllCaps(false);
        back.setText("Volver a Gemini");
        back.setOnClickListener(v -> { hidePanel(); listener.onReturnToGemini(); });
        box.addView(back);

        Button close = new Button(service);
        close.setAllCaps(false);
        boolean active = status != null && ("En Live".equals(status.stageLabel) || "En chat".equals(status.stageLabel)
                || "Cerrando".equals(status.stageLabel) || "Guardando".equals(status.stageLabel)
                || "Resumiendo".equals(status.stageLabel));
        close.setText(active ? "Cerrar sesión" : "Cancelar preparación");
        close.setOnClickListener(v -> { hidePanel(); listener.onCloseRequested(); });
        box.addView(close);

        if (status != null && status.recoveryPending) {
            Button recover = new Button(service);
            recover.setAllCaps(false);
            recover.setText("Recuperar");
            recover.setOnClickListener(v -> {
                hidePanel();
                listener.onRecoverRequested();
            });
            box.addView(recover);
        }

        panel = box;
        box.setOnTouchListener((v,event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { hidePanel(); return true; }
            return false;
        });
        WindowManager.LayoutParams lp = baseParams(dp(286), WindowManager.LayoutParams.WRAP_CONTENT);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        boolean left = isBubbleOnLeft();
        lp.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        lp.x = dp(EDGE_MARGIN_DP);
        lp.y = bubbleParams != null ? bubbleParams.y : dp(96);
        wm.addView(panel, lp);
    }

    private void hidePanel() {
        if (panel != null) {
            wm.removeView(panel);
            panel = null;
        }
        if (working && !workingPhaseLabel.isEmpty() && workingCaption == null && bubble != null)
            showWorkingCaption(workingPhaseLabel);
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
