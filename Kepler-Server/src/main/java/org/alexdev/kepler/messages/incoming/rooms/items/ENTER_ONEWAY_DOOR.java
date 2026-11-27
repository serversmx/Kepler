package org.alexdev.kepler.messages.incoming.rooms.items;

import org.alexdev.kepler.game.item.OneWayGate;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

/**
 * Sent by the client (command 232) with the furniture id as an integer when the user
 * double clicks a one way door while standing on the tile in front of it.
 */
public class ENTER_ONEWAY_DOOR implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        if (player.getRoomUser().getRoom() == null || !player.getRoomUser().isWalkingAllowed()) {
            return;
        }

        OneWayGate.enter(player, reader.readInt());
    }
}
