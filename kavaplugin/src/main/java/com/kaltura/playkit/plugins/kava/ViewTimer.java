package com.kaltura.playkit.plugins.kava;

import com.kaltura.playkit.PKLog;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by anton.afanasiev on 19/02/2018.
 */

class ViewTimer {
    private static final PKLog log = PKLog.get("ViewTimer");

    static final int TEN_SECONDS_IN_MS = 10000;
    private static final long ONE_SECOND_IN_MS = 1000;
    private static final long MAX_ALLOWED_VIEW_IDLE_TIME = 30000;

    private int viewEventTimeCounter;
    private int viewEventIdleCounter;

    private boolean isPaused;
    private boolean viewEventsEnabled = true;

    private Timer viewEventTimer;
    private ViewEventTrigger viewEventTrigger;

    interface ViewEventTrigger {

        /**
         * Called when VIEW event should be sent.
         */
        void onTriggerViewEvent();

        /**
         * Called when VIEW event was not sent for 30 seconds.
         */
        void onResetViewEvent();

        /**
         * Triggered every 1000ms
         */
        void onTick();
    }

    void start() {
        log.d("Kava - StartTimer");
        stop();
        viewEventTimer = new Timer();
        viewEventTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (viewEventsEnabled) {
                    if (isPaused) {
                        viewEventIdleCounter += ONE_SECOND_IN_MS;
                        if (viewEventIdleCounter >= MAX_ALLOWED_VIEW_IDLE_TIME) {
                            resetCounters();
                            viewEventTrigger.onResetViewEvent();
                        }
                    } else {
                        //log.d("viewEventTimeCounter = " + viewEventTimeCounter);
                        viewEventTimeCounter += ONE_SECOND_IN_MS;
                        if (viewEventTimeCounter >= TEN_SECONDS_IN_MS && viewEventTrigger != null) {
                            resetCounters();
                            viewEventTrigger.onTriggerViewEvent();
                        }
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

        resetCounters();
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

    private void resetCounters() {
        viewEventIdleCounter = 0;
        viewEventTimeCounter = 0;
    }

    void setViewEventsEnabled(boolean viewEventsEnabled) {
        this.viewEventsEnabled = viewEventsEnabled;
        resetCounters();
        if (!viewEventsEnabled) {
            viewEventTrigger.onResetViewEvent();
        }
    }
}
