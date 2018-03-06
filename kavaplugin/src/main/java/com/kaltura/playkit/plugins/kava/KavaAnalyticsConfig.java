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

    private static final String KS = "ks";
    private static final String BASE_URL = "baseUrl";
    private static final String UICONF_ID = "uiconfId";
    private static final String PARTNER_ID = "partnerId";
    private static final String CUSTOM_VAR_1 = "customVar1";
    private static final String CUSTOM_VAR_2 = "customVar2";
    private static final String CUSTOM_VAR_3 = "customVar3";
    private static final String REFERRER = "referrerAsBase64";
    private static final String DVR_THRESHOLD = "dvrThreshold";
    private static final String PLAYBACK_CONTEXT = "playbackContext";

    private static final String DEFAULT_BASE_URL = "http://analytics.kaltura.com/api_v3/index.php";

    private int uiconfId;
    private int partnerId;

    private String ks;
    private String referrer;
    private String playlistId;
    private String playbackContext;
    private String applicationVersion;
    private String baseUrl = DEFAULT_BASE_URL;
    private String customVar1, customVar2, customVar3;

    private long dvrThreshold = Consts.DISTANCE_FROM_LIVE_THRESHOLD;

    public KavaAnalyticsConfig setUiConfId(int uiConfId) {
        this.uiconfId = uiConfId;
        return this;
    }

    public KavaAnalyticsConfig setPartnerId(int partnerId) {
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

    public KavaAnalyticsConfig setDvrThreshold(long dvrThreshold) {
        this.dvrThreshold = dvrThreshold;
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

    int getUiConfId() {
        return uiconfId;
    }

    int getPartnerId() {
        return partnerId;
    }

    String getKs() {
        return ks;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    long getDvrThreshold() {
        return dvrThreshold;
    }

    String getCustomVar1() {
        return customVar1;
    }

    String getCustomVar2() {
        return customVar2;
    }

    String getCustomVar3() {
        return customVar3;
    }

    String getPlaybackContext() {
        return playbackContext;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    String getReferrer() {
        if (isValidReferrer(referrer)) {
            return this.referrer;
        }
        return null;
    }

    boolean hasPlaybackContext() {
        return playbackContext != null;
    }

    boolean hasCustomVar1() {
        return customVar1 != null;
    }

    boolean hasCustomVar2() {
        return customVar2 != null;
    }

    boolean hasCustomVar3() {
        return customVar3 != null;
    }

    boolean hasPlaylistId() {
        return playlistId != null;
    }

    boolean hasApplicationVersion() {
        return applicationVersion != null;
    }

    boolean hasKs() {
        return ks != null;
    }

    boolean hasUiConfId() {
        return uiconfId != 0;
    }

    private boolean isValidReferrer(String referrer) {
        return referrer != null && (referrer.startsWith("app://") || referrer.startsWith("http://") || referrer.startsWith("https://"));
    }

    boolean isPartnerIdValid() {
        return partnerId != 0;
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(PARTNER_ID, partnerId);
        jsonObject.addProperty(UICONF_ID, uiconfId);
        jsonObject.addProperty(BASE_URL, baseUrl);
        jsonObject.addProperty(DVR_THRESHOLD, dvrThreshold);

        jsonObject.addProperty(KS, ks);
        jsonObject.addProperty(PLAYBACK_CONTEXT, playbackContext);
        jsonObject.addProperty(REFERRER, referrer);
        jsonObject.addProperty(CUSTOM_VAR_1, customVar1);
        jsonObject.addProperty(CUSTOM_VAR_2, customVar2);
        jsonObject.addProperty(CUSTOM_VAR_3, customVar3);

        return jsonObject;
    }
}
