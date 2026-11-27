package org.alexdev.kepler.game.song;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each sound machine started its current song, so players who
 * enter the room later hear it from the same point instead of from zero.
 * The v14 client reads the play time in units of 100 ms and wraps it by the
 * song length itself.
 */
public final class SongPlayback {
    private static final Map<Long, Entry> STARTED = new ConcurrentHashMap<>();

    private static final int RESTARTED = -1;

    private record Entry(int songId, int dataHash, long startedAt) { }

    private SongPlayback() { }

    public static int playTime(long machineId, Song song) {
        return playTime(machineId, song, System.currentTimeMillis());
    }

    static int playTime(long machineId, Song song, long now) {
        int dataHash = Objects.hashCode(song.getData());
        Entry entry = STARTED.compute(machineId, (id, current) -> {
            if (current != null && current.songId() == RESTARTED) {
                // Machine switched on/off: the song restarted at that moment.
                return new Entry(song.getId(), dataHash, current.startedAt());
            }

            return current != null && current.songId() == song.getId() && current.dataHash() == dataHash
                    ? current
                    : new Entry(song.getId(), dataHash, now);
        });

        long tenths = Math.max(0, (now - entry.startedAt()) / 100);
        return (int) Math.min(tenths, Integer.MAX_VALUE);
    }

    /**
     * The client restarts the song when the machine is switched on or off.
     */
    public static void restart(long machineId) {
        restart(machineId, System.currentTimeMillis());
    }

    static void restart(long machineId, long now) {
        STARTED.put(machineId, new Entry(RESTARTED, 0, now));
    }

    static void reset() {
        STARTED.clear();
    }
}
