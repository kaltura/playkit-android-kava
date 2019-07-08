package com.kaltura.playkit.plugins.kava;

import java.util.LinkedHashMap;

/**
 * Created by anton.afanasiev on 18/02/2018.
 */

class OptionalParams {

    private LinkedHashMap<String, String> optionalParams;

    OptionalParams(KavaAnalyticsConfig config) {
        optionalParams = new LinkedHashMap<>();

        if (config.getPlaybackContext() != null) {
            optionalParams.put("playbackContext", config.getPlaybackContext());
        }

        if (config.getCustomVar1() != null) {
            optionalParams.put("customVar1", config.getCustomVar1());
        }

        if (config.getCustomVar2() != null) {
            optionalParams.put("customVar2", config.getCustomVar2());
        }

        if (config.getCustomVar3() != null) {
            optionalParams.put("customVar3", config.getCustomVar3());
        }

        if (config.getKs() != null) {
            optionalParams.put("ks", config.getKs());
        }

        if (config.getUiConfId() != null && config.getUiConfId() != 0) {
            optionalParams.put("uiConfId", Integer.toString(config.getUiConfId()));
        }

        if(config.getApplicationVersion() != null) {
            optionalParams.put("applicationVer", config.getApplicationVersion());
        }

        if(config.getPlaylistId() != null) {
            optionalParams.put("playlistId", config.getPlaylistId());
        }
    }

    LinkedHashMap<String, String> getParams() {
        return optionalParams;
    }
}
