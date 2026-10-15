package com.lada.wheelbridge;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.media.MediaBrowserService;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Ядро приложения.
 * Работает как MediaBrowserService, регистрируется у Яндекс.Авто
 * как "аудиоисточник". Принимает команды кнопок руля через BridgeCallback
 * и перенаправляет их в активный медиаплеер.
 */
public class WheelBridgeService extends MediaBrowserService {

    private static final String TAG = "WheelBridge";
    private static final String ROOT_ID = "root";
    private static final long POLL_MS = 500L;

    // Разрешённые действия для нашей MediaSession
    private static final long ACTIONS =
            PlaybackState.ACTION_PLAY
          | PlaybackState.ACTION_PAUSE
          | PlaybackState.ACTION_PLAY_PAUSE
          | PlaybackState.ACTION_SKIP_TO_NEXT
          | PlaybackState.ACTION_SKIP_TO_PREVIOUS
          | PlaybackState.ACTION_STOP;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaSession session;
    private MediaSessionManager sessionManager;
    private ComponentName listenerComponent;
    private MediaController target;
    private boolean polling;
    private boolean sessionsListenerAdded;

    // ===== CALLBACK АКТИВНОГО ПЛЕЕРА =====
    private final MediaController.Callback targetCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata md) {
            if (session != null) session.setMetadata(copyMetadata(md));
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState ps) {
            if (session != null) session.setPlaybackState(copyState(ps));
        }
    };

    // ===== POLL =====
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        session = new MediaSession(this, TAG);
        session.setCallback(new BridgeCallback());
        session.setActive(true);
        setSessionToken(session.getSessionToken());

        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, WheelNotificationListener.class);

        ensureSessionsListener();

        polling = true;
        handler.post(poll);

        Log.i(TAG, "WheelBridgeService started");
    }

    @Override
    public void onDestroy() {
        polling = false;
        handler.removeCallbacks(poll);
        detachTarget();
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        super.onDestroy();
        Log.i(TAG, "WheelBridgeService stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    // ===== MediaBrowserService API =====
    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowserService.MediaItem>> result) {
        result.sendResult(new ArrayList<>());
    }

    // ===== refresh() =====
    private void refresh() {
        if (sessionManager == null) return;
        List<MediaController> sessions;
        try {
            sessions = sessionManager.getActiveSessions(listenerComponent);
        } catch (SecurityException e) {
            Log.w(TAG, "notification access not granted");
            return;
        }
        if (sessions == null || sessions.isEmpty()) {
            detachTarget();
            return;
        }

        MediaController best = null;
        for (MediaController mc : sessions) {
            PlaybackState ps = mc.getPlaybackState();
            if (ps != null && isPlaying(ps.getState())) {
                best = mc;
                break;
            }
        }
        if (best == null) best = sessions.get(0);

        if (best != target) {
            detachTarget();
            target = best;
            try {
                target.registerCallback(targetCallback, handler);
                if (session != null) {
                    session.setMetadata(copyMetadata(target.getMetadata()));
                    session.setPlaybackState(copyState(target.getPlaybackState()));
                }
                Log.i(TAG, "target = " + target.getPackageName());
            } catch (Exception e) {
                Log.e(TAG, "attach target failed", e);
            }
        }
    }

    // ===== forward() =====
    private void forward(Control cmd) {
        if (target == null) refresh();
        if (target == null) {
            Log.w(TAG, "no target to forward");
            return;
        }
        try {
            cmd.apply(target.getTransportControls());
            Log.i(TAG, "forwarded to " + target.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "forward failed", e);
        }
    }

    private void detachTarget() {
        if (target == null) return;
        try { target.unregisterCallback(targetCallback); } catch (Exception ignored) {}
        target = null;
    }

    private void ensureSessionsListener() {
        if (sessionsListenerAdded || sessionManager == null) return;
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    controllers -> refresh(),
                    listenerComponent,
                    handler);
            sessionsListenerAdded = true;
            Log.i(TAG, "sessions listener added");
        } catch (SecurityException e) {
            Log.w(TAG, "notification access not granted");
        }
    }

    private MediaMetadata copyMetadata(MediaMetadata src) {
        if (src == null) return null;
        MediaMetadata.Builder b = new MediaMetadata.Builder();

        String title = src.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = src.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = src.getString(MediaMetadata.METADATA_KEY_ALBUM);
        long duration = src.getLong(MediaMetadata.METADATA_KEY_DURATION);

        if (title != null) {
            b.putString(MediaMetadata.METADATA_KEY_TITLE, title);
            b.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title);
        }
        if (artist != null) {
            b.putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
            b.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist);
        }
        if (album != null) b.putString(MediaMetadata.METADATA_KEY_ALBUM, album);
        if (duration > 0) b.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);

        Bitmap art = src.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);

        return b.build();
    }

    private PlaybackState copyState(PlaybackState src) {
        long pos = 0;
        float speed = 0f;
        int state = PlaybackState.STATE_STOPPED;

        if (src != null) {
            state = src.getState();
            pos = src.getPosition();
            speed = src.getPlaybackSpeed();
        }

        return new PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(state, pos, speed)
                .build();
    }

    private static boolean isPlaying(int state) {
        return state == PlaybackState.STATE_PLAYING
            || state == PlaybackState.STATE_BUFFERING
            || state == PlaybackState.STATE_FAST_FORWARDING
            || state == PlaybackState.STATE_REWINDING;
    }

    // ===== BridgeCallback =====
    private class BridgeCallback extends MediaSession.Callback {
        @Override public void onPlay()             { forward(Control.PLAY); }
        @Override public void onPause()            { forward(Control.PAUSE); }
        @Override public void onStop()             { forward(Control.STOP); }
        @Override public void onSkipToNext()       { forward(Control.NEXT); }
        @Override public void onSkipToPrevious()   { forward(Control.PREV); }
    }
}
