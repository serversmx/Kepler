package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.game.room.RoomUserStatus;
import org.alexdev.kepler.game.room.enums.StatusType;
import org.alexdev.kepler.game.texts.TextsManager;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE.ChatMessageType;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;

public class GiveDrinkCommand extends Command {

    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
    }

    @Override
    public void addArguments() {
        this.arguments.add("usuario");
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity.getType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) entity;

        if (player.getRoomUser().getRoom() == null) {
            return;
        }

        Player targetUser = PlayerManager.getInstance().getPlayerByName(args[0]);

        if (targetUser == null ||
                targetUser.getRoomUser().getRoom() == null ||
                targetUser.getRoomUser().getRoom().getId() != player.getRoomUser().getRoom().getId()) {
            player.send(new ALERT("No se encontró al usuario: " + args[0]));
            return;
        }

        if (!player.getRoomUser().containsStatus(StatusType.CARRY_DRINK) && !player.getRoomUser().containsStatus(StatusType.CARRY_FOOD)) {
            player.send(new ALERT("No llevas comida ni bebida para entregar."));
            return;
        }

        RoomUserStatus drink = null;

        if (player.getRoomUser().containsStatus(StatusType.CARRY_DRINK)) {
            drink = player.getRoomUser().getStatus(StatusType.CARRY_DRINK);
        }

        if (player.getRoomUser().containsStatus(StatusType.CARRY_FOOD)) {
            drink = player.getRoomUser().getStatus(StatusType.CARRY_FOOD);
        }

        if (drink != null) {
            if (targetUser.getRoomUser().containsStatus(StatusType.AVATAR_SLEEP)) {
                player.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), targetUser.getDetails().getName() + " está durmiendo."));
                return;
            }

            // Give drink to user if they're not already having a drink or food, and they're not dancing
            if (targetUser.getRoomUser().containsStatus(StatusType.CARRY_FOOD) ||
                targetUser.getRoomUser().containsStatus(StatusType.CARRY_DRINK)) {
                player.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), targetUser.getDetails().getName() + " ya tiene comida o bebida."));
                return;
            }

            if (targetUser.getRoomUser().containsStatus(StatusType.DANCE)) {
                player.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "No puedes entregarle la bebida a " + targetUser.getDetails().getName() + ", porque está bailando."));
                return;
            }

            int carryID = Integer.parseInt(drink.getValue());
            targetUser.getRoomUser().carryItem(carryID, null);
            String carryName = TextsManager.getInstance().getValue("handitem" + carryID);

            targetUser.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, targetUser.getRoomUser().getInstanceId(), player.getDetails().getName() + " te entregó: " + carryName + "."));

            player.getRoomUser().removeStatus(StatusType.CARRY_DRINK);
            player.getRoomUser().removeStatus(StatusType.CARRY_FOOD);
            player.getRoomUser().setNeedsUpdate(true);
        }
    }

    @Override
    public String getDescription() {
        return "Entrega tu bebida o comida a otro usuario de la sala";
    }
}
