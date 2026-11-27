package org.alexdev.kepler.messages.outgoing.polls;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * 318: hh_poll handle_poll_error closes every poll window and shows
 * poll_alert_server_error ("La encuesta ya no está disponible..."). No body.
 */
public class POLL_ERROR extends MessageComposer {
    @Override
    public void compose(NettyResponse response) {
    }

    @Override
    public short getHeader() {
        return 318;
    }
}
