package org.alexdev.kepler.game.song;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SongPlaybackTest {
    @AfterEach
    void tearDown() {
        SongPlayback.reset();
    }

    private static Song song(int id, String data) {
        return new Song(id, "t", 1L, 1, 10, data, false);
    }

    @Test
    void laterListenersJoinAtTheElapsedTimeInTenthsOfASecond() {
        Song song = song(5, "1:1,4");
        assertThat(SongPlayback.playTime(9L, song, 10_000L)).isZero();
        assertThat(SongPlayback.playTime(9L, song, 22_550L)).isEqualTo(125);
    }

    @Test
    void aDifferentOrEditedSongRestartsFromZero() {
        assertThat(SongPlayback.playTime(9L, song(5, "a"), 0L)).isZero();
        assertThat(SongPlayback.playTime(9L, song(6, "a"), 5_000L)).isZero();
        assertThat(SongPlayback.playTime(9L, song(6, "b"), 8_000L)).isZero();
        assertThat(SongPlayback.playTime(9L, song(6, "b"), 9_000L)).isEqualTo(10);
    }

    @Test
    void machinesAreTrackedSeparately() {
        assertThat(SongPlayback.playTime(1L, song(5, "a"), 0L)).isZero();
        assertThat(SongPlayback.playTime(2L, song(5, "a"), 3_000L)).isZero();
        assertThat(SongPlayback.playTime(1L, song(5, "a"), 3_000L)).isEqualTo(30);
    }

    @Test
    void switchingTheMachineRestartsTheSongAtThatMoment() {
        Song song = song(5, "a");
        assertThat(SongPlayback.playTime(9L, song, 0L)).isZero();
        SongPlayback.restart(9L, 50_000L);
        assertThat(SongPlayback.playTime(9L, song, 53_000L)).isEqualTo(30);
    }
}
