package org.alexdev.kepler.messages.incoming.games;

import org.alexdev.kepler.game.games.Game;
import org.alexdev.kepler.game.games.player.GamePlayer;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.games.SPECTATOR_AMOUNT;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.config.GameConfiguration;

/**
 * Command 216, sent by the spectator system every 15 seconds while watching a game.
 * The answer (298) carries the spectator count and the maximum, -1 hides the counter.
 */
public class GET_SPECTATOR_AMOUNT implements MessageEvent {
    public static final int DEFAULT_MAX_SPECTATORS = 20;

    @Override
    public void handle(Player player, NettyRequest reader) {
        GamePlayer gamePlayer = player.getRoomUser().getGamePlayer();
        Game game = gamePlayer != null && gamePlayer.isSpectator() ? gamePlayer.getGame() : null;

        if (game == null || player.getRoomUser().getRoom() == null) {
            player.send(new SPECTATOR_AMOUNT(-1, 0));
            return;
        }

        int count = (int) game.getSpectators().stream()
                .filter(spectator -> spectator.getPlayer().getRoomUser().getRoom() == player.getRoomUser().getRoom())
                .count();

        player.send(new SPECTATOR_AMOUNT(count, getMax(count)));
    }

    /**
     * The server has no spectator cap, so the maximum only feeds the "%cnt%/%max%" label:
     * game.spectators.max from the config (default 20), never below the real count.
     */
    public static int getMax(int count) {
        int max = DEFAULT_MAX_SPECTATORS;

        try {
            max = Integer.parseInt(GameConfiguration.getInstance().getString("game.spectators.max", String.valueOf(DEFAULT_MAX_SPECTATORS)));
        } catch (Exception ignored) {
            // Config not loaded or not a number: keep the default
        }

        return Math.max(max, count);
    }
}
