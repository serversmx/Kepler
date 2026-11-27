package org.alexdev.kepler.messages.outgoing.rooms.items;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * Changes the sprite of a one way door. hh_room_utils OneWayDoor Manager
 * (changeStatus, listener 312) reads two ints: the furniture id and the
 * status (1 = open, 0 = closed, anything else is rejected by setDoor).
 */
public class ONEWAY_DOOR_STATUS extends MessageComposer {
    private final int itemId;
    private final boolean open;

    public ONEWAY_DOOR_STATUS(int itemId, boolean open) {
        this.itemId = itemId;
        this.open = open;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.itemId);
        response.writeInt(this.open ? 1 : 0);
    }

    @Override
    public short getHeader() {
        return 312;
    }
}
