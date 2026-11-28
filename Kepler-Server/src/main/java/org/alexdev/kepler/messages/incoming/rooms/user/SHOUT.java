package org.alexdev.kepler.messages.incoming.rooms.user;

import org.alexdev.kepler.game.commands.CommandManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.StringUtil;

public class SHOUT implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        Room room = player.getRoomUser().getRoom();

        if (room == null) {
            return;
        }

        String message = StringUtil.filterInput(reader.readString(), true);

        // TYPING_STATUS (361) is not sent: the v14 client registers no listener for it.
        player.getRoomUser().setTyping(false);

        if (message.isEmpty()) {
            return;
        }

        if (CommandManager.getInstance().hasCommand(player, message)) {
            CommandManager.getInstance().invokeCommand(player, message);
            return;
        }

        player.getRoomUser().talk(message, CHAT_MESSAGE.ChatMessageType.SHOUT);
    }
}
