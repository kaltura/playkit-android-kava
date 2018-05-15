package com.kaltura.playkit.plugins.kava;

import android.content.Context;
import android.support.annotation.Nullable;

import com.kaltura.playkit.PKError;
import com.kaltura.playkit.PKEvent;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.PKMediaConfig;
import com.kaltura.playkit.PKMediaEntry;
import com.kaltura.playkit.PKMediaFormat;
import com.kaltura.playkit.PKMediaSource;
import com.kaltura.playkit.PlayKitManager;
import com.kaltura.playkit.PlaybackInfo;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.PlayerEvent;
import com.kaltura.playkit.Utils;
import com.kaltura.playkit.ads.PKAdErrorType;
import com.kaltura.playkit.mediaproviders.base.FormatsHelper;
import com.kaltura.playkit.player.PKPlayerErrorType;
import com.kaltura.playkit.utils.Consts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handle, update and hold all the Kava related data. When needed will provide all the collected information.
 * Created by anton.afanasiev on 31/01/2018.
 */

class DataHandler {

    private static final PKLog log = PKLog.get(DataHandler.class.getSimpleName());

    private Context context;
    private final Player player;

    private int errorCode;
    private int eventIndex;
    private int totalBufferTimePerViewEvent;

    private long playTimeSum;
    private long dvrThreshold;
    private long actualBitrate;
    private long currentPosition;
    private long currentDuration;
    private long joinTimeStartTimestamp;
    private long totalBufferTimePerEntry;
    private long lastKnownBufferingTimestamp;
    private long targetSeekPositionInSeconds;

    private String entryId;
    private String sessionId;
    private String partnerId;
    private String userAgent;
    private String deliveryType;
    private String sessionStartTime;
    private String referrer;
    private String metadataPlaybackType;
    private String currentAudioLanguage;
    private String currentCaptionLanguage;

    private OptionalParams optionalParams;
    private KavaMediaEntryType playbackType;
    private AverageBitrateCounter averageBitrateCounter;

    private boolean onApplicationPaused = false;

    DataHandler(Context context, Player player) {
        this.context = context;
        this.player = player;
        this.userAgent = context.getPackageName() + " " + PlayKitManager.CLIENT_TAG + " " + System.getProperty("http.agent");
    }

    /**
     * Apply plugin configuration values.
     *
     * @param pluginConfig - plugin configurations.
     */
    void onUpdateConfig(KavaAnalyticsConfig pluginConfig) {

        partnerId = Integer.toString(pluginConfig.getPartnerId());
        dvrThreshold = pluginConfig.getDvrThreshold();
        generateReferrer(pluginConfig.getReferrer());
        optionalParams = new OptionalParams(pluginConfig);
    }

    /**
     * Apply media related values.
     *
     * @param mediaConfig  - media configurations.
     * @param pluginConfig - plugin configurations
     */
    void onUpdateMedia(PKMediaConfig mediaConfig, KavaAnalyticsConfig pluginConfig) {

        averageBitrateCounter = new AverageBitrateCounter();

        this.entryId = (pluginConfig != null && pluginConfig.getEntryId() != null) ? pluginConfig.getEntryId() : mediaConfig.getMediaEntry().getId();
        this.sessionId = player.getSessionId() != null ? player.getSessionId() : "";
        this.metadataPlaybackType = (mediaConfig.getMediaEntry().getMediaType() != null) ?
                mediaConfig.getMediaEntry().getMediaType().name() : PKMediaEntry.MediaEntryType.Unknown.name();

        resetValues();
    }

    /**
     * Collect all the event relevant information.
     * Information will be hold in {@link LinkedHashMap} in order to preserve parameters order
     * when sending to server.
     *
     * @param event - current Kava event.
     * @return - Map with all the event relevant information
     */
    Map<String, String> collectData(KavaEvents event) {
        if (!onApplicationPaused) {
            playbackType = decideOnPlaybackType(event);
        }

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
        params.put("playbackType", playbackType.name().toLowerCase());
        params.put("clientVer", PlayKitManager.CLIENT_TAG);
        params.put("position", getPlayerPosition(playbackType));
        params.put("application", context.getPackageName());

        if (sessionStartTime != null) {
            params.put("sessionStartTime", sessionStartTime);
        }

        //Set event specific information.
        switch (event) {

            case VIEW:

                playTimeSum += ViewTimer.TEN_SECONDS_IN_MS - totalBufferTimePerViewEvent;
                params.put("playTimeSum", Float.toString(playTimeSum / Consts.MILLISECONDS_MULTIPLIER_FLOAT));

                params.put("actualBitrate", Long.toString(actualBitrate));
                long averageBitrate = averageBitrateCounter.getAverageBitrate(playTimeSum + totalBufferTimePerEntry);
                params.put("averageBitrate", Long.toString(averageBitrate));

                addBufferParams(params);

                break;
            case PLAY:
            case RESUME:
                params.put("actualBitrate", Long.toString(actualBitrate));
                if (event == KavaEvents.PLAY) {
                    float joinTime = (System.currentTimeMillis() - joinTimeStartTimestamp) / Consts.MILLISECONDS_MULTIPLIER_FLOAT;
                    params.put("joinTime", Float.toString(joinTime));
                }
                averageBitrateCounter.resumeCounting();
                addBufferParams(params);
                break;
            case SEEK:
                params.put("targetPosition", Float.toString(targetSeekPositionInSeconds / Consts.MILLISECONDS_MULTIPLIER_FLOAT));
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
                // each time we stop counting view timer we should reset the sessionStartTimer.
                sessionStartTime = null;
                break;
        }

        params.putAll(optionalParams.getParams());
        eventIndex++;
        return params;
    }


