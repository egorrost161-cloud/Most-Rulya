package com.lada.wheelbridge;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Слушатель уведомлений.
 * Пока пользователь не даст доступ — мост не работает.
 * Как только доступ выдан, система вызывает onListenerConnected,
 * и мы автоматически запускаем WheelBridgeService.
 */
public class WheelNotificationListener extends NotificationListenerService {

    private static final String TAG = "WheelBridge";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "NotificationListener connected");
        try {
            Intent intent = new Intent(this, WheelBridgeService.class);
            startService(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start bridge from notification listener", e);
        }
    }
}
