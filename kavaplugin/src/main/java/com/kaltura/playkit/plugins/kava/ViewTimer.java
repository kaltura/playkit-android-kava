package com.kaltura.playkit.plugins.kava;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by anton.afanasiev on 19/02/2018.
 */

class ViewTimer {

    static final int TEN_SECONDS_IN_MS = 10000;
    private static final long ONE_SECOND_IN_MS = 1000;

    private int viewEventTimeCounter;

    private boolean isPaused;
    private boolean viewEventsEnabled = true;

    private Timer viewEventTimer;
    private ViewEventTrigger viewEventTrigger;

    interface ViewEventTrigger {

        /**
         * Called when VIEW event should be sent.
         */
        void triggerViewEvent();

        /**
         * Triggered every 1000ms
         */
        void onTick();
    }

    void start() {
        stop();
        viewEventTimer = new Timer();
        viewEventTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isPaused && viewEventsEnabled) {
                    viewEventTimeCounter += ONE_SECOND_IN_MS;
                    if (viewEventTimeCounter >= TEN_SECONDS_IN_MS && viewEventTrigger != null) {
                        viewEventTrigger.triggerViewEvent();
                        viewEventTimeCounter = 0;
                    }
                }
                if (viewEventTrigger != null) {
                    viewEventTrigger.onTick();
                }
            }
        }, 0, ONE_SECOND_IN_MS);
    }

    void stop() {
        if (viewEventTimer == null) {
            return;
        }
        viewEventTimer.cancel();
        viewEventTimer = null;
    }

    void pause() {
        isPaused = true;
    }

    void resume() {
        isPaused = false;
    }

    void setViewEventTrigger(ViewEventTrigger viewEventTrigger) {
        this.viewEventTrigger = viewEventTrigger;
    }
}
