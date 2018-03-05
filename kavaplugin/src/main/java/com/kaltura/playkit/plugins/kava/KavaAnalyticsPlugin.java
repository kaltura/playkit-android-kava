/*
 * ============================================================================
 * Copyright (C) 2017 Kaltura Inc.
 * 
 * Licensed under the AGPLv3 license, unless a different license for a
 * particular library is specified in the applicable library path.
 * 
 * You may obtain a copy of the License at
 * https://www.gnu.org/licenses/agpl-3.0.html
 * ============================================================================
 */

package com.kaltura.playkit.plugins.kava;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kaltura.netkit.connect.executor.APIOkRequestsExecutor;
import com.kaltura.netkit.connect.executor.RequestQueue;
import com.kaltura.netkit.connect.request.RequestBuilder;
import com.kaltura.netkit.connect.response.ResponseElement;
import com.kaltura.netkit.utils.OnRequestCompletion;
import com.kaltura.playkit.MessageBus;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKPlugin;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.PlayerState;
import com.kaltura.playkit.api.ovp.services.KavaService;
import com.kaltura.playkit.plugin.kava.BuildConfig;
import com.kaltura.playkit.utils.Consts;

import java.util.Map;

/**
 * Created by anton.afanasiev on 27/09/2017.
 */

public class KavaAnalyticsPlugin extends PKPlugin {

    private static final PKLog log = PKLog.get(KavaAnalyticsPlugin.class.getSimpleName());

    private Player player;
    private MessageBus messageBus;
    private PlayerState playerState;
    private PKMediaConfig mediaConfig;
    private DataHandler dataHandler;
    private RequestQueue requestExecutor;
    private KavaAnalyticsConfig pluginConfig;
    private PKEvent.Listener eventListener = initEventListener();

    private boolean playReached25;
    private boolean playReached50;
    private boolean playReached75;
    private boolean playReached100;

    private boolean isAutoPlay;
    private boolean isImpressionSent;
    private boolean isEnded = false;
    private boolean isPaused = true;
    private boolean isFirstPlay = true;

    private ViewTimer viewTimer;
    private ViewTimer.ViewEventTrigger viewEventTrigger = initViewTrigger();


    public static final Factory factory = new Factory() {
        @Override
        public String getName() {
            return "KavaAnalytics";
        }

        @Override
        public String getVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @Override
        public PKPlugin newInstance() {
            return new KavaAnalyticsPlugin();
        }

        @Override
        public void warmUp(Context context) {

        }
    };

    @Override
    protected void onLoad(Player player, Object config, MessageBus messageBus, Context context) {
        this.player = player;
        this.messageBus = messageBus;
        this.requestExecutor = APIOkRequestsExecutor.getSingleton();
        this.messageBus.listen(eventListener, (Enum[]) PlayerEvent.Type.values());
        dataHandler = new DataHandler(context, player);
        onUpdateConfig(config);
    }

    @Override
    protected void onUpdateMedia(PKMediaConfig mediaConfig) {
        this.mediaConfig = mediaConfig;
        viewTimer = new ViewTimer();
        viewTimer.setViewEventTrigger(viewEventTrigger);
        dataHandler.onUpdateMedia(mediaConfig);
        resetFlags();
    }

    @Override
    protected void onUpdateConfig(Object config) {
        this.pluginConfig = parsePluginConfig(config);
        dataHandler.onUpdateConfig(pluginConfig);
    }

    @Override
    protected void onApplicationPaused() {
        dataHandler.onApplicationPaused();
        viewTimer.setViewEventTrigger(null);
        viewTimer.stop();
    }

    @Override
    protected void onApplicationResumed() {
        dataHandler.setOnApplicationResumed();
        viewTimer.setViewEventTrigger(viewEventTrigger);
        viewTimer.start();
    }

    @Override
    protected void onDestroy() {
        viewTimer.setViewEventTrigger(null);
        viewTimer.stop();
        viewTimer = null;
    }

