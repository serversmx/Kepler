package org.alexdev.kepler.messages.incoming.messenger;

import org.alexdev.kepler.game.bot.BotManager;
import org.alexdev.kepler.game.messenger.Messenger;
import org.alexdev.kepler.game.messenger.MessengerError;
import org.alexdev.kepler.game.messenger.MessengerErrorType;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.messages.outgoing.messenger.MESSENGER_ERROR;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;


public class MESSENGER_REQUESTBUDDY implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        String username = reader.readString();

        // The infostand of a bot shows "Pedir que sea tu amigo"; never forward that to a real user with the bot's name.
        if (BotManager.isBotInRoom(player.getRoomUser().getRoom(), username)) {
            player.send(new MESSENGER_ERROR(new MessengerError(MessengerErrorType.TARGET_DOES_NOT_ACCEPT)));
            return;
        }

        Messenger target = PlayerManager.getInstance().getMessengerData(username);

        if (target == null) {
            // Error type in external texts has it defined as "There was an error finding the user for the friend request"
            player.send(new MESSENGER_ERROR(new MessengerError(MessengerErrorType.FRIEND_REQUEST_NOT_FOUND)));
            return;
        }

        Messenger callee = player.getMessenger();

        if (callee.isFriendsLimitReached()) {
            player.send(new MESSENGER_ERROR(new MessengerError(MessengerErrorType.FRIENDLIST_FULL)));
            return;
        }

        if (target.hasFriend(player.getDetails().getId())) {
            return;
        }

        if (target.hasRequest(player.getDetails().getId())) {
            return;
        }

        if (target.isFriendsLimitReached()) {
            player.send(new MESSENGER_ERROR(new MessengerError(MessengerErrorType.TARGET_FRIEND_LIST_FULL)));
            return;
        }

        if (!target.isAllowsFriendRequests()) {
            player.send(new MESSENGER_ERROR(new MessengerError(MessengerErrorType.TARGET_DOES_NOT_ACCEPT)));
            return;
        }

        target.addRequest(callee.getMessengerUser());
    }
}
