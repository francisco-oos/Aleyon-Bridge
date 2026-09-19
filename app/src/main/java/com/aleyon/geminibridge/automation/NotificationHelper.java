package com.aleyon.geminibridge.automation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.aleyon.geminibridge.MainActivity;
import com.aleyon.geminibridge.R;

/**
 * Local (device-only) notification shown after a session closes, surfacing
 * the pedagogical summary Gemini already produced (HECHOS OBSERVADOS,
 * ERRORES RECURRENTES, DICCIONARIO DE LA SESION, etc. - see close_session.txt
 * and live_master.txt's CIERRE section) instead of leaving it buried in the
 * chat (2026-09-06, user request: a small recap/tips notification on close).
 *
 * Deliberately shows the summary as produced rather than trying to regex out
 * just "the hard words": Gemini's exact section wording is not perfectly
 * uniform between the two prompts that can trigger a close, and this project
 * treats fragile parsing of freeform model text as a recurring bug class
 * (see MarkerParser/D-060 history) - failing open to "show the whole
 * summary" is safer than failing closed to "show nothing" or, worse, an
 * empty/mis-parsed excerpt.
 *
 * Every entry point here is defensive by construction: a missing permission,
 * missing system service, or any unexpected exception simply results in no
 * notification, never a crash - this runs from a background accessibility
 * service with no UI to recover from a failure.
 */
public final class NotificationHelper {
    private static final String CHANNEL_ID = "aleyon_session_summary";
    private static final int MAX_BODY_CHARS = 1400;

    private NotificationHelper() {}

    public static void postSessionClosed(Context context, String profileId,
            String profileLabel, String summary) {
        if (context == null || profileId == null) return;
        try {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 33
                    && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            ensureChannel(nm);

            String label = profileLabel == null || profileLabel.trim().isEmpty()
                    ? "tu sesión" : profileLabel.trim();
            String body = excerpt(summary);

            Intent tapIntent = new Intent(context, MainActivity.class);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, profileId.hashCode(),
                    tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Sesión de " + label + " cerrada")
                    .setContentText(body.isEmpty()
                            ? "Sesión cerrada correctamente."
                            : firstLine(body))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            if (!body.isEmpty()) {
                builder.setStyle(new Notification.BigTextStyle().bigText(body));
            }

            nm.notify(profileId.hashCode(), builder.build());

            // Let MainActivity know a summary is waiting so it can open the
            // latest full close directly on next resume, whether the user
            // gets there by tapping this notification or just reopening the
            // app (2026-09-06, user report: tapping the notification itself
            // just opened Aleyon with nothing readable, with no way back to
            // this text afterward).
            context.getSharedPreferences("aleyon_runtime", Context.MODE_PRIVATE)
                    .edit().putString("pending_summary_profile", profileId).apply();
        } catch (Exception ignored) {
            // Optional feature - never let a notification failure affect the
            // automation transaction that already completed successfully.
        }
    }

    private static void ensureChannel(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Resumen de sesión", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Un resumen breve cada vez que cierras una sesión de práctica.");
        nm.createNotificationChannel(channel);
    }

    private static String excerpt(String summary) {
        if (summary == null) return "";
        String s = summary.trim();
        if (s.length() <= MAX_BODY_CHARS) return s;
        return s.substring(0, MAX_BODY_CHARS).trim() + "…";
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = (nl > 0 ? s.substring(0, nl) : s).trim();
        return line.length() > 90 ? line.substring(0, 90) + "…" : line;
    }
}
