package com.aleyon.geminibridge.core;

/**
 * Pure geometry policy used to reject Accessibility nodes that only exist in
 * Gemini's retained/off-screen trees.  It is deliberately Android-free so the
 * exact field regression can be unit-tested with a plain JDK.
 */
public final class ScreenBoundsPolicy {
    private ScreenBoundsPolicy() {}

    public static boolean isActionableRect(
            int left, int top, int right, int bottom,
            int viewportLeft, int viewportTop, int viewportRight, int viewportBottom) {
        if (right <= left || bottom <= top) return false;
        if (viewportRight <= viewportLeft || viewportBottom <= viewportTop) {
            // Conservative fallback used by GeminiUi when an OEM reports an
            // empty viewport: the node still has to occupy the physical
            // non-negative screen quadrant.
            return right > 0 && bottom > 0 && left >= 0 && top >= 0;
        }
        return right > viewportLeft && left < viewportRight
                && bottom > viewportTop && top < viewportBottom;
    }
}
