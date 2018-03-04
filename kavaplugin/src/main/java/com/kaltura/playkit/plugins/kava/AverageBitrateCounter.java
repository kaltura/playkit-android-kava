package com.kaltura.playkit.plugins.kava;


import android.util.LongSparseArray;

import com.kaltura.playkit.PKLog;


/**
 * Created by anton.afanasiev on 08/02/2018.
 */

public class AverageBitrateCounter {

    private PKLog log = PKLog.get(AverageBitrateCounter.class.getSimpleName());

    private boolean shouldCount = false;
    private long currentTrackBitrate = -1;
    private long currentTrackStartTimestamp = 0;

    private LongSparseArray<Long> averageTrackPlaybackDuration = new LongSparseArray<>();

    /**
     * Calculate average bitrate for the entire media session.
     *
     * @param totalPlaytimeSum - total amount of the player being in active playback mode.
     * @return - average bitrate.
     */
    long getAverageBitrate(long totalPlaytimeSum) {

        updateBitratePlayTime();

        long bitrate;
        long playTime;
        long averageBitrate = 0;
        for (int i = 0; i < averageTrackPlaybackDuration.size(); i++) {
            bitrate = averageTrackPlaybackDuration.keyAt(i);
            playTime = averageTrackPlaybackDuration.get(bitrate);
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

    void reset() {
        averageTrackPlaybackDuration.clear();
    }
}
