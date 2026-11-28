package org.alexdev.kepler.game.room;

import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.rooms.user.USER_OBJECTS;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.NettyPlayerNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * G_STAT announces the entering user to the others with room.send(USER_OBJECTS(player), List.of(player));
 * the exclusion list used to be ignored, so the entering client created its own user twice.
 */
class RoomSendExclusionTest {

    @Test
    void excludedPlayersDoNotReceiveTheMessage() {
        NettyPlayerNetwork enteringNetwork = mock(NettyPlayerNetwork.class);
        NettyPlayerNetwork otherNetwork = mock(NettyPlayerNetwork.class);
        Player entering = new Player(enteringNetwork);
        Player other = new Player(otherNetwork);

        Room room = new Room();
        room.getEntities().add(entering);
        room.getEntities().add(other);

        MessageComposer composer = new USER_OBJECTS(List.of());
        room.send(composer, List.of(entering));

        verify(otherNetwork).send(composer);
        verify(enteringNetwork, never()).send(any());
    }

    @Test
    void sendWithoutExclusionsReachesEveryone() {
        NettyPlayerNetwork first = mock(NettyPlayerNetwork.class);
        NettyPlayerNetwork second = mock(NettyPlayerNetwork.class);

        Room room = new Room();
        room.getEntities().add(new Player(first));
        room.getEntities().add(new Player(second));

        MessageComposer composer = new USER_OBJECTS(List.of());
        room.send(composer);

        verify(first).send(composer);
        verify(second).send(composer);
    }
}
