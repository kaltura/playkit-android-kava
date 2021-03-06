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
import com.kaltura.android.exoplayer2.C;
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

import com.kaltura.playkit.player.metadata.PKMetadata;
import com.kaltura.playkit.player.metadata.PKTextInformationFrame;
import com.kaltura.playkit.plugin.kava.BuildConfig;
import com.kaltura.playkit.utils.Consts;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * Created by anton.afanasiev on 27/09/2017.
 */

public class KavaAnalyticsPlugin extends PKPlugin {

    private static final PKLog log = PKLog.get(KavaAnalyticsPlugin.class.getSimpleName());
    private static final String TEXT = "TEXT";

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
    private Boolean isFirstPlay;
    private boolean isFatalError;
    private boolean isLiveMedia;

    private ViewTimer viewTimer;
    private ViewTimer.ViewEventTrigger viewEventTrigger = initViewTrigger();
    private long applicationBackgroundTimeStamp;
    private DecimalFormat decimalFormat;

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
        decimalFormat = new DecimalFormat("#");
        decimalFormat.setMaximumFractionDigits(3);
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
            isLiveMedia = player.isLive();
            if (isFirstPlay == null || isFirstPlay) {
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
            if (isFirstPlay == null) {
                dataHandler.handleFirstPlay();
            }

            if (isImpressionSent && (isFirstPlay == null || !isPaused)) {
                sendAnalyticsEvent(KavaEvents.PLAY_REQUEST);
            } else {
                isAutoPlay = true;
            }
            if (isFirstPlay == null) {
                isFirstPlay = true;
            }
        });

        messageBus.addListener(this, PlayerEvent.pause, event -> {
            setIsPaused(true);
            sendAnalyticsEvent(KavaEvents.PAUSE);
        });

        messageBus.addListener(this, PlayerEvent.playbackRateChanged, event -> {
            dataHandler.handlePlaybackSpeed(event);
            sendAnalyticsEvent(KavaEvents.SPEED);
        });

        messageBus.addListener(this, PlayerEvent.playing, event -> {
            if (isFirstPlay == null || isFirstPlay) {
                isFirstPlay = false;
                sendAnalyticsEvent(KavaEvents.PLAY);
                sendAnalyticsEvent(KavaEvents.VIEW);
                startViewTimer();
            } else {
                if (isPaused && !isEnded) {
                    sendAnalyticsEvent(KavaEvents.RESUME);
                }
            }
            isEnded = false; // needed in order to prevent sending RESUME event after REPLAY.
            setIsPaused(false);
        });

        messageBus.addListener(this, PlayerEvent.seeking, event -> {
            PKMediaEntry.MediaEntryType mediaEntryType = getMediaEntryType();
            if((isFirstPlay == null || isFirstPlay) && (isLiveMedia || PKMediaEntry.MediaEntryType.Live.equals(mediaEntryType)|| PKMediaEntry.MediaEntryType.DvrLive.equals(mediaEntryType))) {
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
            PKMediaEntry.MediaEntryType mediaType = getMediaEntryType();
            boolean isLive = (isLiveMedia || mediaType == PKMediaEntry.MediaEntryType.Live || mediaType == PKMediaEntry.MediaEntryType.DvrLive);
            if (!isLive) {
                maybeSentPlayerReachedEvent();
                if (!playReached100) {
                    playReached100 = true;
                    sendAnalyticsEvent(KavaEvents.PLAY_REACHED_100_PERCENT);
                }
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

        messageBus.addListener(this, PlayerEvent.bytesLoaded, event -> {
            //log.d("bytesLoaded = " + event.trackType + " load time " + event.loadDuration);
            if (C.TRACK_TYPE_VIDEO == event.trackType || C.TRACK_TYPE_DEFAULT == event.trackType) {
                dataHandler.handleSegmentDownloadTime(event);
            } else if (C.TRACK_TYPE_UNKNOWN == event.trackType){
                dataHandler.handleManifestDownloadTime(event);
            }
        });

        messageBus.addListener(this, PlayerEvent.metadataAvailable, event -> {
            log.d("metadataAvailable = " + event.eventType());
            for (PKMetadata pkMetadata : event.metadataList){
                if (pkMetadata instanceof PKTextInformationFrame) {
                    PKTextInformationFrame textFrame = (PKTextInformationFrame) pkMetadata;
                    if (textFrame != null) {
                        if (TEXT.equals(textFrame.id)) {
                            try {
                                if(textFrame.value != null) {
                                    JSONObject textFrameValue = new JSONObject(textFrame.value);
                                    String flavorParamsId = textFrameValue.getString("sequenceId");
                                    //log.d("metadataAvailable Received user text: flavorParamsId = " + flavorParamsId);
                                    dataHandler.handleSequenceId(flavorParamsId); //flavorParamsId = sequenceId from {"timestamp":1573049629312,"sequenceId":"32"}
                                }
                            } catch (JSONException e) {
                                //e.printStackTrace();
                                log.e("Failed to parse the sequenceId from TEXT ID3 frame");
                                return;
                            }
                        }
                    }
                }
            }
        });

        messageBus.addListener(this, PlayerEvent.error, event -> {
            PKError error =  event.error;
            if (error != null && !error.isFatal()) {
                log.v("Error eventType = " + error.errorType + " severity = " + error.severity + " errorMessage = " + error.message);
                return;
            }
            dataHandler.handleError(event, isFirstPlay, player.getCurrentPosition());
            sendAnalyticsEvent(KavaEvents.ERROR);
            if (viewTimer != null) {
                viewTimer.setViewEventTrigger(null);
                viewTimer.stop();
            }
        });
        
        messageBus.addListener(this, PlayerEvent.playheadUpdated, event -> {
            playheadUpdated = event;
            //log.d("playheadUpdated event  position = " + playheadUpdated.position + " duration = " + playheadUpdated.duration);
            PKMediaEntry.MediaEntryType mediaType = getMediaEntryType();
            boolean isLive = (isLiveMedia || mediaType == PKMediaEntry.MediaEntryType.Live || mediaType == PKMediaEntry.MediaEntryType.DvrLive);
            if (!isLive) {
                maybeSentPlayerReachedEvent();
            }
        });

        messageBus.addListener(this, PlayerEvent.connectionAcquired, event -> {
            dataHandler.handleConnectionAcquired(event);
        });

    }

    private PKMediaEntry.MediaEntryType getMediaEntryType() {
        PKMediaEntry.MediaEntryType mediaType = PKMediaEntry.MediaEntryType.Unknown;
        if (mediaConfig != null && mediaConfig.getMediaEntry() != null) {
            mediaType = mediaConfig.getMediaEntry().getMediaType();
        }
        return mediaType;
    }

    @Override
    protected void onUpdateMedia(PKMediaConfig mediaConfig) {
        log.d("onUpdateMedia");
        this.mediaConfig = mediaConfig;
        isLiveMedia = false;
        clearViewTimer();
        dataHandler.onUpdateMedia(mediaConfig, pluginConfig);
        resetFlags();
        viewTimer = new ViewTimer();
        viewTimer.setViewEventTrigger(viewEventTrigger);
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
            PKMediaEntry.MediaEntryType mediaEntryType = getMediaEntryType();
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

        if (isInputInvalid())
            return;

        if (isFatalError) {
            return;
        }
        if (event == KavaEvents.ERROR) {
            isFatalError = true;
        }

        Map<String, String> params = dataHandler.collectData(event, mediaConfig.getMediaEntry().getMediaType(), isLiveMedia, playheadUpdated);

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
                    if (decimalFormat != null) {
                        dataHandler.setSessionStartTime(decimalFormat.format(jsonObject.optDouble("time")));
                    }
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

    private boolean isInputInvalid() {
        if (mediaConfig == null || mediaConfig.getMediaEntry() == null) {
            return true;
        }

        if (!isValidEntryId()) {
            if (dataHandler != null && pluginConfig.getPartnerId() == null && pluginConfig.getEntryId() == null) {
                pluginConfig.setPartnerId(KavaAnalyticsConfig.DEFAULT_KAVA_PARTNER_ID);
                pluginConfig.setEntryId(KavaAnalyticsConfig.DEFAULT_KAVA_ENTRY_ID);
                dataHandler.updatePartnerAndEntryId(KavaAnalyticsConfig.DEFAULT_KAVA_PARTNER_ID, KavaAnalyticsConfig.DEFAULT_KAVA_ENTRY_ID);
            } else {
                return true;
            }
        }

        if (!pluginConfig.isPartnerIdValid()) {
            int ovpPartnerId = getOvpPartnerId(mediaConfig);
            String entryId = dataHandler != null ? dataHandler.populateEntryId(mediaConfig, pluginConfig) : null;
            if (dataHandler != null && ovpPartnerId > 0 && !TextUtils.isEmpty(entryId)) {
                log.d("Getting ovpPartnerId from metadata");
                pluginConfig.setPartnerId(ovpPartnerId);
                pluginConfig.setEntryId(entryId);
                dataHandler.updatePartnerAndEntryId(ovpPartnerId, entryId);
                return false;
            }

            log.w("Can not send analytics event. Mandatory field partnerId is missing");
            return true;
        }
        return false;
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

    private int getOvpPartnerId(PKMediaConfig pkMediaConfig) {
        if (mediaConfig == null) {
            return 0;
        }

        final String kavaPartnerIdKey = "kavaPartnerId";
        int ovpPartnerId = 0;
        if (pkMediaConfig.getMediaEntry() != null && pkMediaConfig.getMediaEntry().getMetadata() != null) {
            if (pkMediaConfig.getMediaEntry().getMetadata().containsKey(kavaPartnerIdKey)) {
                String partnerId = pkMediaConfig.getMediaEntry().getMetadata().get(kavaPartnerIdKey);
                ovpPartnerId = Integer.parseInt(partnerId != null && TextUtils.isDigitsOnly(partnerId) && !TextUtils.isEmpty(partnerId) ? partnerId : "0");
            }
        }
        return ovpPartnerId;
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
        isFirstPlay = null;
        isLiveMedia = false;
        isFatalError = false;
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
