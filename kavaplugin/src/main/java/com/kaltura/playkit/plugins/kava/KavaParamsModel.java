package com.kaltura.playkit.plugins.kava;

import android.content.Context;
import android.util.Log;

import com.kaltura.netkit.connect.response.ResponseElement;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.Utils;
import com.kaltura.playkit.mediaproviders.base.FormatsHelper;
import com.kaltura.playkit.utils.Consts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by anton.afanasiev on 31/01/2018.
 */

public class KavaParamsModel {

    private Context context;

    private int errorCode;
    private int eventIndex;
    private int totalBufferTimePerViewEvent;

    private long playTimeSum;
    private long actualBitrate;
    private long joinTimeStartTimestamp;
    private long totalBufferTimePerEntry;
    private long lastKnownBufferingTimestamp;
    private long targetSeekPositionInSeconds;

    private String entryId;
    private String referrer;
    private String sessionId;
    private String partnerId;
    private String deliveryType;
    private String playbackType;
    private String playerPosition;
    private String sessionStartTime;
    private String currentAudioLanguage;
    private String currentCaptionLanguage;

    private AverageBitrateCounter averageBitrateCounter;
    private LinkedHashMap<String, String> optionalParams;


    KavaParamsModel(Context context) {
        this.context = context;
    }

    Map<String, String> getParams(KavaEvents event) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("service", "analytics");
        params.put("action", "trackEvent");
        params.put("eventType", Integer.toString(event.getValue()));
        params.put("partnerId", partnerId);
        params.put("entryId", entryId);
        params.put("sessionId", sessionId);
        params.put("eventIndex", Integer.toString(eventIndex));
        params.put("referrer", referrer);
        params.put("deliveryType", deliveryType);
        params.put("playbackType", playbackType);
        params.put("clientVer", PlayKitManager.CLIENT_TAG);
        params.put("clientTag", PlayKitManager.CLIENT_TAG);
        params.put("position", playerPosition);

        if (sessionStartTime != null) {
            params.put("sessionStartTime", sessionStartTime);
        }

        switch (event) {

            case VIEW:

                params.put("actualBitrate", Long.toString(actualBitrate));

                long averageBitrate = averageBitrateCounter.getAverageBitrate(playTimeSum + totalBufferTimePerEntry);
                params.put("averageBitrate", Long.toString(averageBitrate));

                playTimeSum += KavaAnalyticsPlugin.TEN_SECONDS_IN_MS - totalBufferTimePerViewEvent;
                params.put("playTimeSum", Float.toString(playTimeSum / Consts.MILLISECONDS_MULTIPLIER_FLOAT));

                addBufferParams(params);

                break;
            case PLAY:
            case RESUME:
                if (event == KavaEvents.PLAY) {
                    float joinTime = (System.currentTimeMillis() - joinTimeStartTimestamp) / Consts.MILLISECONDS_MULTIPLIER_FLOAT;
                    params.put("joinTime", Float.toString(joinTime));
                }
                averageBitrateCounter.resumeCounting();
                addBufferParams(params);
                break;
            case SEEK:
                params.put("targetPosition", Float.toString(targetSeekPositionInSeconds));
                break;
            case SOURCE_SELECTED:
            case FLAVOR_SWITCHED:
                params.put("actualBitrate", Long.toString(actualBitrate));
                break;
            case AUDIO_SELECTED:
                params.put("language", currentAudioLanguage);
                break;
            case CAPTIONS:
                params.put("caption", currentCaptionLanguage);
                break;
            case ERROR:
                if (errorCode != -1) {
                    params.put("errorCode", Integer.toString(errorCode));
                    errorCode = -1;
                }
                break;
            case PAUSE:
                //When player was paused we should update average bitrate value,
                //because we are interested in average bitrate only during active playback.
                averageBitrateCounter.pauseCounting();
                break;
        }