    private PKEvent.Listener initEventListener() {
        return new PKEvent.Listener() {
            @Override
            public void onEvent(PKEvent event) {
                if (event instanceof PlayerEvent) {
                    switch (((PlayerEvent) event).type) {
                        case STATE_CHANGED:
                            handleStateChanged((PlayerEvent.StateChanged) event);
                            break;
                        case LOADED_METADATA:
                            if (!isImpressionSent) {
                                sendAnalyticsEvent(KavaEvents.IMPRESSION);
                                if (isAutoPlay) {
                                    sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
                                    isAutoPlay = false;
                                }
                                isImpressionSent = true;
                            }
                            break;
                        case PLAY:
                            if (isFirstPlay) {
                                dataHandler.handleFirstPlay();
                            }
                            if (isImpressionSent) {
                                sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
                            } else {
                                isAutoPlay = true;
                            }
                            break;
                        case PAUSE:
                            setIsPaused(true);
                            sendAnalyticsEvent(KavaEvents.PAUSE);
                            break;
                        case PLAYING:
                            if (isFirstPlay) {
                                isFirstPlay = false;
                                viewTimer.start();
                                sendAnalyticsEvent(KavaEvents.PLAY);
                            } else {
                                if (isPaused && !isEnded) {
                                    sendAnalyticsEvent(KavaEvents.RESUME);
                                }
                            }
                            isEnded = false; // needed in order to prevent sending of RESUME event after REPLAY.
                            setIsPaused(false);
                            break;
                        case SEEKING:
                            dataHandler.handleSeek(event);
                            sendAnalyticsEvent(KavaEvents.SEEK);
                            break;
                        case REPLAY:
                            sendAnalyticsEvent(KavaEvents.REPLAY);
                            break;
                        case SOURCE_SELECTED:
                            dataHandler.handleSourceSelected(event);
                            break;
                        case ENDED:
                            maybeSentPlayerReachedEvent();
                            if (!playReached100) {
                                playReached100 = true;
                                sendAnalyticsEvent(KavaEvents.PLAY_REACHED_100_PERCENT);
                            }

                            isEnded = true;
                            setIsPaused(true);
                            break;
                        case PLAYBACK_INFO_UPDATED:
                            if (dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_VIDEO)) {
                                sendAnalyticsEvent(KavaEvents.FLAVOR_SWITCHED);
                            }
                            break;
                        case VIDEO_TRACK_CHANGED:
                            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_VIDEO);
                            sendAnalyticsEvent(KavaEvents.SOURCE_SELECTED);
                            break;
                        case AUDIO_TRACK_CHANGED:
                            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_AUDIO);
                            sendAnalyticsEvent(KavaEvents.AUDIO_SELECTED);
                            break;
                        case TEXT_TRACK_CHANGED:
                            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_TEXT);
                            sendAnalyticsEvent(KavaEvents.CAPTIONS);
                            break;
                        case ERROR:
                            dataHandler.handleError(event);
                            sendAnalyticsEvent(KavaEvents.ERROR);
                            break;
                    }
                }
            }
        };
    }

    private void handleStateChanged(PlayerEvent.StateChanged event) {
        switch (event.newState) {
            case BUFFERING:
                playerState = PlayerState.BUFFERING;
                //We should start count buffering time only after IMPRESSION was sent.
                if (isImpressionSent) {
                    dataHandler.handleBufferingStart();
                }
                break;
            case READY:
                playerState = PlayerState.READY;
                dataHandler.handleBufferingEnd();
                break;
        }
    }

    private void sendAnalyticsEvent(final KavaEvents event) {
        if (!pluginConfig.isPartnerIdValid()) {
            log.w("Can not send analytics event. Mandatory field partnerId is missing");
            return;
        }
        if (mediaConfig == null || mediaConfig.getMediaEntry() == null || mediaConfig.getMediaEntry().getId() == null) {
            log.w("Can not send analytics event. Mandatory field entryId is missing");
            return;
        }

        Map<String, String> params = dataHandler.collectData(event);

        RequestBuilder requestBuilder = KavaService.sendAnalyticsEvent(pluginConfig.getBaseUrl(), dataHandler.getUserAgent(), params);
        requestBuilder.completion(new OnRequestCompletion() {
            @Override
            public void onComplete(ResponseElement response) {
                log.d("onComplete: " + event.name());
                //TODO obtain from server flag that tells if view events should be enabled or not(when server be able to send that)
                dataHandler.setSessionStartTime(response);
                messageBus.post(new KavaAnalyticsEvent.KavaAnalyticsReport(event.name()));
            }
        });
        log.d("request sent " + requestBuilder.build().getUrl());
        requestExecutor.queue(requestBuilder.build());
    }

    private KavaAnalyticsConfig parsePluginConfig(Object config) {
        if (config instanceof KavaAnalyticsConfig) {
            return (KavaAnalyticsConfig) config;
        } else if (config instanceof JsonObject) {
            return new Gson().fromJson((JsonObject) config, KavaAnalyticsConfig.class);
        }
        // If no config passed, create default one.
        return new KavaAnalyticsConfig();
    }

    private void maybeSentPlayerReachedEvent() {
        if (player.isLive()) {
            return;
        }

        float progress = (float) player.getCurrentPosition() / player.getDuration();

        if (progress < 0.25) {
            return;
        }

        if (!playReached25) {
            playReached25 = true;
            sendAnalyticsEvent(KavaEvents.PLAY_REACHED_25_PERCENT);
        }

        if (!playReached50 && progress >= 0.5) {
            playReached50 = true;
            sendAnalyticsEvent(KavaEvents.PLAY_REACHED_50_PERCENT);
        }

        if (!playReached75 && progress >= 0.75) {
            playReached75 = true;
            sendAnalyticsEvent(KavaEvents.PLAY_REACHED_75_PERCENT);
        }

    }

    private void setIsPaused(boolean isPaused) {
        this.isPaused = isPaused;
        if (isPaused) {
            viewTimer.pause();
        } else {
            viewTimer.resume();
        }
    }

    private void resetFlags() {
        setIsPaused(true);
        isEnded = false;
        isFirstPlay = true;
        playReached25 = playReached50 = playReached75 = playReached100 = false;
    }

    private ViewTimer.ViewEventTrigger initViewTrigger() {
        return new ViewTimer.ViewEventTrigger() {
            @Override
            public void onTriggerViewEvent() {
                //When we send VIEW event, while player is buffering we should
                //manually update buffer time. So we will simulate handleBufferEnd()
                if (playerState == PlayerState.BUFFERING) {
                    dataHandler.handleBufferingEnd();
                }
                sendAnalyticsEvent(KavaEvents.VIEW);
            }

            @Override
            public void onResetViewEvent() {
                dataHandler.handleViewEventSessionClosed();
            }

            @Override
            public void onTick() {
                maybeSentPlayerReachedEvent();
            }
        };
    }
}
