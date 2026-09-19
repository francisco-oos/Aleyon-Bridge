package com.aleyon.geminibridge.automation;

import android.os.Bundle;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.aleyon.geminibridge.core.ScreenBoundsPolicy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

/**
 * Semantic Gemini UI adapter.
 *
 * IMPORTANT: No screen coordinates or dispatchGesture calls live here.
 * Selectors are based on accessible text/content-description/class/role and
 * exact profile names.  This makes minor layout changes survivable and makes
 * selector failures explicit instead of silently clicking arbitrary pixels.
 */
public final class GeminiUi {
    public static final String GEMINI_APP_PACKAGE = "com.google.android.apps.bard";
    public static final String GEMINI_GOOGLE_HOST_PACKAGE = "com.google.android.googlequicksearchbox";

    private GeminiUi() {}

    public static AccessibilityNodeInfo findAny(
            AccessibilityNodeInfo root, String... candidates) {
        if (root == null) return null;
        List<String> wanted = normalize(candidates);
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            // FIELD BUG (2026-09-06, real device): an EditText showing
            // exactly the text we just typed into it (e.g. Gemini's search
            // exact-matches its own query as a false "result" - findAny
            // then returned the search box itself, and callers built on it
            // (clickExact, hasExact...) treated "I found and clicked the
            // A live-editable node can never legitimately BE the semantic
            // control/result callers are searching for, so it must never
            // participate in a text/description match here.
            if (isActionablyVisible(n, root) && !n.isEditable()) {
                String text = nodeText(n);
                String desc = n.getContentDescription() == null
                        ? "" : n.getContentDescription().toString();
                String nt = norm(text);
                String nd = norm(desc);
                for (String w : wanted) {
                    if (nt.equals(w) || nd.equals(w)) return n;
                }
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
            // Same false-positive class as findAny: a live-editable node's
            // own typed content must never satisfy a contains() match either.
            if (isActionablyVisible(n, root) && !n.isEditable()) {
                String nt = norm(nodeText(n));
                String nd = norm(n.getContentDescription() == null
                        ? "" : n.getContentDescription().toString());
                for (String w : wanted) {
                    if ((!w.isEmpty() && nt.contains(w)) || (!w.isEmpty() && nd.contains(w))) {
                        return n;
                    }
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

    /** Exact-only variant for controls whose label can also appear inside user text. */
    public static boolean clickAnyExact(AccessibilityNodeInfo root, String... candidates) {
        AccessibilityNodeInfo node = findAny(root, candidates);
        return node != null && clickNodeOrClickableParent(node);
    }

    /** Exact-only visibility check. Avoids contains() false positives in editable text. */
    public static boolean hasExact(AccessibilityNodeInfo root, String... candidates) {
        return findAny(root, candidates) != null;
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
            if (n.isEditable() && isActionablyVisible(n, root)) {
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
            if (n.isEditable() && isActionablyVisible(n, root)) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo uniqueEditableDescendant(
            AccessibilityNodeInfo container, AccessibilityNodeInfo root) {
        if (container == null || root == null) return null;
        AccessibilityNodeInfo only = null;
        int count = 0;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(container);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isEditable() && isActionablyVisible(n, root)) {
                only = n;
                if (++count > 1) return null;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return count == 1 ? only : null;
    }

    /**
     * Strong evidence for the normal chat composer.
     *
     * Resource ids are treated as optional evidence inside the provider
     * adapter only; callers never depend on them. A changed build may still
     * resolve through a verified chat-input container. We deliberately do not
     * fall back to an arbitrary EditText.
     */
    private static AccessibilityNodeInfo explicitChatComposer(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo byId = findByViewIdSuffix(root,
                "assistant_robin_input_collapsed_text_half_sheet");
        if (byId != null && byId.isEditable() && isActionablyVisible(byId, root)) return byId;

        String[] containers = {
                "assistant_chat_add_reply_container",
                "assistant_mode_convergence_chat_input_layout",
                "assistant_chat_add_reply_wrapper"
        };
        for (String suffix : containers) {
            AccessibilityNodeInfo container = findByViewIdSuffix(root, suffix);
            AccessibilityNodeInfo editable = uniqueEditableDescendant(container, root);
            if (editable != null) return editable;
        }
        return null;
    }

    private static boolean subtreeLooksLikeSearch(AccessibilityNodeInfo root) {
        if (root == null) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < 24) {
            AccessibilityNodeInfo n = q.remove();
            String sig = norm(nodeText(n) + " "
                    + (n.getContentDescription() == null ? "" : n.getContentDescription().toString()) + " "
                    + (n.getHintText() == null ? "" : n.getHintText().toString()));
            if (sig.contains("buscar") || sig.contains("search")) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /**
     * Search-result lists on the observed Compose surface expose selectable
     * rows. Requiring multiple checkable rows avoids confusing a normal
     * scrollable conversation with the search surface.
     */
    private static boolean hasScrollableChoiceList(AccessibilityNodeInfo root) {
        if (root == null) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isScrollable() && isActionablyVisible(n, root)) {
                int choices = 0;
                Queue<AccessibilityNodeInfo> sub = new ArrayDeque<>();
                sub.add(n);
                while (!sub.isEmpty() && choices < 2) {
                    AccessibilityNodeInfo x = sub.remove();
                    if (x != n && x.isCheckable() && isActionablyVisible(x, root)) choices++;
                    for (int i = 0; i < x.getChildCount(); i++) {
                        AccessibilityNodeInfo child = x.getChild(i);
                        if (child != null) sub.add(child);
                    }
                }
                if (choices >= 2) return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /**
     * Conversation search is a first-class semantic surface, not a chat.
     *
     * This intentionally combines independent evidence (search semantics or a
     * selectable result list) and refuses to classify an editable field alone.
     */
    public static boolean isConversationSearchOpen(AccessibilityNodeInfo root) {
        if (root == null || explicitChatComposer(root) != null) return false;
        AccessibilityNodeInfo editable = focusedOrOnlyEditable(root);
        if (editable == null) return false;
        return subtreeLooksLikeSearch(editable) || hasScrollableChoiceList(root);
    }

    /**
     * Returns only a proven normal Gemini chat composer.
     *
     * Never fall back to the first EditText. The previous fallback is exactly
     * what made Gemini's "Buscar chats" field masquerade as the message
     * composer in the field probe.
     */
    public static AccessibilityNodeInfo chatComposer(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo explicit = explicitChatComposer(root);
        if (explicit != null) return explicit;
        if (isConversationSearchOpen(root) || isNavigationDrawerOpen(root)) return null;

        AccessibilityNodeInfo conversation = findByViewIdSuffix(root,
                "assistant_robin_conversation_container");
        if (conversation != null && isActionablyVisible(conversation, root)) {
            return uniqueEditableDescendant(conversation, root);
        }
        return null;
    }

    /** Structural evidence of a normal conversation even when controls are off-screen. */
    public static boolean hasConversationViewport(AccessibilityNodeInfo root) {
        if (root == null) return false;
        return findByViewIdSuffix(root, "assistant_robin_conversation_container") != null
                || findByViewIdSuffix(root, "assistant_robin_chat_history_list") != null
                || findByViewIdSuffix(root, "assistant_robin_chat_history_sheet") != null;
    }

    /** True only for a clean normal chat with no visible conversation messages yet. */
    public static boolean isBlankNormalChat(AccessibilityNodeInfo root) {
        if (root == null || isTemporaryChat(root) || isConversationSearchOpen(root)
                || isNavigationDrawerOpen(root) || isLiveScreen(root)
                || chatComposer(root) == null) return false;
        return !hasVisibleResourceSuffix(root, "assistant_robin_user_message_container")
                && !hasVisibleResourceSuffix(root, "assistant_robin_content_message_container");
    }

    private static boolean hasVisibleResourceSuffix(AccessibilityNodeInfo root, String suffix) {
        if (root == null || suffix == null || suffix.isEmpty()) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            if (id != null && id.endsWith(suffix) && isActionablyVisible(n, root)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /** True only when the verified normal composer contains exactly this payload. */
    public static boolean composerContainsExactText(AccessibilityNodeInfo root, String text) {
        AccessibilityNodeInfo editable = chatComposer(root);
        if (editable == null) return false;
        CharSequence current = editable.getText();
        return current != null && (text == null ? "" : text).equals(current.toString());
    }

    /** Writes a payload but deliberately does not submit it. */
    public static boolean writeComposer(AccessibilityNodeInfo root, String text) {
        AccessibilityNodeInfo editable = chatComposer(root);
        if (editable == null) return false;
        String wanted = text == null ? "" : text;
        CharSequence current = editable.getText();
        if (current != null && wanted.equals(current.toString())) return true;
        return setText(editable, wanted);
    }

    public static boolean sendMessage(AccessibilityNodeInfo root, String text) {
        if (!composerContainsExactText(root, text)) {
            // ACTION_SET_TEXT rebuilds the Robin action slot. Keep this legacy
            // helper two-phase; callers that need strong postconditions should
            // use writeComposer() + clickSendAction() and re-observe.
            return writeComposer(root, text) && false;
        }
        return clickSendAction(root);
    }

    /**
     * Sends the already-filled composer. Current Gemini builds replace the Live
     * action in the Robin compose slot with the blue Send arrow while text is
     * present. Prefer localized semantics; if those are absent (observed on
     * Nubia), use that field-proven compose action slot only when it no longer
     * identifies itself as Live. No global clickable guessing is allowed.
     */
    public static boolean clickSendAction(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo editable = chatComposer(root);
        if (editable == null) return false;
        CharSequence current = editable.getText();
        if (current == null || current.toString().trim().isEmpty()) return false;

        AccessibilityNodeInfo semantic = findAny(root, "Enviar", "Send", "Enviar mensaje", "Send message");
        if (semantic == null) semantic = findContains(root, "Enviar mensaje", "Send message");
        if (semantic != null && clickNodeOrClickableParent(semantic)) return true;

        AccessibilityNodeInfo slot = findByViewIdSuffix(root,
                "assistant_robin_input_voice_chat_button_compose");
        if (slot != null && isActionablyVisible(slot, root)) {
            /*
             * Field evidence (2026-09-18): on the current Google-host build the
             * compose slot can retain stale Live accessibility semantics after
             * text is inserted even though the rendered control is the blue
             * Send arrow. Because a non-empty verified composer is the
             * precondition here, prefer the known compose action slot and let
             * the caller verify the postcondition (composer emptied + history
             * advanced). We never click this slot from an empty composer.
             */
            if (slot.isClickable() && slot.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            AccessibilityNodeInfo unique = uniqueClickableDescendant(slot);
            if (unique != null && clickNodeOrClickableParent(unique)) return true;
        }

        // Alpha7 field probes: both Samsung zero-state and Nubia established chat
        // share assistant_chat_add_reply_container even though their wrappers differ.
        AccessibilityNodeInfo container = findByViewIdSuffix(root, "assistant_chat_add_reply_container");
        if (container == null) container = findByViewIdSuffix(root, "assistant_mode_convergence_chat_input_layout");
        if (container == null) container = findByViewIdSuffix(root, "assistant_chat_add_reply_wrapper");
        AccessibilityNodeInfo candidate = rightmostSendCandidate(container, root, editable);
        return candidate != null && clickNodeOrClickableParent(candidate);
    }

    private static AccessibilityNodeInfo rightmostSendCandidate(
            AccessibilityNodeInfo container, AccessibilityNodeInfo root, AccessibilityNodeInfo editable) {
        if (container == null || root == null) return null;
        Rect editBounds = new Rect(); if (editable != null) editable.getBoundsInScreen(editBounds);
        AccessibilityNodeInfo best = null; int bestRight = Integer.MIN_VALUE; long bestArea = Long.MAX_VALUE;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>(); q.add(container);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n != container && n != editable && n.isClickable() && n.isEnabled() && isActionablyVisible(n, root)) {
                String sig = norm(nodeText(n) + " " + (n.getContentDescription()==null?"":n.getContentDescription().toString()));
                String id = n.getViewIdResourceName()==null?"":n.getViewIdResourceName().toLowerCase(Locale.ROOT);
                boolean excluded = sig.contains("microfono") || sig.contains("microphone") || sig.equals("+")
                        || sig.contains("adjuntar") || sig.contains("attach") || sig.contains("camara")
                        || sig.contains("camera") || sig.contains("galeria") || sig.contains("gallery")
                        || sig.contains("gemini live") || id.contains("microphone") || id.contains("attachment")
                        || id.contains("camera");
                Rect b = new Rect(); n.getBoundsInScreen(b);
                boolean editEmpty = editBounds.right<=editBounds.left || editBounds.bottom<=editBounds.top;
                boolean bNonEmpty = b.right>b.left && b.bottom>b.top;
                boolean plausiblyRight = editEmpty || ((b.left+b.right)/2) >= ((editBounds.left+editBounds.right)/2);
                if (!excluded && plausiblyRight && bNonEmpty) {
                    long area = (long)(b.right-b.left) * (long)(b.bottom-b.top);
                    if (b.right > bestRight || (b.right == bestRight && area < bestArea)) {best=n;bestRight=b.right;bestArea=area;}
                }
            }
            for (int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}
        }
        return best;
    }

    public static boolean clearComposer(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo editable = chatComposer(root);
        if (editable == null) return true;
        CharSequence current = editable.getText();
        if (current == null || current.length() == 0) return true;
        return setText(editable, "");
    }

    private static boolean subtreeLooksLikeLive(AccessibilityNodeInfo root) {
        if (root == null) return false;
        return findAny(root, "Open Gemini Live", "Abrir Gemini Live", "Gemini Live",
                "Iniciar Live", "Start Live", "Live") != null;
    }

    /**
     * True once Gemini has finished generating its reply to the last
     * message, so the next sendMessage() will land in an idle composer
     * instead of racing an in-flight response.
     *
     * FIELD BUG (2026-09-06, real device): the objective message (case 12)
     * has no structured marker to wait for before the next message (case
     * 13, the adaptive-supplement request) is sent - unlike every other
     * back-to-back sendMessage() in this service, which always waits for a
     * marker in the reply first. Sending case 13's message immediately
     * after case 12 raced Gemini's own response generation.
     *
     * FIELD BUG #2 (2026-09-06, same device, found while verifying the fix
     * above): the first version of this check looked for "Enviar"/"Send"
     * and walked up to the first clickable ancestor, returning its
     * isEnabled(). Live uiautomator dumps showed that control is disabled
     * whenever the compose field is simply EMPTY - its normal resting state
     * after every send - regardless of whether Gemini is still generating.
     * So the check returned false essentially always, not just while
     * generating, and case 13 failed deterministically on every run
     * instead of only when unlucky on timing. The real signal Gemini
     * exposes for "this reply is fully rendered" is the per-message action
     * row (thumbs up/down, "Escuchar"/"Listen" for text-to-speech) it
     * attaches to a reply only once streaming has finished - confirmed
     * present in the dump for a completed reply and used here instead.
     * This is only safe for a single-turn wait like case 12 -> case 13
     * (one prior exchange, so no earlier action row can produce a false
     * positive); a multi-turn caller would need a stronger signal.
     */
    public static boolean isComposerReadyForNextMessage(AccessibilityNodeInfo root) {
        return findAny(root, "Escuchar", "Listen", "Respuesta correcta", "Good response") != null;
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

    /**
     * Reads a labelled switch/checkable state without mutating it.
     * Returns null when the control cannot be identified safely.
     */
    public static Boolean toggleState(
            AccessibilityNodeInfo root, String labelSpanish, String labelEnglish) {
        AccessibilityNodeInfo label = findAny(root, labelSpanish, labelEnglish);
        if (label == null) label = findContains(root, labelSpanish, labelEnglish);
        if (label == null) return null;

        AccessibilityNodeInfo cursor = label;
        for (int depth = 0; depth < 4 && cursor != null; depth++) {
            AccessibilityNodeInfo checkable = findCheckableDescendant(cursor);
            if (checkable != null) return checkable.isChecked();
            cursor = cursor.getParent();
        }
        return null;
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


    public static String collectVisibleText(AccessibilityNodeInfo root) {
        if (root == null) return "";
        StringBuilder b = new StringBuilder();
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (isActionablyVisible(n, root)) {
                String t = nodeText(n);
                if (!t.trim().isEmpty()) b.append(t).append('\n');
                CharSequence d = n.getContentDescription();
                if (d != null && !d.toString().trim().isEmpty()) b.append(d).append('\n');
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return b.toString();
    }

    public static boolean isLiveScreen(AccessibilityNodeInfo root) {
        if (root == null) return false;
        // A normal Gemini chat exposes a visible
        // editable composer and a microphone/Live launcher. That combination
        // must never be mistaken for an active voice session. An active Live
        // surface is therefore proved by visible call controls while the normal
        // editable composer is absent.
        if (chatComposer(root) != null) return false;

        // FIELD EVIDENCE (alpha10 post-Live tree): conversational-mode caption
        // nodes such as assistant_conv_mode_visual_output_captions_* persist in
        // the tree after Live, but with negative/off-screen bounds. During the
        // actual Live UI they are the full-screen caption surface. Because
        // findByViewIdSuffix() already enforces actionable visibility, a visible
        // caption surface + no normal composer is a strong structural Live
        // postcondition without relying on localized copy.
        if (findByViewIdSuffix(root,
                "assistant_conv_mode_visual_output_captions_container") != null
                || findByViewIdSuffix(root,
                "assistant_conv_mode_visual_output_captions_text_large") != null) {
            return true;
        }

        String all = norm(collectVisibleText(root));
        boolean liveWord = all.contains("gemini live") || all.contains(" live ")
                || all.startsWith("live ") || all.endsWith(" live") || all.equals("live");
        int controlSignals = 0;
        if (all.contains("microfono") || all.contains("microphone")) controlSignals++;
        if (all.contains("pausar") || all.contains("pause") || all.contains("reanudar")
                || all.contains("resume")) controlSignals++;
        if (all.contains("finalizar") || all.contains("end live") || all.contains("end call")
                || all.contains("colgar") || all.contains("hang up")) controlSignals++;
        return (liveWord && controlSignals >= 1) || controlSignals >= 2;
    }


    /**
     * Package allow-list only. The Google app is included because the current
     * Gemini launcher can hand the visible Gemini surface to the official
     * Google app process. This method intentionally does NOT prove that a
     * googlequicksearchbox window is Gemini; callers that acquire roots must
     * use isVerifiedGeminiSurface() before returning/acting on one.
     */
    public static boolean isGeminiPackage(CharSequence packageName) {
        if (packageName == null) return false;
        return GEMINI_APP_PACKAGE.contentEquals(packageName)
                || GEMINI_GOOGLE_HOST_PACKAGE.contentEquals(packageName);
    }

    /** True when a root belongs to one of the two official packages that can host Gemini. */
    public static boolean isGeminiRoot(AccessibilityNodeInfo root) {
        return root != null && isGeminiPackage(root.getPackageName());
    }

    /**
     * Strong semantic proof that a root is a Gemini UI surface.
     *
     * The standalone Gemini package is trusted directly. The Google app is a
     * much broader host, so package identity alone is never enough: we require
     * Gemini-specific accessible UI evidence before automation is allowed.
     * This prevents a normal Google Search/Discover screen from being treated
     * as Gemini merely because it shares the host process.
     */
    public static boolean isVerifiedGeminiSurface(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null) return false;
        if (GEMINI_APP_PACKAGE.contentEquals(root.getPackageName())) return true;
        if (!GEMINI_GOOGLE_HOST_PACKAGE.contentEquals(root.getPackageName())) return false;

        /*
         * FIELD EVIDENCE (alpha6 passive probe, Android 16): current Gemini can
         * expose almost no visible text in the accessibility tree while still
         * exposing a dense family of resource ids prefixed assistant_robin_.
         * Robin is therefore treated as a structural capability signature, not
         * as a selector for one particular screen. We require more than one
         * matching node (or one of the high-level containers) so a random Google
         * Search view cannot become actionable merely because it shares the host
         * process.
         */
        if (hasRobinResourceSignature(root)) return true;
        return hasGeminiSurfaceSignature(root);
    }

    /**
     * Structural Gemini signature discovered from the real device tree.
     * This deliberately does not depend on localized UI copy such as "Gemini",
     * rendering its assistant_robin surface; screen-specific actions still
     * require their own postconditions.
     */
    public static boolean hasRobinResourceSignature(AccessibilityNodeInfo root) {
        if (root == null) return false;
        int robinIds = 0;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            if (id != null && id.contains(":id/assistant_robin_")) {
                robinIds++;
                if (id.endsWith("assistant_robin_main_activity")
                        || id.endsWith("assistant_robin_chat_fragment_container")
                        || id.endsWith("assistant_robin_extensions_consent_dialog_constraint_layout")) {
                    return true;
                }
                if (robinIds >= 2) return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /** True when Gemini is showing a consent/permission decision owned by Gemini. */
    public static boolean isBlockingConsentDialog(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null
                || !GEMINI_GOOGLE_HOST_PACKAGE.contentEquals(root.getPackageName())) return false;
        boolean consentSurface = hasViewIdFragment(root, "assistant_robin_extensions_consent_")
                || hasViewIdFragment(root, "assistant_robin_consent_dialog_");
        if (!consentSurface) return false;
        // Require an actual choice surface. The field probe exposed Android's
        // standard button1/button2 ids; we do not click either of them.
        return hasViewIdFragment(root, "android:id/button1")
                || hasViewIdFragment(root, "android:id/button2");
    }

    public static boolean hasGeminiSurfaceSignature(AccessibilityNodeInfo root) {
        if (root == null) return false;

        boolean brand = hasExactNode(root, "Gemini");
        int companionSignals = 0;
        if (hasAny(root, "Nuevo chat", "New chat")) companionSignals++;
        if (hasAny(root, "Buscar chats", "Search chats")) companionSignals++;
        if (hasAny(root, "Biblioteca", "Library")) companionSignals++;
        if (hasAny(root, "Administrador de Gems", "Gems manager", "Manage Gems")) companionSignals++;

        // These phrases are sufficiently Gemini-specific to establish the
        // surface even if the brand heading itself is absent from the current
        // accessibility subtree/modal.
        if (findContains(root,
                "Pregunta a Gemini", "Ask Gemini",
                "El escenario es tuyo",
                "Actividad en Gemini", "Gemini Apps Activity",
                "Importar memoria a Gemini", "Import memory to Gemini") != null) {
            return true;
        }

        // Gemini Live can replace the standard chat chrome. Its combination of
        // Live + call controls is already guarded against a simple launcher button.
        if (isLiveScreen(root)) return true;

        return brand && companionSignals >= 1;
    }


    /** True when Gemini explicitly exposes a temporary/ephemeral chat surface. */
    public static boolean isTemporaryChat(AccessibilityNodeInfo root) {
        if (root == null) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            String text = norm(nodeText(n));
            String desc = norm(n.getContentDescription() == null ? "" : n.getContentDescription().toString());
            if ((id != null && (id.toLowerCase().contains("temporary") || id.toLowerCase().contains("ephemeral")))
                    || text.equals("chat temporal") || desc.equals("chat temporal")
                    || text.equals("conversacion temporal") || desc.equals("conversacion temporal")
                    || text.equals("temporary chat") || desc.equals("temporary chat")) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /**
     * Clicks only a normal New chat control. If the clickable ancestor also
     * contains a Temporary chat control, fail closed rather than risk entering
     * a Live-incompatible ephemeral conversation.
     */
    public static boolean clickNormalNewChat(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findAny(root, "Nuevo chat", "New chat", "Chat nuevo");
        if (label == null) return false;
        if (label.isClickable() && isActionablyVisible(label, root)) return label.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo cur = label.getParent();
        for (int depth = 0; cur != null && depth < 4; depth++, cur = cur.getParent()) {
            if (cur.isClickable() && isActionablyVisible(cur, root)) {
                if (subtreeHasTemporaryControl(cur)) return false;
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    /** Normal Live session surface: editable composer + real Live capability + not temporary. */
    public static boolean isNormalLiveCapableChat(AccessibilityNodeInfo root) {
        return root != null && !isTemporaryChat(root) && chatComposer(root) != null && hasVisibleGeminiLiveLauncher(root);
    }

    private static boolean subtreeHasTemporaryControl(AccessibilityNodeInfo root) {
        if (root == null) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            String text = norm(nodeText(n));
            String desc = norm(n.getContentDescription() == null ? "" : n.getContentDescription().toString());
            if ((id != null && (id.toLowerCase().contains("temporary") || id.toLowerCase().contains("ephemeral")))
                    || text.equals("chat temporal") || desc.equals("chat temporal")
                    || text.equals("conversacion temporal") || desc.equals("conversacion temporal")
                    || text.equals("temporary chat") || desc.equals("temporary chat")) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    /**
     * Capability resolver for the Live launcher. The alpha7 field probe exposed
     * contentDescription="Open Gemini Live" while older/localized builds use
     * shorter labels. The clickable ancestor is resolved semantically.
     */
    public static boolean clickGeminiLive(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo compose = findByViewIdSuffix(root,
                "assistant_robin_input_voice_chat_button_compose");
        if (compose != null) {
            AccessibilityNodeInfo semantic = findAny(compose,
                    "Open Gemini Live", "Abrir Gemini Live",
                    "Gemini Live", "Iniciar Live", "Start Live", "Live");
            if (semantic != null && clickNodeOrClickableParent(semantic)) return true;
            AccessibilityNodeInfo unique = uniqueClickableDescendant(compose);
            if (unique != null && clickNodeOrClickableParent(unique)) return true;
        }

        AccessibilityNodeInfo semantic = findAny(root,
                "Open Gemini Live", "Abrir Gemini Live",
                "Gemini Live", "Iniciar Live", "Start Live", "Live");
        if (semantic != null && clickNodeOrClickableParent(semantic)) return true;

        // Alpha7 physical probes (Nubia + Samsung): current Gemini can expose the
        // blue Live orb with neither the historical Robin resource-id nor a useful
        // accessibility label. What remains stable is the composer structure: with
        // an EMPTY text field, normal Live-capable chat has two action controls on
        // the right (microphone + Live). Temporary/non-Live surfaces expose only
        // the microphone. Use that capability pattern instead of device names or
        // absolute coordinates.
        AccessibilityNodeInfo structural = structuralLiveCandidate(root);
        return structural != null && clickNodeOrClickableParent(structural);
    }

    /**
     * Ends an active Live session without exposing provider labels to callers.
     * Exact semantic labels are accepted only while the root already satisfies
     * isLiveScreen(); resource-id evidence must contain both a Live token and
     * an end/close/stop token. This prevents a generic Close button elsewhere
     * in Gemini from being treated as a call-control.
     */
    public static boolean clickEndLive(AccessibilityNodeInfo root) {
        if (root == null || !isLiveScreen(root)) return false;
        AccessibilityNodeInfo exact=findAny(root,
                "Finalizar", "End", "Finalizar Live", "End Live",
                "Cerrar Live", "Close Live", "Salir de Live");
        if (exact != null && clickNodeOrClickableParent(exact)) return true;
        Queue<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        while(!q.isEmpty()){
            AccessibilityNodeInfo n=q.remove();
            String id=n.getViewIdResourceName()==null?"":n.getViewIdResourceName().toLowerCase(Locale.ROOT);
            String sig=norm(nodeText(n)+" "+(n.getContentDescription()==null?"":n.getContentDescription().toString()));
            boolean live=id.contains("live")||sig.contains("live");
            boolean end=id.contains("end")||id.contains("close")||id.contains("stop")||id.contains("hangup")
                    ||sig.equals("finalizar")||sig.equals("end")||sig.contains("end live")
                    ||sig.contains("finalizar live")||sig.contains("cerrar live")||sig.contains("close live")
                    ||sig.contains("salir de live");
            if(live&&end&&n.isEnabled()&&isActionablyVisible(n,root)&&clickNodeOrClickableParent(n))return true;
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}
        }
        return false;
    }

    /**
     * Opens Gemini's native attachment surface. Bridge/Artemis never reads the
     * selected bytes; Android's picker and Gemini own that handoff.
     */
    public static boolean clickAddFiles(AccessibilityNodeInfo root) {
        if(root==null||chatComposer(root)==null)return false;
        AccessibilityNodeInfo container=findByViewIdSuffix(root,"assistant_mode_convergence_chat_input_layout");
        if(container==null)container=findByViewIdSuffix(root,"assistant_chat_add_reply_container");
        if(container==null)container=findByViewIdSuffix(root,"assistant_chat_add_reply_wrapper");
        if(container==null)return false;
        Queue<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(container);
        while(!q.isEmpty()){
            AccessibilityNodeInfo n=q.remove();
            String id=n.getViewIdResourceName()==null?"":n.getViewIdResourceName().toLowerCase(Locale.ROOT);
            String sig=norm(nodeText(n)+" "+(n.getContentDescription()==null?"":n.getContentDescription().toString()));
            boolean candidate=id.contains("attachment")||id.contains("add_file")||id.contains("add_attachment")
                    ||sig.equals("+")||sig.equals("anadir archivos")||sig.equals("agregar archivos")
                    ||sig.equals("add files")||sig.equals("adjuntar")||sig.equals("attach");
            if(candidate&&n.isEnabled()&&isActionablyVisible(n,root)&&clickNodeOrClickableParent(n))return true;
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}
        }
        return false;
    }

    /** True while Gemini visibly exposes an in-flight response/thinking control. */
    public static boolean isResponseInProgress(AccessibilityNodeInfo root) {
        if (root == null) return false;
        if (findAny(root, "Responder ahora", "Respond now",
                "Detener respuesta", "Stop response", "Stop generating",
                "Detener", "Stop") != null) return true;
        AccessibilityNodeInfo slot = findByViewIdSuffix(root,
                "assistant_robin_input_voice_chat_button_compose");
        if (slot != null) {
            Queue<AccessibilityNodeInfo> q = new ArrayDeque<>(); q.add(slot);
            while (!q.isEmpty()) {
                AccessibilityNodeInfo n = q.remove();
                String id = n.getViewIdResourceName()==null?"":n.getViewIdResourceName().toLowerCase(Locale.ROOT);
                String sig = norm(nodeText(n)+" "+(n.getContentDescription()==null?"":n.getContentDescription().toString()));
                if ((id.contains("stop")||id.contains("cancel")
                        ||sig.contains("stop response")||sig.contains("stop generating")
                        ||sig.contains("detener respuesta"))
                        && n.isEnabled()&&isActionablyVisible(n,root)) return true;
                for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}
            }
        }
        return false;
    }

    public static boolean hasRespondNow(AccessibilityNodeInfo root) {
        return findAny(root, "Responder ahora", "Respond now") != null;
    }

    public static boolean clickRespondNow(AccessibilityNodeInfo root) {
        return clickAnyExact(root, "Responder ahora", "Respond now");
    }

    public static boolean hasVisibleGeminiLiveLauncher(AccessibilityNodeInfo root) {
        return !liveLauncherEvidence(root).equals("none");
    }

    /** Human-readable reason used by passive field diagnostics. */
    public static String liveLauncherEvidence(AccessibilityNodeInfo root) {
        if (root == null) return "none";
        AccessibilityNodeInfo compose = findByViewIdSuffix(root,
                "assistant_robin_input_voice_chat_button_compose");
        if (compose != null) return "robin-resource";
        if (findAny(root,
                "Open Gemini Live", "Abrir Gemini Live",
                "Gemini Live", "Iniciar Live", "Start Live", "Live") != null)
            return "semantic";
        return structuralLiveCandidate(root) != null ? "structural-dual-action" : "none";
    }

    /** Number of distinct visible action buttons to the right of the composer. */
    public static int visibleComposerRightActionCount(AccessibilityNodeInfo root) {
        ComposerActions a = composerActions(root);
        return a == null ? 0 : a.count;
    }

    private static AccessibilityNodeInfo structuralLiveCandidate(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo editable = chatComposer(root);
        if (editable == null) return null;
        CharSequence current = editable.getText();
        // Compose can expose an empty-field placeholder through getText().
        // Treat it as empty when the accessibility node says it is showing hint text.
        if (current != null && !editable.isShowingHintText()
                && !current.toString().trim().isEmpty()) return null;
        ComposerActions actions = composerActions(root);
        // Two distinct controls on the right are the field-observed normal-chat
        // signature (mic + Live). Requiring both prevents a single unlabeled mic
        // in Temporary Chat from being mistaken for Live.
        return actions != null && actions.count >= 2 ? actions.rightmost : null;
    }

    private static ComposerActions composerActions(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo editable = chatComposer(root);
        if (root == null || editable == null) return null;
        AccessibilityNodeInfo container = findByViewIdSuffix(root, "assistant_mode_convergence_chat_input_layout");
        if (container == null) container = findByViewIdSuffix(root, "assistant_chat_add_reply_container");
        if (container == null) container = findByViewIdSuffix(root, "assistant_chat_add_reply_wrapper");
        if (container == null) return null;

        Rect editBounds = new Rect(); editable.getBoundsInScreen(editBounds);
        Rect containerBounds = new Rect(); container.getBoundsInScreen(containerBounds);
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>(); q.add(container);
        java.util.ArrayList<Rect> seen = new java.util.ArrayList<>();
        AccessibilityNodeInfo rightmost = null; int right = Integer.MIN_VALUE; int count = 0;
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n != container && n != editable && n.isClickable() && n.isEnabled() && isActionablyVisible(n, root)) {
                Rect b = new Rect(); n.getBoundsInScreen(b);
                if (!b.isEmpty()) {
                    String sig = norm(nodeText(n) + " " + (n.getContentDescription()==null?"":n.getContentDescription().toString()));
                    String id = n.getViewIdResourceName()==null?"":n.getViewIdResourceName().toLowerCase(Locale.ROOT);
                    boolean leftUtility = sig.equals("+") || sig.contains("adjuntar") || sig.contains("attach")
                            || sig.contains("camara") || sig.contains("camera") || sig.contains("galeria")
                            || sig.contains("gallery") || id.contains("attachment") || id.contains("camera");
                    int referenceCenter = !editBounds.isEmpty() ? editBounds.centerX() : containerBounds.centerX();
                    boolean onRight = b.centerX() >= referenceCenter;
                    if (!leftUtility && onRight && !sameRect(seen,b)) {
                        seen.add(new Rect(b)); count++;
                        if (b.right > right) { right=b.right; rightmost=n; }
                    }
                }
            }
            for (int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.add(c);}
        }
        return new ComposerActions(count,rightmost);
    }

    private static boolean sameRect(java.util.List<Rect> seen, Rect candidate) {
        for (Rect r: seen) {
            if (r.equals(candidate)) return true;
            int dx=Math.abs(r.centerX()-candidate.centerX());
            int dy=Math.abs(r.centerY()-candidate.centerY());
            int dw=Math.abs(r.width()-candidate.width());
            int dh=Math.abs(r.height()-candidate.height());
            if (dx<=4 && dy<=4 && dw<=8 && dh<=8) return true;
            Rect overlap=new Rect(r);
            if (overlap.intersect(candidate)) {
                long ia=(long)overlap.width()*overlap.height();
                long ma=Math.max(1L,Math.min((long)r.width()*r.height(),(long)candidate.width()*candidate.height()));
                if (ia*100L/ma>=90L) return true;
            }
        }
        return false;
    }

    private static final class ComposerActions {
        final int count; final AccessibilityNodeInfo rightmost;
        ComposerActions(int count, AccessibilityNodeInfo rightmost){this.count=count;this.rightmost=rightmost;}
    }

    // ---------------------------------------------------------------------
    // ---------------------------------------------------------------------









    /** Locate a node by resource-id suffix; resource ids are used as evidence, not coordinates. */
    public static AccessibilityNodeInfo findByViewIdSuffix(
            AccessibilityNodeInfo root, String suffix) {
        if (root == null || suffix == null || suffix.isEmpty()) return null;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            if (id != null && id.endsWith(suffix) && isActionablyVisible(n, root)) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    public static boolean hasViewIdFragment(AccessibilityNodeInfo root, String fragment) {
        if (root == null || fragment == null || fragment.isEmpty()) return false;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            String id = n.getViewIdResourceName();
            if (id != null && id.contains(fragment)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo uniqueClickableDescendant(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo only = null;
        int count = 0;
        Queue<AccessibilityNodeInfo> q = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) q.add(child);
        }
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.remove();
            if (n.isClickable() && isActionablyVisible(n, root)) {
                count++;
                only = n;
                if (count > 1) return null;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return count == 1 ? only : null;
    }

    /**
     * field dump exposed this dedicated resource id on the real device.
     */


    /**
     * the accessibility meaning observed on the real Gemini toolbar. This is
     * intentionally not a coordinate click and the caller must verify the
     * postcondition before advancing.
     */

    /** True only when the Gemini navigation drawer is actually visible. */
    public static boolean isNavigationDrawerOpen(AccessibilityNodeInfo root) {
        if (root == null) return false;
        int signals = 0;
        if (hasExactNode(root, "Nuevo chat", "New chat")) signals++;
        if (hasExactNode(root, "Buscar chats", "Search chats",
                "Buscar conversaciones", "Search conversations")) signals++;
        if (hasExactNode(root, "Biblioteca", "Library")) signals++;
        return signals >= 2;
    }

    /**
     * the alpha9 diagnostic proved hidden global-chat chrome remains in the
     * tree and previously caused this predicate to return a false positive.
     *
     * is itself collapsible (a chevron toggles it), and its header keeps the
     * "create new") was actually reachable, so callers skipped the one click
     * Only accept signals that require the section to actually be expanded.
     */

    private static boolean hasExactNode(AccessibilityNodeInfo root, String... candidates) {
        return findAny(root, candidates) != null;
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
            if (n.isScrollable() && isActionablyVisible(n, root) && n.performAction(action)) return true;
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
            if (n.isEditable() && isActionablyVisible(n, root)) {
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
        // EditText reports its placeholder ("Agrega instrucciones
        // detalladas...") through getText() - not just getHintText() - while
        // empty, which is standard Android accessibility behaviour for empty
        // text fields (TextView exposes hint text through the accessibility
        // text so screen readers announce it) but was being read here as if
        // it were real saved content. That made a genuinely empty,
        // failed the ownership check in SETUP case 9 with "no puedo probar
        // created. isShowingHintText() (API 26+) is the real signal for
        // whether getText() is currently the hint rather than saved content.
        if (best == null || best.getText() == null || best.isShowingHintText()) return "";
        return best.getText().toString();
    }

    /**
     * Dialog/search safety: never guess among multiple editables. Prefer the
     * focused field; otherwise accept only a single visible editable node.
     */
    private static AccessibilityNodeInfo focusedOrOnlyEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo only=null; int count=0;
        Queue<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        while(!q.isEmpty()){
            AccessibilityNodeInfo n=q.remove();
            if(n.isEditable()&&isActionablyVisible(n,root)){
                if(n.isFocused())return n;
                only=n;count++;
            }
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)q.add(child);}
        }
        return count==1?only:null;
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
            if (n.isClickable() && n.isEnabled() && isVisibleOnScreen(n)) {
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
            if (n.isCheckable() && isActionablyVisible(n, root)) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    /**
     * Whether a node can safely participate in a control/state decision for
     * the supplied root viewport. Gemini keeps duplicate off-screen trees
     * (including Live and New-chat controls with negative coordinates); those
     * nodes must never be treated as visible controls.
     */
    public static boolean isActionablyVisible(
            AccessibilityNodeInfo node, AccessibilityNodeInfo viewportRoot) {
        if (node == null || viewportRoot == null) return false;
        try {
            if (!node.isVisibleToUser()) return false;
        } catch (Exception ignored) {}
        Rect nr = new Rect();
        Rect vr = new Rect();
        try {
            node.getBoundsInScreen(nr);
            viewportRoot.getBoundsInScreen(vr);
        } catch (Exception e) {
            return false;
        }
        return ScreenBoundsPolicy.isActionableRect(
                nr.left, nr.top, nr.right, nr.bottom,
                vr.left, vr.top, vr.right, vr.bottom);
    }

    private static boolean isVisibleOnScreen(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            if (!node.isVisibleToUser()) return false;
        } catch (Exception ignored) {}
        Rect r = new Rect();
        try { node.getBoundsInScreen(r); }
        catch (Exception e) { return false; }
        return r.right > r.left && r.bottom > r.top
                && r.right > 0 && r.bottom > 0 && r.left >= 0 && r.top >= 0;
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
