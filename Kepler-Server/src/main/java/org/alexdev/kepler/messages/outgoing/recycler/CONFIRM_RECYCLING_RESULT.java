package org.alexdev.kepler.messages.outgoing.recycler;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * Respuesta a CONFIRM_FURNI_RECYCLING. El cliente (handle_confirm_recycling_result)
 * lee un entero con GetIntFrom; distinto de 0 devuelve el reciclador al estado "open".
 */
public class CONFIRM_RECYCLING_RESULT extends MessageComposer {
    private final boolean success;

    public CONFIRM_RECYCLING_RESULT(boolean success) {
        this.success = success;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.success ? 1 : 0);
    }

    @Override
    public short getHeader() {
        return 307;
    }
}
