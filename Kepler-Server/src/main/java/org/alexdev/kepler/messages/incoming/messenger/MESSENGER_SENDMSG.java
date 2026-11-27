package org.alexdev.kepler.messages.incoming.messenger;

import org.alexdev.kepler.dao.mysql.MessengerDao;
import org.alexdev.kepler.game.messenger.MessengerMessage;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.messages.outgoing.messenger.MESSENGER_MSG;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.DateUtil;
import org.alexdev.kepler.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class MESSENGER_SENDMSG implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        int amount = reader.readInt();

        List<Integer> recipients = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            recipients.add(reader.readInt());
        }

        String chatMessage = StringUtil.filterInput(reader.readString(), false);

        // Solo amigos: el cliente puede enviar ids arbitrarios (los mensajes offline a amigos se siguen guardando)
        for (int userId : filterFriends(recipients, player.getMessenger()::hasFriend)) {
            int messageId = MessengerDao.newMessage(player.getDetails().getId(), userId, chatMessage);

            Player friend = PlayerManager.getInstance().getPlayerById(userId);

            if (friend != null) {
                MessengerMessage message = new MessengerMessage(
                        messageId, userId, player.getDetails().getId(), DateUtil.getCurrentTimeSeconds(), chatMessage);

                friend.send(new MESSENGER_MSG(message));
            }
        }
    }

    /**
     * Deja solo los destinatarios que son amigos, sin duplicados y en el mismo orden.
     */
    static List<Integer> filterFriends(List<Integer> recipients, IntPredicate isFriend) {
        List<Integer> result = new ArrayList<>();

        for (int id : recipients) {
            if (isFriend.test(id) && !result.contains(id)) {
                result.add(id);
            }
        }

        return result;
    }
}
