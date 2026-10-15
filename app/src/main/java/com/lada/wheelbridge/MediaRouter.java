package com.lada.wheelbridge;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import java.util.List;

/**
 * Единый "мозг" приложения.
 * Хранит активный плеер (target) и перенаправляет в него команды.
 */
public final class MediaRouter {

    private static final String TAG = "WheelBridge";

    private static MediaRouter instance;

    private MediaController target;
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;

    private MediaRouter(Context context) {
        sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(context, WheelNotificationListener.class);
    }

    public static synchronized MediaRouter get(Context context) {
        if (instance == null) {
            instance = new MediaRouter(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized MediaRouter get() {
        return instance;
    }

    /** Найти активный плеер и обновить target. */
    public void refresh() {
        if (sessionManager == null) return;
        List<MediaController> sessions;
        try {
            sessions = sessionManager.getActiveSessions(listenerComponent);
        } catch (SecurityException e) {
            Log.w(TAG, "notification access not granted");
            return;
        }
        if (sessions == null || sessions.isEmpty()) {
            target = null;
            return;
        }

        MediaController best = null;
        for (MediaController mc : sessions) {
            PlaybackState ps = mc.getPlaybackState();
            int state = ps != null ? ps.getState() : 0;
            if (isPlaying(state)) {
                best = mc;
                break;
            }
        }
        if (best == null) best = sessions.get(0);

        target = best;
    }

    /** Перенаправить команду в активный плеер. */
    public void forward(Control cmd) {
        if (target == null) refresh();
        if (target == null) {
            Log.w(TAG, "no active media session");
            return;
        }
        try {
            cmd.apply(target.getTransportControls());
        } catch (Exception e) {
            Log.e(TAG, "forward failed", e);
        }
    }

    public MediaController getTarget() {
        return target;
    }

    public void setTarget(MediaController target) {
        this.target = target;
    }

    public ComponentName getListenerComponent() {
        return listenerComponent;
    }

    public MediaSessionManager getSessionManager() {
        return sessionManager;
    }

    private static boolean isPlaying(int state) {
        return state == PlaybackState.STATE_PLAYING
            || state == PlaybackState.STATE_BUFFERING
            || state == PlaybackState.STATE_FAST_FORWARDING
            || state == PlaybackState.STATE_REWINDING;
    }
}
