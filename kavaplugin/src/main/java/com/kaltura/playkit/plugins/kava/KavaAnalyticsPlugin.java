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
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kaltura.netkit.connect.executor.APIOkRequestsExecutor;
import com.kaltura.netkit.connect.executor.RequestQueue;
import com.kaltura.netkit.connect.request.RequestBuilder;
import com.kaltura.netkit.connect.response.ResponseElement;
import com.kaltura.netkit.utils.OnRequestCompletion;
import com.kaltura.playkit.MessageBus;
import com.kaltura.playkit.PKError;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKPlugin;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.PlayerState;

import com.kaltura.playkit.plugin.kava.BuildConfig;
import com.kaltura.playkit.utils.Consts;

import org.json.JSONException;
import org.json.JSONObject;

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

    private PlayerEvent.PlayheadUpdated playheadUpdated;
    private boolean playReached25;
    private boolean playReached50;
    private boolean playReached75;
    private boolean playReached100;

    private boolean isAutoPlay;
    private boolean isImpressionSent;
    private boolean isBufferingStart;
    private boolean isEnded = false;
    private boolean isPaused = true;
    private boolean isFirstPlay = true;

    private ViewTimer viewTimer;
    private ViewTimer.ViewEventTrigger viewEventTrigger = initViewTrigger();
    private long applicationBackgroundTimeStamp;


    public static final Factory factory = new Factory() {
        @Override
        public String getName() {
            return "kava";
        }

        @Override
        public PKPlugin newInstance() {
            return new KavaAnalyticsPlugin();
        }

        @Override
        public String getVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @Override
        public void warmUp(Context context) {

        }
    };

    @Override
    protected void onLoad(Player player, Object config, MessageBus messageBus, Context context) {
        log.d("onLoad");
        this.player = player;
        this.messageBus = messageBus;
        this.requestExecutor = APIOkRequestsExecutor.getSingleton();
        addListeners();
        dataHandler = new DataHandler(context, player);
        onUpdateConfig(config);
    }

    private void addListeners() {
        messageBus.addListener(this, PlayerEvent.stateChanged, event -> {
            handleStateChanged(event);
        });

        this.messageBus.addListener(this, PlayerEvent.canPlay, event -> {
            if (isFirstPlay) {
                dataHandler.handleCanPlay();
            }
        });

        messageBus.addListener(this, PlayerEvent.loadedMetadata, event -> {
            if (!isImpressionSent) {
                sendAnalyticsEvent(KavaEvents.IMPRESSION);
                dataHandler.handleLoadedMetaData();
                if (isAutoPlay) {
                    sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
                    isAutoPlay = false;
                }
                isImpressionSent = true;
            }
        });

        messageBus.addListener(this, PlayerEvent.play, event -> {
            if (isFirstPlay) {
                dataHandler.handleFirstPlay();
            }
            if (isImpressionSent && (isFirstPlay || !isPaused)) {
                sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
            } else {
                isAutoPlay = true;
            }
        });

        messageBus.addListener(this, PlayerEvent.pause, event -> {
            setIsPaused(true);
            sendAnalyticsEvent(KavaEvents.PAUSE);
        });

        messageBus.addListener(this, PlayerEvent.playing, event -> {
            if (isFirstPlay) {
                isFirstPlay = false;
                startViewTimer();
                sendAnalyticsEvent(KavaEvents.PLAY);
            } else {
                if (isPaused && !isEnded) {
                    sendAnalyticsEvent(KavaEvents.RESUME);
                }
            }
            isEnded = false; // needed in order to prevent sending of RESUME event after REPLAY.
            setIsPaused(false);
        });

        messageBus.addListener(this, PlayerEvent.seeking, event -> {
            PKMediaEntry.MediaEntryType mediaEntryType = PKMediaEntry.MediaEntryType.Unknown;
            if (mediaConfig != null && mediaConfig.getMediaEntry() != null) {
                mediaEntryType = mediaConfig.getMediaEntry().getMediaType();
            }
            if(isFirstPlay && (PKMediaEntry.MediaEntryType.DvrLive.equals(mediaEntryType)|| PKMediaEntry.MediaEntryType.DvrLive.equals(mediaEntryType))) {
                return;
            }
            dataHandler.handleSeek(event);
            sendAnalyticsEvent(KavaEvents.SEEK);
        });

        messageBus.addListener(this, PlayerEvent.replay, event -> {
            sendAnalyticsEvent(KavaEvents.REPLAY);
        });

        messageBus.addListener(this, PlayerEvent.sourceSelected, event -> {
            dataHandler.handleSourceSelected(event);
        });

        messageBus.addListener(this, PlayerEvent.ended, event -> {
            maybeSentPlayerReachedEvent();
            if (!playReached100) {
                playReached100 = true;
                sendAnalyticsEvent(KavaEvents.PLAY_REACHED_100_PERCENT);
            }

            isEnded = true;
            setIsPaused(true);
        });

        messageBus.addListener(this, PlayerEvent.playbackInfoUpdated, event -> {
            if (dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_VIDEO)) {
                sendAnalyticsEvent(KavaEvents.FLAVOR_SWITCHED);
            }
        });

        messageBus.addListener(this, PlayerEvent.tracksAvailable, event -> {
            dataHandler.handleTracksAvailable(event);
        });

        messageBus.addListener(this, PlayerEvent.videoTrackChanged, event -> {
            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_VIDEO);
            sendAnalyticsEvent(KavaEvents.SOURCE_SELECTED);
        });

        messageBus.addListener(this, PlayerEvent.audioTrackChanged, event -> {
            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_AUDIO);
            sendAnalyticsEvent(KavaEvents.AUDIO_SELECTED);
        });

        messageBus.addListener(this, PlayerEvent.textTrackChanged, event -> {
            dataHandler.handleTrackChange(event, Consts.TRACK_TYPE_TEXT);
            sendAnalyticsEvent(KavaEvents.CAPTIONS);
        });

        messageBus.addListener(this, PlayerEvent.error, event -> {
            PKError error =  event.error;
            if (error != null && !error.isFatal()) {
                log.v("Error eventType = " + error.errorType + " severity = " + error.severity + " errorMessage = " + error.message);
                return;
            }
            dataHandler.handleError(event);
            sendAnalyticsEvent(KavaEvents.ERROR);
        });

        messageBus.addListener(this, PlayerEvent.playheadUpdated, event -> {
            playheadUpdated = event;
            //log.d("playheadUpdated event  position = " + playheadUpdated.position + " duration = " + playheadUpdated.duration);
            maybeSentPlayerReachedEvent();
        });
    }

    @Override
    protected void onUpdateMedia(PKMediaConfig mediaConfig) {
        log.d("onUpdateMedia");
        this.mediaConfig = mediaConfig;
        clearViewTimer();
        viewTimer = new ViewTimer();
        viewTimer.setViewEventTrigger(viewEventTrigger);
        if (pluginConfig != null && this.pluginConfig.getPartnerId() == Consts.DEFAULT_KAVA_PARTNER_ID) {
            this.pluginConfig.setEntryId(Consts.DEFAULT_KAVA_ENTRY_ID);
        }
        dataHandler.onUpdateMedia(mediaConfig, pluginConfig);
        resetFlags();
    }

    @Override
    protected void onUpdateConfig(Object config) {
        this.pluginConfig = parsePluginConfig(config);
        dataHandler.onUpdateConfig(pluginConfig);
    }

    @Override
    protected void onApplicationPaused() {
        log.d("onApplicationPaused");

        applicationBackgroundTimeStamp = System.currentTimeMillis();
        if (dataHandler != null) {
            PKMediaEntry.MediaEntryType mediaEntryType = PKMediaEntry.MediaEntryType.Unknown;
            if (mediaConfig != null && mediaConfig.getMediaEntry() != null) {
                mediaEntryType = mediaConfig.getMediaEntry().getMediaType();
            }
            dataHandler.onApplicationPaused(mediaEntryType);
        }
        if (viewTimer != null) {
            viewTimer.setViewEventTrigger(null);
            viewTimer.stop();
        }
    }

    @Override
    protected void onApplicationResumed() {
        log.d("onApplicationResumed");

        long currentTimeInSeconds = System.currentTimeMillis() - applicationBackgroundTimeStamp;
        if (dataHandler != null) {
            if (currentTimeInSeconds >= ViewTimer.MAX_ALLOWED_VIEW_IDLE_TIME) {
                dataHandler.handleViewEventSessionClosed();
            }
            dataHandler.setOnApplicationResumed();
        }
        startViewTimer();
    }

    private void startViewTimer() {
        if (viewTimer != null) {
            viewTimer.setViewEventTrigger(viewEventTrigger);
            viewTimer.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (messageBus != null) {
            messageBus.removeListeners(this);
        }
        clearViewTimer();
    }

    private void clearViewTimer() {
        if (viewTimer != null) {
            viewTimer.setViewEventTrigger(null);
            viewTimer.stop();
            viewTimer = null;
        }
    }

    private void handleStateChanged(PlayerEvent.StateChanged event) {
        switch (event.newState) {
            case BUFFERING:
                playerState = PlayerState.BUFFERING;
                //We should start count buffering time only after IMPRESSION was sent.
                if (isImpressionSent) {
                    dataHandler.handleBufferingStart();
                    sendAnalyticsEvent(KavaEvents.BUFFER_START);
                    isBufferingStart = true;
                }
                break;
            case READY:
                playerState = PlayerState.READY;
                dataHandler.handleBufferingEnd();
                if (isBufferingStart) {
                    sendAnalyticsEvent(KavaEvents.BUFFER_END);
                    isBufferingStart = false;
                }
                break;
        }
    }

    private void sendAnalyticsEvent(final KavaEvents event) {
        if (!pluginConfig.isPartnerIdValid()) {
            log.w("Can not send analytics event. Mandatory field partnerId is missing");
            return;
        }

        if (!isValidEntryId()) {
            return;
        }

        Map<String, String> params = dataHandler.collectData(event, mediaConfig.getMediaEntry().getMediaType(), playheadUpdated);

        RequestBuilder requestBuilder = KavaService.sendAnalyticsEvent(pluginConfig.getBaseUrl(), dataHandler.getUserAgent(), params);
        requestBuilder.completion(new OnRequestCompletion() {
            @Override
            public void onComplete(ResponseElement response) {
                log.d("onComplete: " + event.name());
                try {
                    if(response == null || response.getResponse() == null) {
                        log.w("Kava event response is null");
                        return;
                    }
                    //If response is in Json format, handle it and update required values.
                    JSONObject jsonObject = new JSONObject(response.getResponse());
                    dataHandler.setSessionStartTime(jsonObject.optString("time"));
                    if (viewTimer != null) {
                        viewTimer.setViewEventsEnabled(jsonObject.optBoolean("viewEventsEnabled", true));
                    }
                } catch (JSONException e) {
                    //If no, exception thrown, we will treat response as String format.
                    if (response.getResponse() != null) {
                        dataHandler.setSessionStartTime(response.getResponse());
                    }
                }

                messageBus.post(new KavaAnalyticsEvent.KavaAnalyticsReport(event.name()));
            }
        });
        log.d("request sent " + requestBuilder.build().getUrl());
        requestExecutor.queue(requestBuilder.build());
    }

    private boolean isValidEntryId() {

        if (mediaConfig == null || mediaConfig.getMediaEntry() == null) {
            return false;
        }

        boolean mediaEntryValid = true;
        if (mediaConfig.getMediaEntry().getId() == null) {
            log.w("Can not send analytics event. Mandatory field entryId is missing");
            mediaEntryValid = false;
        } else {
            // for OTT assetId is not valid for Kava
            mediaEntryValid = mediaEntryValid && !TextUtils.isDigitsOnly(mediaConfig.getMediaEntry().getId());
        }

        boolean metadataVaild = true;
        if ((pluginConfig == null || TextUtils.isEmpty(pluginConfig.getEntryId())) && !isEntryIdInMetadata()) {
            log.w("Can not send analytics event. Mandatory field entryId is missing");
            metadataVaild = false;
        }

        return mediaEntryValid || metadataVaild;
    }

    private boolean isEntryIdInMetadata() {
        return (mediaConfig != null && mediaConfig.getMediaEntry() != null &&
                mediaConfig.getMediaEntry().getMetadata() != null) &&
                mediaConfig.getMediaEntry().getMetadata().containsKey("entryId") &&
                !TextUtils.isEmpty(mediaConfig.getMediaEntry().getMetadata().get("entryId"));
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
        float progress = 0f;
        if (playheadUpdated != null && playheadUpdated.position >= 0 && playheadUpdated.duration > 0) {
            progress = (float) playheadUpdated.position / playheadUpdated.duration;
        }
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
        if (viewTimer != null) {
            if (isPaused) {
                viewTimer.pause();
            } else {
                viewTimer.resume();
            }
        }
    }

    private void resetFlags() {
        setIsPaused(true);
        isEnded = false;
        isFirstPlay = true;
        isImpressionSent = false;
        isBufferingStart = false;
        playReached25 = playReached50 = playReached75 = playReached100 = false;
        playheadUpdated = null;
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
            public void onTick() {}
        };
    }
}
