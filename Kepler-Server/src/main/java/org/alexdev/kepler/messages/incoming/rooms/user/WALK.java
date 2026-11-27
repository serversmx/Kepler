package org.alexdev.kepler.messages.incoming.rooms.user;

import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.item.OneWayGate;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.mapping.RoomTile;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

public class WALK implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        if (!player.getRoomUser().isWalkingAllowed()) {
            return;
        }

        int X = reader.readBase64();
        int Y = reader.readBase64();

        // Any new walk request cancels an approach to a one way gate
        player.getRoomUser().setPendingOneWayGateId(-1);

        // The one way door class lets the click fall back to a normal MOVE onto the door tile
        if (player.getRoomUser().getRoom() != null) {
            RoomTile tile = player.getRoomUser().getRoom().getMapping().getTile(X, Y);
            Item item = tile != null ? tile.getHighestItem() : null;

            if (item != null && item.isOneWayGate()) {
                OneWayGate.requestPass(player, item);
                return;
            }
        }

        player.getRoomUser().walkTo(X, Y);
    }
}
