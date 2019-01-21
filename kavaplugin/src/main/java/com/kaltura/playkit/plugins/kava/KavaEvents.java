package com.kaltura.playkit.plugins.kava;

/**
 * Created by anton.afanasiev on 31/01/2018.
 */

public enum KavaEvents {
    IMPRESSION(1),
    PLAY_REQUEST(2),
    PLAY(3),
    RESUME(4),
    CAN_PLAY(5),
    PLAY_REACHED_25_PERCENT(11),
    PLAY_REACHED_50_PERCENT(12),
    PLAY_REACHED_75_PERCENT(13),
    PLAY_REACHED_100_PERCENT(14),
    PAUSE(33),
    REPLAY(34),
    SEEK(35),
    CAPTIONS(38),
    SOURCE_SELECTED(39), // video track changed manually.
    AUDIO_SELECTED(42), // audio track changed manually
    FLAVOR_SWITCHED(43), // abr bitrate switch.
    ERROR(98),
    VIEW(99);

    private final int value;

    KavaEvents(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
