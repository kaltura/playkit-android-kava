package com.kaltura.playkit.plugins.kava;


import com.kaltura.playkit.PKLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by anton.afanasiev on 08/02/2018.
 */

public class AverageBitrateCounter {

    private PKLog log = PKLog.get(AverageBitrateCounter.class.getSimpleName());

    private boolean shouldCount = false;
    private long currentTrackBitrate = -1;
    private long currentTrackStartTimestamp = 0;

    private Map<Long, Long> averageTrackPlaybackDuration = new HashMap<>();

    /**
     * Calculate average bitrate for the entire media session.
     *
     * @param totalPlaytimeSum - total amount of the player being in active playback mode.
     * @return - average bitrate.
     */
    long getAverageBitrate(long totalPlaytimeSum) {

        updateBitratePlayTime();
        Iterator<Map.Entry<Long, Long>> iterator = averageTrackPlaybackDuration.entrySet().iterator();
        long averageBitrate = 0;
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> pair = iterator.next();
            long bitrate = pair.getKey();
            long playTime = pair.getValue();
            averageBitrate += (bitrate * playTime) / totalPlaytimeSum;
        }

        return averageBitrate;
    }

    private void updateBitratePlayTime() {
        //We are not counting adaptive bitrate(0) selection as average.
        if (currentTrackBitrate == 0) {
            return;
        }

        long currentTimeStamp = System.currentTimeMillis();
        long playedTime = currentTimeStamp - currentTrackStartTimestamp;

        //When it is first time that this bitrate is was selected we add it to the averageTrackPlaybackDuration
        //with the playedTime value and bitrate as key.
        if (averageTrackPlaybackDuration.get(currentTrackBitrate) == null) {
            averageTrackPlaybackDuration.put(currentTrackBitrate, playedTime);

        } else {
            // Otherwise we will get the existing value and add the last played one.
            //After that save it to averageTrackPlaybackDuration.
            long totalPlayedTime = averageTrackPlaybackDuration.get(currentTrackBitrate);
            totalPlayedTime += playedTime;
            averageTrackPlaybackDuration.put(currentTrackBitrate, totalPlayedTime);
        }

        currentTrackStartTimestamp = currentTimeStamp;

    }

    void resumeCounting() {
        currentTrackStartTimestamp = System.currentTimeMillis();
        shouldCount = true;
    }

    void pauseCounting() {
        updateBitratePlayTime();
        shouldCount = false;
    }

    void setBitrate(long bitrate) {
        if (shouldCount) {
            updateBitratePlayTime();
        }
        this.currentTrackBitrate = bitrate;
    }
}
