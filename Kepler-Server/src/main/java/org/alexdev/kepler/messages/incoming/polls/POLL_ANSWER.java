package org.alexdev.kepler.messages.incoming.polls;

import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.polls.PollManager;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

/**
 * 236 POLL_ANSWER, one per question: [#integer: pollId, #integer: questionId]
 * plus #integer count and #integer 1-based positions (types 1/2) or
 * #string text (types 3/4). The client expects no reply.
 */
public class POLL_ANSWER implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        if (!player.isLoggedIn()) {
            return;
        }

        try {
            PollManager.getInstance().answer(player, reader);
        } catch (IndexOutOfBoundsException malformed) {
            // Truncated packet; nothing to store.
        }
    }
}
