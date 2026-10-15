package com.lada.wheelbridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Утилита для работы с доступом к уведомлениям.
 * Все методы статические.
 */
public final class AccessPrompt {

    private static final String TAG = "WheelBridge";
    private static final long RETRY_MS = 8000L; // 8 секунд anti-spam

    private static long lastAttempt = 0L;
    private static long lastScreen = 0L;

    private AccessPrompt() {}

    /** Проверить, выдан ли доступ к уведомлениям нашему слушателю. */
    public static boolean isEnabled(Context ctx) {
        String enabled = Settings.Secure.getString(
                ctx.getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;

        ComponentName our = new ComponentName(ctx, WheelNotificationListener.class);
        String full = our.flattenToString();
        String shortName = our.flattenToShortString();

        for (String s : enabled.split(":")) {
            if (full.equals(s) || shortName.equals(s)) return true;
        }
        return false;
    }

    /** Открыть системные настройки доступа к уведомлениям. */
    public static boolean openSettings(Context ctx) {
        return openSettings(ctx, false);
    }

    public static boolean openSettings(Context ctx, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastAttempt < RETRY_MS) return true;
        lastAttempt = now;

        // Способ 1: стандартный Intent
        Intent i1 = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        i1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (start(ctx, i1)) return true;

        // Способ 2: явный ComponentName (fallback для некоторых прошивок)
        Intent i2 = new Intent();
        i2.setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.Settings$NotificationAccessSettingsActivity"));
        i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return start(ctx, i2);
    }

    /** Открыть своё приложение (SettingsActivity). */
    public static void openOwnScreen(Context ctx) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastScreen < RETRY_MS) return;
        lastScreen = now;

        Intent i = new Intent(ctx, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        start(ctx, i);
    }

    private static boolean start(Context ctx, Intent intent) {
        if (intent.resolveActivity(ctx.getPackageManager()) == null) {
            Log.w(TAG, "no activity for " + intent);
            return false;
        }
        try {
            ctx.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open " + intent, e);
            return false;
        }
    }
}
