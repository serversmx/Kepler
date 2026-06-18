package org.alexdev.kepler.messages.incoming.songs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SAVE_SONG.calculateSongLength parses the Trax sound-machine song format
 * (1:<t0>:2:<t1>:3:<t2>:4:<t3> plus a trailing byte) and returns the longest
 * track in seconds (each sample contributes count*2). It must also fail closed
 * — any malformed input returns 0 rather than throwing — since the payload
 * comes straight from the client.
 */
class SAVE_SONGTest {

    @Test
    void returnsTheLongestTrackInSeconds() {
        // tracks: aa,3 -> 6s | aa,1 -> 2s | aa,2 -> 4s | aa,5 -> 10s ; trailing 'X' is stripped.
        String song = "1:aa,3:2:aa,1:3:aa,2:4:aa,5X";

        assertThat(SAVE_SONG.calculateSongLength(song)).isEqualTo(10);
    }

    @Test
    void sumsMultipleSamplesWithinATrack() {
        // track 0 has two samples (3+2)*2 = 10s, which is the max.
        String song = "1:aa,3;bb,2:2:aa,1:3:aa,1:4:aa,1X";

        assertThat(SAVE_SONG.calculateSongLength(song)).isEqualTo(10);
    }

    @Test
    void malformedInputFailsClosedToZero() {
        assertThat(SAVE_SONG.calculateSongLength("")).isZero();
        assertThat(SAVE_SONG.calculateSongLength("not a song")).isZero();
        assertThat(SAVE_SONG.calculateSongLength("1:aa,x:2::3::4:Z")).isZero(); // non-numeric count
    }
}