    /**
     * Player track change handler.
     *
     * @param event     - current event.
     * @param trackType - type of the requested track.
     * @return - return true if analytics managed to set newly received track data. Otherwise false.
     */
    boolean handleTrackChange(PKEvent event, int trackType) {
        boolean shouldSendEvent = true;
        switch (trackType) {
            case Consts.TRACK_TYPE_VIDEO:
                if (event instanceof PlayerEvent.PlaybackInfoUpdated) {
                    PlaybackInfo playbackInfo = ((PlayerEvent.PlaybackInfoUpdated) event).playbackInfo;
                    if (actualBitrate == playbackInfo.getVideoBitrate()) {
                        shouldSendEvent = false;
                    } else {
                        this.actualBitrate = playbackInfo.getVideoBitrate();
                    }
                } else {
                    PlayerEvent.VideoTrackChanged videoTrackChanged = (PlayerEvent.VideoTrackChanged) event;
                    this.actualBitrate = videoTrackChanged.newTrack.getBitrate();
                }
                averageBitrateCounter.setBitrate(actualBitrate);
                break;
            case Consts.TRACK_TYPE_AUDIO:
                PlayerEvent.AudioTrackChanged audioTrackChanged = (PlayerEvent.AudioTrackChanged) event;
                currentAudioLanguage = audioTrackChanged.newTrack.getLanguage();
                break;
            case Consts.TRACK_TYPE_TEXT:
                PlayerEvent.TextTrackChanged textTrackChanged = (PlayerEvent.TextTrackChanged) event;
                currentCaptionLanguage = textTrackChanged.newTrack.getLanguage();
                break;
        }

        return shouldSendEvent;
    }

    /**
     * Handle error event.
     *
     * @param event - current event.
     */
    void handleError(PKEvent event) {
        PKError error = ((PlayerEvent.Error) event).error;
        int errorCode = -1;
        if (error.errorType instanceof PKPlayerErrorType) {
            errorCode = ((PKPlayerErrorType) error.errorType).errorCode;
        } else if (error.errorType instanceof PKAdErrorType) {
            errorCode = ((PKAdErrorType) error.errorType).errorCode;
        }
        log.e("Playback ERROR. errorCode : " + errorCode);
        this.errorCode = errorCode;
    }

    /**
     * Handle SourceSelected event. Obtain and update current media format
     * accepted by KAVA.
     *
     * @param event - current event.
     */
    void handleSourceSelected(PKEvent event) {
        PKMediaSource selectedSource = ((PlayerEvent.SourceSelected) event).source;
        if (selectedSource.getMediaFormat() == PKMediaFormat.dash) {
            deliveryType = FormatsHelper.StreamFormat.MpegDash.formatName;
        } else if (selectedSource.getMediaFormat() == PKMediaFormat.hls) {
            deliveryType = FormatsHelper.StreamFormat.AppleHttp.formatName;
        } else {
            deliveryType = FormatsHelper.StreamFormat.Url.formatName;
        }
    }

    /**
     * Handle seek event. Update and cache target position.
     *
     * @param event - current event.
     */
    void handleSeek(PKEvent event) {
        PlayerEvent.Seeking seekingEvent = (PlayerEvent.Seeking) event;
        this.targetSeekPositionInSeconds = seekingEvent.targetPosition;
    }

    /**
     * Handle player buffering state.
     */
    void handleBufferingStart() {
        lastKnownBufferingTimestamp = System.currentTimeMillis();
    }

    /**
     * Called when player has finish buffering (PlayerState = READY). When player goes into this state, we should collect all the
     * buffer related information.
     */
    void handleBufferingEnd() {
        if (lastKnownBufferingTimestamp == 0) return;
        long currentTime = System.currentTimeMillis();
        long bufferTime = currentTime - lastKnownBufferingTimestamp;
        totalBufferTimePerViewEvent += bufferTime;
        totalBufferTimePerEntry += bufferTime;
        lastKnownBufferingTimestamp = currentTime;
    }

    /**
     * Handles first play.
     */
    void handleFirstPlay() {
        joinTimeStartTimestamp = System.currentTimeMillis();
    }

    /**
     * Set view session start time.
     *
     * @param sessionStartTime - sessionStartTime from server.
     */
    void setSessionStartTime(String sessionStartTime) {
        if (this.sessionStartTime == null && !sessionStartTime.isEmpty()) {
            this.sessionStartTime = sessionStartTime;
        }
    }

