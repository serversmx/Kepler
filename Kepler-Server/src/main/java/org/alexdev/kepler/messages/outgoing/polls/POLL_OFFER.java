package org.alexdev.kepler.messages.outgoing.polls;

import org.alexdev.kepler.game.polls.PollManager;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * 316: hh_poll handle_poll_offer reads GetIntFrom (poll id) and
 * GetStrFrom (description shown in the "Encuesta" offer window).
 */
public class POLL_OFFER extends MessageComposer {
    private final int pollId;
    private final String description;

    public POLL_OFFER(int pollId, String description) {
        this.pollId = pollId;
        this.description = description;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.pollId);
        response.writeString(PollManager.clientText(this.description));
    }

    @Override
    public short getHeader() {
        return 316;
    }
}
