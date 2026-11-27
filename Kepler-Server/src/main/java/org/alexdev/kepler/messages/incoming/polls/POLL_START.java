package org.alexdev.kepler.messages.incoming.polls;

import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.polls.PollManager;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

/**
 * 234 POLL_START: [#integer: pollId] after clicking OK in the offer window.
 */
public class POLL_START implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        if (!player.isLoggedIn()) {
            return;
        }

        int pollId;

        try {
            pollId = reader.readInt();
        } catch (RuntimeException malformed) {
            return;
        }

        PollManager.getInstance().start(player, pollId);
    }
}
