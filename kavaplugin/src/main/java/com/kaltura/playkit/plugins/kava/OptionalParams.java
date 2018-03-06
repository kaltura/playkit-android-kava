package com.kaltura.playkit.plugins.kava;

import java.util.LinkedHashMap;

/**
 * Created by anton.afanasiev on 18/02/2018.
 */

class OptionalParams {

    private LinkedHashMap<String, String> optionalParams;

    OptionalParams(KavaAnalyticsConfig config) {
        optionalParams = new LinkedHashMap<>();

        if (config.hasPlaybackContext()) {
            optionalParams.put("playbackContext", config.getPlaybackContext());
        }

        if (config.hasCustomVar1()) {
            optionalParams.put("customVar1", config.getCustomVar1());
        }

        if (config.hasCustomVar2()) {
            optionalParams.put("customVar2", config.getCustomVar2());
        }

        if (config.hasCustomVar3()) {
            optionalParams.put("customVar3", config.getCustomVar3());
        }

        if (config.hasKs()) {
            optionalParams.put("ks", config.getKs());
        }

        if (config.hasUiConfId()) {
            optionalParams.put("uiConfId", Integer.toString(config.getUiConfId()));
        }

        if(config.hasApplicationVersion()) {
            optionalParams.put("applicationVer", config.getApplicationVersion());
        }

        if(config.hasPlaylistId()) {
            optionalParams.put("playlistId", config.getPlaylistId());
        }
    }

    LinkedHashMap<String, String> getParams() {
        return optionalParams;
    }
}
