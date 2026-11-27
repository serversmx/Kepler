package org.alexdev.kepler.messages.outgoing.songs;

import org.alexdev.kepler.game.song.Song;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

public class SONG_INFO extends MessageComposer {
    private final Song song;
    private final int playTime;

    public SONG_INFO(Song song) {
        this(song, 0);
    }

    /**
     * @param playTime position of the song in units of 100 ms (the client wraps it by the song length)
     */
    public SONG_INFO(Song song, int playTime) {
        this.song = song;
        this.playTime = playTime;
    }

    @Override
    public void compose(NettyResponse response) {
        // TODO: V15 toggle?
        // if (getPlayer().getVersion() > 14) {
         //   response.writeInt(this.song.getId());
         //   response.writeString(this.song.getTitle());
        //}

        response.writeString(this.song.getData());
        // v14 reads a VL64 playTime after the string (units of 100 ms).
        response.writeInt(this.playTime);
    }

    @Override
    public short getHeader() {
        return 300; // "Dl"
    }
}
