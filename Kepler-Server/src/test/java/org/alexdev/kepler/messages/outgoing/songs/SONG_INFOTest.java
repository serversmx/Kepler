package org.alexdev.kepler.messages.outgoing.songs;

import io.netty.buffer.Unpooled;
import org.alexdev.kepler.game.song.Song;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SONG_INFOTest {
    @Test
    void sendsTerminatedSongDataFollowedByExplicitVl64Zero() {
        var song = new Song();
        song.setData("1:1,3:2:2,1:3:3,2:4:4,5:");
        var message = new SONG_INFO(song);
        var buffer = Unpooled.buffer();
        try {
            message.compose(new NettyResponse(message.getHeader(), buffer));
            // v14 GetStrFrom consumes byte 2, GetIntFrom then reads H = VL64 zero.
            assertThat(buffer.toString(StandardCharsets.ISO_8859_1))
                    .isEqualTo("Dl" + song.getData() + "\u0002H");
        } finally {
            buffer.release();
        }
    }
}