    /**
     * When VIEW event was not delivered for more then 30 seconds, Kava server will reset
     * VIEW session. So we also have to do the same.
     */
    void handleViewEventSessionClosed() {
        eventIndex = 1;
        playTimeSum = 0;
        sessionStartTime = null;
        totalBufferTimePerEntry = 0;
        totalBufferTimePerViewEvent = 0;
        averageBitrateCounter.reset();
    }

    /**
     * Updates player position based on playbackType. If current playbackType is LIVE or DVR
     * player position will be calculated based on distance from the live edge. Therefore should be 0 or negative value.
     * Otherwise it should be just a real current position.
     *
     * @param mediaEntryType - {@link KavaMediaEntryType} of the media for the moment of sending event.
     */
    private String getPlayerPosition(KavaMediaEntryType mediaEntryType) {
        //When position obtained not from onApplicationPaused state update position/duration.
        if (!onApplicationPaused) {
            currentPosition = player.getCurrentPosition();
            currentDuration = player.getDuration();
        }

        long playerPosition = currentPosition;
        if (mediaEntryType == KavaMediaEntryType.Dvr
                || mediaEntryType == KavaMediaEntryType.Live) {
            playerPosition = currentPosition - currentDuration;
        }

        return playerPosition == 0 ? "0" : Float.toString(playerPosition / Consts.MILLISECONDS_MULTIPLIER_FLOAT);

    }

    /**
     * Add buffer information to the report.
     *
     * @param params - map of current params.
     */
    private void addBufferParams(Map<String, String> params) {

        float curBufferTimeInSeconds = totalBufferTimePerViewEvent == 0 ? 0 : totalBufferTimePerViewEvent / Consts.MILLISECONDS_MULTIPLIER_FLOAT;
        float totalBufferTimeInSeconds = totalBufferTimePerEntry == 0 ? 0 : totalBufferTimePerEntry / Consts.MILLISECONDS_MULTIPLIER_FLOAT;

        params.put("bufferTime", Float.toString(curBufferTimeInSeconds));
        params.put("bufferTimeSum", Float.toString(totalBufferTimeInSeconds));

        //View event is sent, so reset totalBufferTimePerViewEvent to 0.
        totalBufferTimePerViewEvent = 0;
    }

    /**
     * Make a decision what playbackType is active now. First we will try to decide it base on
     * information provided in {@link KavaAnalyticsConfig}. If for some reason there is no relevant information found,
     * we will rely on player to provide this data.
     * In case when current event of type ERROR - we will concern it as KavaMediaEntryType.Unknown.
     *
     * @param event - KavaEvent type.
     * @return - {@link KavaMediaEntryType} of the media for the moment of sending event.
     */
    private KavaMediaEntryType decideOnPlaybackType(KavaEvents event) {

        KavaMediaEntryType kavaPlaybackType;

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
                if (!player.isLiveStream()) {
                    kavaPlaybackType = KavaMediaEntryType.Vod;
                } else {
                    kavaPlaybackType = hasDvr() ? KavaMediaEntryType.Dvr : KavaMediaEntryType.Live;
                }
            }
        }

        return kavaPlaybackType;
    }

    /**
     * Chceck if current playback state is in LIVE or DVR mode.
     *
     * @return - true if distance from live edge grater the requested dvr threshold.
     */
    private boolean hasDvr() {
        if (player == null) {
            return false;
        }

        if (player.isLiveStream()) {
            long distanceFromLive = player.getDuration() - player.getCurrentPosition();
            return distanceFromLive >= dvrThreshold;
        }
        return false;
    }

    /**
     * If provided referrer is null, it will
     * build default one.
     *
     * @param referrer - Custom referrer to set, or null if should use default one.
     */
    private void generateReferrer(@Nullable String referrer) {
        //If not exist generate default one.
        if (referrer == null) {
            referrer = buildDefaultReferrer();
        }

        this.referrer = referrer;
    }

    /**
     * Build default referrer, will look something like that "app://com.kalura.playkitapplication
     *
     * @return - default referrer.
     */
    private String buildDefaultReferrer() {
        return "app://" + context.getPackageName();
    }

    /**
     * Reset all the values for the default.
     * Should happen only once per media entry.
     */
    private void resetValues() {
        errorCode = -1;
        actualBitrate = -1;
        sessionStartTime = null;
        onApplicationPaused = false;
        lastKnownBufferingTimestamp = 0;
        handleViewEventSessionClosed();
    }

    /**
     * @return - user agent value build from application id + playkit version + systems userAgent
     */
    String getUserAgent() {
        return userAgent;
    }

    void onApplicationPaused() {
        //Player is destroyed during onApplicationPaused call.
        //So we should update this values before PAUSE event sent.
        currentDuration = player.getDuration();
        currentPosition = player.getCurrentPosition();
        playbackType = decideOnPlaybackType(null);
        onApplicationPaused = true;
    }

    void setOnApplicationResumed() {
        onApplicationPaused = false;
    }
}
