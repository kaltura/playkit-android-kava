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
import com.kaltura.playkit.PKError;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PKPlugin;
import com.kaltura.playkit.PlaybackInfo;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.ads.PKAdErrorType;
import com.kaltura.playkit.api.ovp.services.KavaService;
import com.kaltura.playkit.player.PKPlayerErrorType;
import com.kaltura.playkit.plugin.kava.BuildConfig;
import com.kaltura.playkit.utils.Consts;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by anton.afanasiev on 27/09/2017.
 */

public class KavaAnalyticsPlugin extends PKPlugin {

    private static final PKLog log = PKLog.get(KavaAnalyticsPlugin.class.getSimpleName());
    private static final long ONE_SECOND_IN_MS = 1000;
    static final int TEN_SECONDS_IN_MS = 10000;

    private Player player;
    private Timer viewEventTimer;
    private MessageBus messageBus;
    private PKMediaConfig mediaConfig;
    private KavaParamsModel kavaParams;
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
    private boolean viewEventsEnabled = true;

    private int viewEventTimeCounter;

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
        kavaParams = new KavaParamsModel(context);
        onUpdateConfig(config);
    }

    @Override
    protected void onUpdateMedia(PKMediaConfig mediaConfig) {
        this.mediaConfig = mediaConfig;
        String sessionId = player.getSessionId() != null ? player.getSessionId() : "";
        kavaParams.onUpdateMedia(mediaConfig, sessionId);
        resetFlags();
        resetPlayerReachedFlags();
    }

    @Override
    protected void onUpdateConfig(Object config) {
        this.pluginConfig = parsePluginConfig(config);
        kavaParams.onUpdateConfig(pluginConfig);
    }

    @Override
    protected void onApplicationPaused() {
        isPaused = true;
        stopAnalyticsTimer();
    }

    @Override
    protected void onApplicationResumed() {
        startAnalyticsTimer();
    }

    @Override
    protected void onDestroy() {
        stopAnalyticsTimer();
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
                                kavaParams.updateJoinTimestamp();
                            }
                            if (isImpressionSent) {
                                sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
                            } else {
                                isAutoPlay = true;
                            }
                            break;
                        case PAUSE:
                            isPaused = true;
                            sendAnalyticsEvent(KavaEvents.PAUSE);
                            break;
                        case PLAYING:
                            if (isFirstPlay) {
                                isFirstPlay = false;
                                startAnalyticsTimer();
                                sendAnalyticsEvent(KavaEvents.PLAY);
                            } else {
                                if (isPaused && !isEnded) {
                                    sendAnalyticsEvent(KavaEvents.RESUME);
                                }
                            }
                            isEnded = false; // needed in order to prevent sending of RESUME event after REPLAY.
                            isPaused = false;
                            break;
                        case SEEKING:
                            PlayerEvent.Seeking seekingEvent = (PlayerEvent.Seeking) event;
                            kavaParams.setSeekTarget(seekingEvent.targetPosition);
                            sendAnalyticsEvent(KavaEvents.SEEK);
                            break;
                        case REPLAY:
                            sendAnalyticsEvent(KavaEvents.REPLAY);
                            break;
                        case SOURCE_SELECTED:
                            PKMediaSource selectedSource = ((PlayerEvent.SourceSelected) event).source;
                            kavaParams.updateDeliveryType(selectedSource.getMediaFormat());
                            break;
                        case ENDED:
                            maybeSentPlayerReachedEvent();
                            if (!playReached100) {
                                playReached100 = true;
                                sendAnalyticsEvent(KavaEvents.PLAY_REACHED_100_PERCENT);
                            }

                            isEnded = true;
                            isPaused = true;
                            break;
                        case PLAYBACK_INFO_UPDATED:
                            PlaybackInfo playbackInfo = ((PlayerEvent.PlaybackInfoUpdated) event).playbackInfo;
                            if (kavaParams.getActualBitrate() != playbackInfo.getVideoBitrate()) {
                                kavaParams.setActualBitrate(playbackInfo.getVideoBitrate());
                                sendAnalyticsEvent(KavaEvents.FLAVOR_SWITCHED);
                            }
                            break;
                        case VIDEO_TRACK_CHANGED:
                            PlayerEvent.VideoTrackChanged videoTrackChanged = ((PlayerEvent.VideoTrackChanged) event);
                            kavaParams.setActualBitrate(videoTrackChanged.newTrack.getBitrate());
                            sendAnalyticsEvent(KavaEvents.SOURCE_SELECTED);
                            break;
                        case AUDIO_TRACK_CHANGED:
                            PlayerEvent.AudioTrackChanged audioTrackChanged = (PlayerEvent.AudioTrackChanged) event;
                            kavaParams.setCurrentAudioLanguage(audioTrackChanged.newTrack.getLanguage());
                            sendAnalyticsEvent(KavaEvents.AUDIO_SELECTED);
                            break;
                        case TEXT_TRACK_CHANGED:
                            PlayerEvent.TextTrackChanged textTrackChanged = (PlayerEvent.TextTrackChanged) event;
                            kavaParams.setCurrentCaptionsLanguage(textTrackChanged.newTrack.getLanguage());
                            sendAnalyticsEvent(KavaEvents.CAPTIONS);
                            break;
                        case ERROR:
                            PKError error = ((PlayerEvent.Error) event).error;
                            int errorCode = -1;
                            if (error.errorType instanceof PKPlayerErrorType) {
                                errorCode = ((PKPlayerErrorType) error.errorType).errorCode;
                            } else if (error.errorType instanceof PKAdErrorType) {
                                errorCode = ((PKAdErrorType) error.errorType).errorCode;
                            }
                            log.e("Playback ERROR. errorCode : " + errorCode);
                            kavaParams.setErrorCode(errorCode);
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
                if (isImpressionSent) {
                    kavaParams.updateLastKnownBufferingTimestamp();
                }
                break;
            case READY:
                kavaParams.updateBufferTime();
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

        kavaParams.updatePlaybackType(decideOnPlaybackType(event));
        kavaParams.updatePlayerPosition(Float.toString(player.getCurrentPosition() / Consts.MILLISECONDS_MULTIPLIER_FLOAT));

        Map<String, String> params = kavaParams.getParams(event);

        RequestBuilder requestBuilder = KavaService.sendAnalyticsEvent(pluginConfig.getBaseUrl(), params);
        requestBuilder.completion(new OnRequestCompletion() {
            @Override
            public void onComplete(ResponseElement response) {
                log.d("onComplete: " + event.name());
                //TODO obtain from server flag that tells if view events should be enabled or not(when server be able to send that)
                kavaParams.setSessionStartTime(response);
                messageBus.post(new KavaAnalyticsEvent.KavaAnalyticsReport(event.name()));
            }
        });
        log.d("request sent " + requestBuilder.build().getUrl());
        requestExecutor.queue(requestBuilder.build());
    }

    private void startAnalyticsTimer() {
        if (viewEventTimer != null) {
            viewEventTimeCounter = 0;
            viewEventTimer.cancel();
            viewEventTimer = null;
        }
        viewEventTimer = new Timer();
        viewEventTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isPaused) {
                    maybeSendViewEvent();
                    maybeSentPlayerReachedEvent();
                }
            }
        }, 0, ONE_SECOND_IN_MS);
    }

    private void stopAnalyticsTimer() {
        if (viewEventTimer == null) {
            return;
        }
        viewEventTimer.cancel();
        viewEventTimer = null;
    }

    private void maybeSendViewEvent() {
        if(viewEventsEnabled) {
            viewEventTimeCounter += ONE_SECOND_IN_MS;
            if (viewEventTimeCounter >= TEN_SECONDS_IN_MS) {
                sendAnalyticsEvent(KavaEvents.VIEW);
                viewEventTimeCounter = 0;
            }
        }
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

    private KavaAnalyticsConfig parsePluginConfig(Object config) {
        if (config instanceof KavaAnalyticsConfig) {
            return (KavaAnalyticsConfig) config;
        } else if (config instanceof JsonObject) {
            return new Gson().fromJson((JsonObject) config, KavaAnalyticsConfig.class);
        }
        // If no config passed, create default one.
        return new KavaAnalyticsConfig();
    }

    /**
     * Use metadata playback type in order to decide which playback type is currently active.
     *
     * @param event - KavaEvent type.
     */
    private String decideOnPlaybackType(KavaEvents event) {

        KavaMediaEntryType kavaPlaybackType;
        String metadataPlaybackType = mediaConfig.getMediaEntry().getMediaType().name();

        if (KavaMediaEntryType.Vod.name().equals(metadataPlaybackType)) {
            kavaPlaybackType = KavaMediaEntryType.Vod;
        } else if (PKMediaEntry.MediaEntryType.Live.name().equals(metadataPlaybackType)) {
            kavaPlaybackType = hasDvr() ? KavaMediaEntryType.Dvr : KavaMediaEntryType.Live;
        } else {
            //If there is no playback type in metadata, obtain it from player as fallback.
            if (player == null || event == KavaEvents.ERROR) {
                //If player is null it is impossible to obtain the playbackType, so it will be unknown.
                kavaPlaybackType = KavaMediaEntryType.Unknown;
            } else {
                if (!player.isLive()) {
                    kavaPlaybackType = KavaMediaEntryType.Vod;
                } else {
                    kavaPlaybackType = hasDvr() ? KavaMediaEntryType.Dvr : KavaMediaEntryType.Live;
                }
            }
        }

        return kavaPlaybackType.name().toLowerCase();
    }

    private boolean hasDvr() {
        if (player == null) {
            return false;
        }

        if (player.isLive()) {
            long distanceFromLive = player.getDuration() - player.getCurrentPosition();
            return distanceFromLive >= pluginConfig.getDvrThreshold();
        }
        return false;
    }

    private void resetFlags() {
        isPaused = true;
        isEnded = false;
        isFirstPlay = true;
    }

    private void resetPlayerReachedFlags() {
        playReached25 = playReached50 = playReached75 = playReached100 = false;
    }

    private enum KavaMediaEntryType {
        Vod,
        Live,
        Dvr,
        Unknown
    }

}