        params.putAll(optionalParams);
        eventIndex++;
        return params;
    }

    private void addBufferParams(Map<String, String> params) {

        float curBufferTimeInSeconds = totalBufferTimePerViewEvent == 0 ? 0 : totalBufferTimePerViewEvent / Consts.MILLISECONDS_MULTIPLIER_FLOAT;
        float totalBufferTimeInSeconds = totalBufferTimePerEntry == 0 ? 0 : totalBufferTimePerEntry / Consts.MILLISECONDS_MULTIPLIER_FLOAT;

        params.put("bufferTime", Float.toString(curBufferTimeInSeconds));
        params.put("bufferTimeSum", Float.toString(totalBufferTimeInSeconds));


        //View event is sent, so reset totalBufferTimePerViewEvent to 0.
        totalBufferTimePerViewEvent = 0;
    }

    void onUpdateConfig(KavaAnalyticsConfig pluginConfig) {
        partnerId = Integer.toString(pluginConfig.getPartnerId());
        referrer = pluginConfig.getReferrer();
        if (referrer == null) {
            this.referrer = buildDefaultReferrer();
        } else {
            this.referrer = Utils.toBase64(referrer.getBytes());
        }
        buildOptionalParams(pluginConfig);
    }

    void onUpdateMedia(PKMediaConfig mediaConfig, String sessionId) {
        averageBitrateCounter = new AverageBitrateCounter();
        this.sessionId = sessionId;
        this.entryId = mediaConfig.getMediaEntry().getId();
        eventIndex = 1;
        sessionStartTime = null;
        errorCode = -1;
        playTimeSum = 0;
        actualBitrate = -1;
        lastKnownBufferingTimestamp = 0;
        totalBufferTimePerEntry = 0;
        totalBufferTimePerViewEvent = 0;
        actualBitrate = 0;

    }

    private String buildDefaultReferrer() {
        String referrer = "app://" + context.getPackageName();
        return Utils.toBase64(referrer.getBytes());
    }

    private void buildOptionalParams(KavaAnalyticsConfig pluginConfig) {

        optionalParams = new LinkedHashMap<>();

        if (pluginConfig.hasPlaybackContext()) {
            optionalParams.put("playbackContext", pluginConfig.getPlaybackContext());
        }

        if (pluginConfig.hasCustomVar1()) {
            optionalParams.put("customVar1", pluginConfig.getCustomVar1());
        }

        if (pluginConfig.hasCustomVar2()) {
            optionalParams.put("customVar2", pluginConfig.getCustomVar2());
        }

        if (pluginConfig.hasCustomVar3()) {
            optionalParams.put("customVar3", pluginConfig.getCustomVar3());
        }

        if (pluginConfig.hasKs()) {
            optionalParams.put("ks", pluginConfig.getKs());
        }

        if (pluginConfig.hasUiConfId()) {
            optionalParams.put("uiConfId", Integer.toString(pluginConfig.getUiConfId()));
        }
    }

    void updateDeliveryType(PKMediaFormat mediaFormat) {
        if (mediaFormat == PKMediaFormat.dash) {
            deliveryType = FormatsHelper.StreamFormat.MpegDash.formatName;
        } else if (mediaFormat == PKMediaFormat.hls) {
            deliveryType = FormatsHelper.StreamFormat.AppleHttp.formatName;
        } else {
            deliveryType = FormatsHelper.StreamFormat.Url.formatName;
        }
    }

    void updatePlaybackType(String playbackType) {
        this.playbackType = playbackType;
    }

    void updatePlayerPosition(String playerPosition) {
        this.playerPosition = playerPosition;
    }

    void setSessionStartTime(ResponseElement response) {
        if (sessionStartTime == null && response.getResponse() != null) {
            sessionStartTime = response.getResponse();
        }
    }

    void setSeekTarget(long seekTarget) {
        this.targetSeekPositionInSeconds = seekTarget / Consts.MILLISECONDS_MULTIPLIER;
    }

    void setActualBitrate(long videoBitrate) {
        this.actualBitrate = videoBitrate;
        averageBitrateCounter.setBitrate(videoBitrate);
    }

    long getActualBitrate() {
        return actualBitrate;
    }

    void setCurrentAudioLanguage(String currentAudioLanguage) {
        this.currentAudioLanguage = currentAudioLanguage;
    }

    void setCurrentCaptionsLanguage(String currentCaptionLanguage) {
        this.currentCaptionLanguage = currentCaptionLanguage;
    }

    void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    void updateBufferTime() {
        if (lastKnownBufferingTimestamp == 0) return;
        long currentTime = System.currentTimeMillis();
        long bufferTime = currentTime - lastKnownBufferingTimestamp;
        totalBufferTimePerViewEvent += bufferTime;
        totalBufferTimePerEntry += bufferTime;
        lastKnownBufferingTimestamp = currentTime;
        Log.e("TAG1", "updateBufferTime " + lastKnownBufferingTimestamp);
    }

    void updateJoinTimestamp() {
        joinTimeStartTimestamp = System.currentTimeMillis();
    }

    void updateLastKnownBufferingTimestamp() {
        lastKnownBufferingTimestamp = System.currentTimeMillis();
    }

}
