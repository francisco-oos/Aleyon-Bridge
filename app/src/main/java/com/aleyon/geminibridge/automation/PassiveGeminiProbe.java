package com.aleyon.geminibridge.automation;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Read-only field instrumentation for Gemini UI research.
 *
 * IMPORTANT: this class intentionally contains no node-action calls, no
 * no gesture dispatch, no clipboard writes, no scrolling, and no text input.
 * Its only job is to serialize what Android Accessibility already exposes so
 * selector design can be based on evidence from the real device instead of
 * assumptions about a screenshot or a particular Gemini build.
 */
public final class PassiveGeminiProbe {
    private static final int MAX_NODES_PER_ROOT = 260;
    private static final int MAX_TEXT_CHARS = 160;
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

    private PassiveGeminiProbe() {}

    public static JSONObject capture(AccessibilityService service, int sampleIndex) {
        JSONObject out = new JSONObject();
        try {
            out.put("sampleIndex", sampleIndex);
            out.put("timestamp", System.currentTimeMillis());
            out.put("privacyRedacted", true);
            out.put("rawTextIncluded", false);
            out.put("conversationTextRedacted", true);
            out.put("nonGeminiWindowsOmitted", true);
            AccessibilityNodeInfo active = null;
            try { active = service.getRootInActiveWindow(); } catch (Exception ignored) {}
            if (GeminiUi.isVerifiedGeminiSurface(active)) {
                out.put("activeRoot", rootSummary(active, true));
            } else {
                JSONObject omitted = new JSONObject();
                omitted.put("available", false);
                omitted.put("omittedNonGemini", active != null);
                out.put("activeRoot", omitted);
            }

            JSONArray windowsJson = new JSONArray();
            List<AccessibilityWindowInfo> windows = null;
            try { windows = service.getWindows(); } catch (Exception ignored) {}
            if (windows != null) {
                for (int i = 0; i < windows.size(); i++) {
                    AccessibilityWindowInfo w = windows.get(i);
                    if (w == null) continue;
                    if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo root = null;
                    try { root = w.getRoot(); } catch (Exception ignored) {}
                    if (!GeminiUi.isVerifiedGeminiSurface(root)) continue;
                    JSONObject x = new JSONObject();
                    x.put("index", i);
                    x.put("type", w.getType());
                    x.put("active", w.isActive());
                    x.put("focused", w.isFocused());
                    try {
                        CharSequence title = w.getTitle();
                        x.put("title", clean(title));
                    } catch (Exception ignored) {
                        x.put("title", "");
                    }
                    x.put("root", rootSummary(root, true));
                    windowsJson.put(x);
                }
            }
            out.put("windowCount", windowsJson.length());
            out.put("windows", windowsJson);
        } catch (Exception e) {
            try {
                out.put("probeError", e.getClass().getSimpleName());
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static JSONObject rootSummary(AccessibilityNodeInfo root, boolean includeNodes) {
        JSONObject out = new JSONObject();
        try {
            if (root == null) {
                out.put("available", false);
                return out;
            }
            out.put("available", true);
            out.put("package", clean(root.getPackageName()));
            out.put("class", clean(root.getClassName()));
            // Compact capability summary so field diagnostics remain useful
            // even when the full node dump is large.
            out.put("geminiVerified", GeminiUi.isVerifiedGeminiSurface(root));
            out.put("robinResourceSignature", GeminiUi.hasRobinResourceSignature(root));
            out.put("blockingConsentDialog", GeminiUi.isBlockingConsentDialog(root));
            out.put("navigationDrawerVisible", GeminiUi.isNavigationDrawerOpen(root));
            out.put("surface", surface(root));
            out.put("chatComposerDetected", GeminiUi.chatComposer(root) != null);
            out.put("liveButtonDetected", GeminiUi.hasVisibleGeminiLiveLauncher(root));
            out.put("liveButtonEvidence", GeminiUi.liveLauncherEvidence(root));
            out.put("composerRightActionCount", GeminiUi.visibleComposerRightActionCount(root));
            out.put("responseInProgress", GeminiUi.isResponseInProgress(root));
            out.put("editableNodeCount", countEditable(root));
            if (includeNodes) {
                JSONArray nodes = dumpNodes(root);
                out.put("nodeCountCaptured", nodes.length());
                out.put("nodesTruncated", nodes.length() >= MAX_NODES_PER_ROOT);
                out.put("nodes", nodes);
            }
        } catch (Exception e) {
            try { out.put("summaryError", e.getClass().getSimpleName()); }
            catch (Exception ignored) {}
        }
        return out;
    }

    private static JSONArray dumpNodes(AccessibilityNodeInfo root) {
        JSONArray out = new JSONArray();
        Queue<NodeDepth> q = new ArrayDeque<>();
        q.add(new NodeDepth(root, 0, -1));
        int ordinal = 0;
        while (!q.isEmpty() && ordinal < MAX_NODES_PER_ROOT) {
            NodeDepth item = q.remove();
            AccessibilityNodeInfo n = item.node;
            int myIndex = ordinal++;
            JSONObject x = new JSONObject();
            try {
                x.put("i", myIndex);
                x.put("parent", item.parentIndex);
                x.put("depth", item.depth);
                x.put("package", clean(n.getPackageName()));
                x.put("class", clean(n.getClassName()));
                x.put("viewId", clean(n.getViewIdResourceName()));
                x.put("text", safeNodeText(n, n.getText(), false));
                x.put("desc", safeNodeText(n, n.getContentDescription(), true));
                x.put("hint", safeNodeText(n, n.getHintText(), false));
                x.put("clickable", n.isClickable());
                x.put("editable", n.isEditable());
                x.put("scrollable", n.isScrollable());
                x.put("checkable", n.isCheckable());
                x.put("checked", n.isChecked());
                x.put("enabled", n.isEnabled());
                try { x.put("visibleToUser", n.isVisibleToUser()); }
                catch (Exception ignored) { x.put("visibleToUser", false); }
                x.put("actionablyVisible", GeminiUi.isActionablyVisible(n, root));
                x.put("actions", n.getActions());
                Rect r = new Rect();
                try {
                    n.getBoundsInScreen(r);
                    x.put("bounds", r.left + "," + r.top + "," + r.right + "," + r.bottom);
                } catch (Exception ignored) {
                    x.put("bounds", "");
                }
            } catch (Exception ignored) {
                // Preserve the traversal even if one vendor node throws.
            }
            out.put(x);
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try { child = n.getChild(i); } catch (Exception ignored) {}
                if (child != null) q.add(new NodeDepth(child, item.depth + 1, myIndex));
            }
        }
        return out;
    }

    private static int countEditable(AccessibilityNodeInfo root) {
        if (root == null) return 0;
        int count = 0;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isEditable() && GeminiUi.isActionablyVisible(n, root)) count++;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try { child = n.getChild(i); } catch (Exception ignored) {}
                if (child != null) q.add(child);
            }
        }
        return count;
    }

    private static String clean(CharSequence value) {
        if (value == null) return "";
        String s = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        s = EMAIL.matcher(s).replaceAll("[REDACTED_EMAIL]");
        if (s.length() > MAX_TEXT_CHARS) return s.substring(0, MAX_TEXT_CHARS) + "…";
        return s;
    }

    private static String safeNodeText(
            AccessibilityNodeInfo node, CharSequence value, boolean description) {
        if (value == null) return "";
        String id = "";
        try {
            String rawId = node == null ? null : node.getViewIdResourceName();
            if (rawId != null) id = rawId;
        } catch (Exception ignored) {}

        if (id.contains("assistant_robin_user_message_text")
                || id.contains("assistant_robin_text")
                || id.contains("assistant_conv_mode_visual_output_captions_text")
                || id.contains("assistant_robin_project_zero_state_recent_chat_text")) {
            return description ? "[REDACTED_MESSAGE_DESC]" : "[REDACTED_MESSAGE_TEXT]";
        }
        if (id.toLowerCase().contains("account")
                || id.toLowerCase().contains("avatar")
                || id.toLowerCase().contains("profile_picture")) {
            return "[REDACTED_ACCOUNT]";
        }
        return clean(value);
    }

    private static String surface(AccessibilityNodeInfo root) {
        if (root == null) return "UNAVAILABLE";
        if (GeminiUi.isBlockingConsentDialog(root)) return "CONSENT_DIALOG";
        if (GeminiUi.isConversationSearchOpen(root)) return "CONVERSATION_SEARCH";
        if (GeminiUi.isNavigationDrawerOpen(root)) return "DRAWER";
        if (GeminiUi.isLiveScreen(root)) return "LIVE";
        if (GeminiUi.chatComposer(root) != null) return "CHAT";
        return "UNKNOWN";
    }

    private static final class NodeDepth {
        final AccessibilityNodeInfo node;
        final int depth;
        final int parentIndex;
        NodeDepth(AccessibilityNodeInfo node, int depth, int parentIndex) {
            this.node = node;
            this.depth = depth;
            this.parentIndex = parentIndex;
        }
    }
}
