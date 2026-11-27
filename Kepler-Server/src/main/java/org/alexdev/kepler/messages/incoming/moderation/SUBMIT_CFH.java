package org.alexdev.kepler.messages.incoming.moderation;

import org.alexdev.kepler.game.moderation.cfh.CallForHelpManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

public class SUBMIT_CFH implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) throws Exception {
        if (player.getRoomUser().getRoom() == null) {
            return;
        }

        String message = reader.readString();

        if (message == null || message.isBlank()) {
            return;
        }

        message = message.trim();

        // Only allow one call for help per user
        if (CallForHelpManager.getInstance().hasPendingCall(player)) {
            // El cliente v14 no tiene un mensaje propio de "llamada pendiente": avisamos con una alerta
            player.send(new ALERT("Ya tienes una llamada de ayuda pendiente. Espera a que un moderador la atienda."));
            return;
        }

        CallForHelpManager.getInstance().submitCall(player, message);
    }
}
