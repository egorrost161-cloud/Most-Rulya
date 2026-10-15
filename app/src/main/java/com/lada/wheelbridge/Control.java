package com.lada.wheelbridge;

import android.media.session.MediaController;

/**
 * Функциональный интерфейс: каждая команда знает, как себя применить
 * к TransportControls активного медиаплеера.
 */
public interface Control {
    void apply(MediaController.TransportControls tc);

    // Предопределённые команды
    Control NEXT  = MediaController.TransportControls::skipToNext;
    Control PREV  = MediaController.TransportControls::skipToPrevious;
    Control PLAY  = MediaController.TransportControls::play;
    Control PAUSE = MediaController.TransportControls::pause;
    Control STOP  = MediaController.TransportControls::stop;
}
