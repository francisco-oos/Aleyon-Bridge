package com.aleyon.geminibridge.automation;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

/**
 * Semantic Gemini UI adapter.
 *
 * IMPORTANT: No screen coordinates or gesture injection calls live here.
 * Selectors are based on accessible text/content-description/class/role and
 * exact profile names.  This makes minor layout changes survivable and makes
 * selector failures explicit instead of silently clicking arbitrary pixels.
 */
public final class GeminiUi {
    private GeminiUi() {}

    public static AccessibilityNodeInfo findAny(
            AccessibilityNodeInfo root, String... candidates) {
        if (root == null) return null;
        List<String> wanted = normalize(candidates);
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String text = nodeText(n);
            String desc = n.getContentDescription() == null
                    ? "" : n.getContentDescription().toString();
            String nt = norm(text);
            String nd = norm(desc);
            for (String w : wanted) {
                if (nt.equals(w) || nd.equals(w)) return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    public static AccessibilityNodeInfo findContains(
            AccessibilityNodeInfo root, String... fragments) {
        if (root == null) return null;
        List<String> wanted = normalize(fragments);
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String nt = norm(nodeText(n));
            String nd = norm(n.getContentDescription() == null
                    ? "" : n.getContentDescription().toString());
            for (String w : wanted) {
                if ((!w.isEmpty() && nt.contains(w)) || (!w.isEmpty() && nd.contains(w))) {
                    return n;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    public static boolean clickAny(AccessibilityNodeInfo root, String... candidates) {
        AccessibilityNodeInfo node = findAny(root, candidates);
        if (node == null) node = findContains(root, candidates);
        return node != null && clickNodeOrClickableParent(node);
    }

    public static boolean clickExact(AccessibilityNodeInfo root, String exactText) {
        AccessibilityNodeInfo node = findAny(root, exactText);
        return node != null && clickNodeOrClickableParent(node);
    }

    public static boolean setFirstEditable(AccessibilityNodeInfo root, String text) {
        AccessibilityNodeInfo node = firstEditable(root);
        return node != null && setText(node, text);
    }

    public static boolean setLargestEditable(AccessibilityNodeInfo root, String text) {
        if (root == null) return false;
        AccessibilityNodeInfo best = null;
        int bestScore = -1;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isEditable()) {
                int score = 0;
                CharSequence hint = n.getHintText();
                if (hint != null) score += hint.length();
                CharSequence current = n.getText();
                if (current != null) score += current.length();
                if (n.isMultiLine()) score += 1000;
                if (score > bestScore) {
                    best = n;
                    bestScore = score;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return best != null && setText(best, text);
    }

    public static AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isEditable()) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    public static boolean sendMessage(AccessibilityNodeInfo root, String text) {
        AccessibilityNodeInfo editable = firstEditable(root);
        if (editable == null || !setText(editable, text)) return false;
        // Gemini has used both localized text and content descriptions.
        return clickAny(root, "Enviar", "Send", "Enviar mensaje", "Send message");
    }

    public static boolean ensureToggleOn(
            AccessibilityNodeInfo root, String labelSpanish, String labelEnglish) {
        AccessibilityNodeInfo label = findAny(root, labelSpanish, labelEnglish);
        if (label == null) label = findContains(root, labelSpanish, labelEnglish);
        if (label == null) return false;

        AccessibilityNodeInfo cursor = label;
        for (int depth = 0; depth < 4 && cursor != null; depth++) {
            AccessibilityNodeInfo checkable = findCheckableDescendant(cursor);
            if (checkable != null) {
                if (checkable.isChecked()) return true;
                return clickNodeOrClickableParent(checkable);
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    public static boolean hasAny(AccessibilityNodeInfo root, String... candidates) {
        return findAny(root, candidates) != null || findContains(root, candidates) != null;
    }

    public static String collectAllText(AccessibilityNodeInfo root) {
        if (root == null) return "";
        StringBuilder b = new StringBuilder();
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String t = nodeText(n);
            if (!t.trim().isEmpty()) b.append(t).append('\n');
            CharSequence d = n.getContentDescription();
            if (d != null && !d.toString().trim().isEmpty()) b.append(d).append('\n');
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return b.toString();
    }

    public static boolean isLiveScreen(AccessibilityNodeInfo root) {
        if (root == null) return false;
        String all = norm(collectAllText(root));
        // We require more than the word "Live" to avoid false positives from a
        // button that merely launches Live.
        boolean live = all.contains("live");
        boolean controls = all.contains("microfono") || all.contains("microphone")
                || all.contains("pausar") || all.contains("pause")
                || all.contains("finalizar") || all.contains("end");
        return live && controls;
    }


    /** True only when the currently active accessibility root belongs to Gemini. */
    public static boolean isGeminiRoot(AccessibilityNodeInfo root) {
        return root != null && root.getPackageName() != null
                && "com.google.android.apps.bard".contentEquals(root.getPackageName());
    }

    /** Cheap node count for diagnostics; not used for any control-flow decision. */
    public static int countNodes(AccessibilityNodeInfo root) {
        if (root == null) return 0;
        int count = 0;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            count++;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return count;
    }

    /**
     * Semantic scrolling without coordinates or gesture injection. The first
     * scrollable container that accepts ACTION_SCROLL_FORWARD is used.
     */
    public static boolean scrollForward(AccessibilityNodeInfo root) {
        return scroll(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public static boolean scrollBackward(AccessibilityNodeInfo root) {
        return scroll(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    private static boolean scroll(AccessibilityNodeInfo root, int action) {
        if (root == null) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isScrollable() && n.performAction(action)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /** Returns the current text of the most likely large instructions field. */
    public static String largestEditableText(AccessibilityNodeInfo root) {
        if (root == null) return "";
        AccessibilityNodeInfo best = null;
        int bestScore = -1;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isEditable()) {
                int score = n.isMultiLine() ? 1000 : 0;
                CharSequence hint = n.getHintText();
                CharSequence current = n.getText();
                if (hint != null) score += hint.length();
                if (current != null) score += current.length();
                if (score > bestScore) { best = n; bestScore = score; }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        if (best == null || best.getText() == null) return "";
        return best.getText().toString();
    }

    private static boolean setText(AccessibilityNodeInfo node, String text) {
        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private static boolean clickNodeOrClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int depth = 0; depth < 5 && n != null; depth++) {
            if (n.isClickable() && n.isEnabled()) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            n = n.getParent();
        }
        return false;
    }

    private static AccessibilityNodeInfo findCheckableDescendant(AccessibilityNodeInfo root) {
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isCheckable()) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    private static String nodeText(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        return t == null ? "" : t.toString();
    }

    private static List<String> normalize(String... values) {
        List<String> out = new ArrayList<>();
        Arrays.stream(values).forEach(v -> out.add(norm(v)));
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
