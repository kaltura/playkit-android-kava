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

import android.net.Uri;

import com.kaltura.netkit.connect.request.RequestBuilder;

import java.util.Map;

import static com.kaltura.playkit.utils.Consts.HTTP_METHOD_GET;

/**
 * Created by anton.afanasiev on 02/10/2017.
 */

public class KavaService {

    public static RequestBuilder sendAnalyticsEvent(String baseUrl, String userAgent, Map<String, String> params) {
        RequestBuilder requestBuilder = new RequestBuilder()
                .method(HTTP_METHOD_GET)
                .url(buildUrlWithParams(baseUrl, params));
        requestBuilder.build().getHeaders().put("User-Agent", userAgent);
        return requestBuilder;
    }

    private static String buildUrlWithParams(String baserUrl, Map<String, String> params) {

        Uri.Builder builder = Uri.parse(baserUrl).buildUpon();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build().toString();
    }
}
