package org.alexdev.kepler.messages.outgoing.games;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * Answer to GET_SPECTATOR_AMOUNT. hh_room Room Handler (handle_spectator_amount,
 * listener 298) reads two ints: the spectator count and the maximum shown as
 * "spectators %cnt%/%max%". A count of -1 makes the client hide the counter.
 */
public class SPECTATOR_AMOUNT extends MessageComposer {
    private final int count;
    private final int max;

    public SPECTATOR_AMOUNT(int count, int max) {
        this.count = count;
        this.max = max;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.count);
        response.writeInt(this.max);
    }

    @Override
    public short getHeader() {
        return 298;
    }
}
