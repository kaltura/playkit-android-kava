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

import com.google.gson.JsonObject;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.utils.Consts;

/**
 * Created by anton.afanasiev on 04/10/2017.
 */

public class KavaAnalyticsConfig {

    private static final PKLog log = PKLog.get(KavaAnalyticsConfig.class.getSimpleName());

    public static final int DEFAULT_KAVA_PARTNER_ID = 2504201;
    public static final String DEFAULT_KAVA_ENTRY_ID = "1_3bwzbc9o";

    public static final String KS = "ks";
    public static final String BASE_URL = "baseUrl";
    public static final String UICONF_ID = "uiconfId";
    public static final String PARTNER_ID = "partnerId";
    public static final String USER_ID = "userId";
    public static final String CUSTOM_VAR_1 = "customVar1";
    public static final String CUSTOM_VAR_2 = "customVar2";
    public static final String CUSTOM_VAR_3 = "customVar3";
    public static final String APPLICATION_VERSION = "applicationVersion";
    public static final String PLAY_LIST_ID = "playlistId";
    public static final String REFERRER = "referrer";
    public static final String DVR_THRESHOLD = "dvrThreshold";
    public static final String PLAYBACK_CONTEXT = "playbackContext";
    public static final String ENTRY_ID = "entryId";
    public static final String VIRTUAL_EVENT_ID = "virtualEventId";
    public static final String DEFAULT_BASE_URL = "https://analytics.kaltura.com/api_v3/index.php";

    private Integer uiconfId;
    private Integer partnerId;
    private Integer virtualEventId;

    private String ks;
    private String referrer;
    private String playlistId;
    private String entryId;
    private String playbackContext;
    private String applicationVersion;
    private String baseUrl = DEFAULT_BASE_URL;
    private String userId;
    private String customVar1, customVar2, customVar3;

    private long dvrThreshold = Consts.DISTANCE_FROM_LIVE_THRESHOLD;


    // Expecting here the OVP partner Id even for OTT account
    public KavaAnalyticsConfig setPartnerId(Integer partnerId) {
        this.partnerId = partnerId;
        return this;
    }

    public KavaAnalyticsConfig setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public KavaAnalyticsConfig setKs(String ks) {
        this.ks = ks;
        return this;
    }

    public KavaAnalyticsConfig setEntryId(String entryId) {
        this.entryId = entryId;
        return this;
    }

    public KavaAnalyticsConfig setDvrThreshold(long dvrThreshold) {
        this.dvrThreshold = dvrThreshold;
        return this;
    }

    public KavaAnalyticsConfig setUiConfId(Integer uiConfId) {
        this.uiconfId = uiConfId;
        return this;
    }

    public KavaAnalyticsConfig setVirtualEventId(Integer virtualEventId) {
        this.virtualEventId = virtualEventId;
        return this;
    }

    public KavaAnalyticsConfig setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public KavaAnalyticsConfig setCustomVar1(String customVar1) {
        this.customVar1 = customVar1;
        return this;
    }

    public KavaAnalyticsConfig setCustomVar2(String customVar2) {
        this.customVar2 = customVar2;
        return this;
    }

    public KavaAnalyticsConfig setCustomVar3(String customVar3) {
        this.customVar3 = customVar3;
        return this;
    }

    public KavaAnalyticsConfig setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    public KavaAnalyticsConfig setPlaybackContext(String playbackContext) {
        this.playbackContext = playbackContext;
        return this;
    }

    public KavaAnalyticsConfig setPlaylistId(String playlistId) {
        this.playlistId = playlistId;
        return this;

    }

    public KavaAnalyticsConfig setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
        return this;

    }

    public Integer getUiConfId() {
        return uiconfId;
    }

    public Integer getPartnerId() {
        return partnerId;
    }

    public Integer getVirtualEventId() {
        return virtualEventId;
    }

    public String getKs() {
        return ks;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getBaseUrl() {
        if (baseUrl == null) {
            return DEFAULT_BASE_URL;
        }
        return baseUrl;
    }

    public long getDvrThreshold() {
        return dvrThreshold;
    }

    public String getUserId() {
        return userId;
    }

    public String getCustomVar1() {
        return customVar1;
    }

    public String getCustomVar2() {
        return customVar2;
    }

    public String getCustomVar3() {
        return customVar3;
    }

    public String getPlaybackContext() {
        return playbackContext;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public String getReferrer() {
        if (isValidReferrer(referrer)) {
            return this.referrer;
        }
        return null;
    }

    private boolean isValidReferrer(String referrer) {
        return referrer != null && (referrer.startsWith("app://") || referrer.startsWith("http://") || referrer.startsWith("https://"));
    }

    boolean isPartnerIdValid() {
        return partnerId != null && partnerId > 0;
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(PARTNER_ID, partnerId);
        jsonObject.addProperty(ENTRY_ID, entryId);
        jsonObject.addProperty(VIRTUAL_EVENT_ID, virtualEventId);
        jsonObject.addProperty(BASE_URL, baseUrl);
        jsonObject.addProperty(DVR_THRESHOLD, dvrThreshold);

        jsonObject.addProperty(KS, ks);
        jsonObject.addProperty(PLAYBACK_CONTEXT, playbackContext);
        jsonObject.addProperty(REFERRER, referrer);
        if (uiconfId != null) {
            jsonObject.addProperty(UICONF_ID, uiconfId);
        }
        jsonObject.addProperty(USER_ID, userId);
        jsonObject.addProperty(CUSTOM_VAR_1, customVar1);
        jsonObject.addProperty(CUSTOM_VAR_2, customVar2);
        jsonObject.addProperty(CUSTOM_VAR_3, customVar3);
        jsonObject.addProperty(PLAY_LIST_ID, playlistId);
        jsonObject.addProperty(APPLICATION_VERSION, applicationVersion);

        return jsonObject;
    }
}
