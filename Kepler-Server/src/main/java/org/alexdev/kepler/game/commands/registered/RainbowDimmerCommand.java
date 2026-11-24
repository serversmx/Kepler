package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.tasks.RainbowTask;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE.ChatMessageType;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

public class RainbowDimmerCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
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

        Room room = player.getRoomUser().getRoom();

        if (!player.getRoomUser().getRoom().isOwner(player.getDetails().getId()) && !player.hasFuse(Fuseright.ANY_ROOM_CONTROLLER)) {
            return;
        }

        int tickInterval = 5;

        if (args.length == 1) {
            if (!StringUtils.isNumeric(args[0])) {
                player.send(new ALERT("Indica con un número los segundos entre cada cambio de color."));
                return;
            } else {
                tickInterval = Integer.parseInt(args[0]);
            }
        }

        if (tickInterval < 1) {
            tickInterval = 1;
        }

        Item moodlight = room.getItemManager().getMoodlight();

        if (moodlight == null) {
            player.send(new ALERT("Necesitas un regulador de luces colocado en la sala."));
            return;
        }


        Player roomOwner = PlayerManager.getInstance().getPlayerById(room.getData().getOwnerId());
        boolean ownerInRoom = false;

        if (roomOwner.getRoomUser().getRoom() != null) {
            ownerInRoom = roomOwner.getRoomUser().getRoom().getData().getOwnerId() == room.getData().getOwnerId();
        }

        String statusMessage;

        if (room.getTaskManager().hasTask("RainbowTask")) {
            room.getTaskManager().cancelTask("RainbowTask");

            statusMessage = "Se detuvo el ciclo de colores del regulador de luces.";
        } else {
            RainbowTask rainbowTask = new RainbowTask(room);
            room.getTaskManager().scheduleTask("RainbowTask", rainbowTask, 0, tickInterval, TimeUnit.SECONDS);

            statusMessage = "Se inició el ciclo de colores del regulador de luces.";
        }

        player.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), statusMessage));

        // Send status of room task to roomowner
        if (ownerInRoom) {
            roomOwner.send(new CHAT_MESSAGE(ChatMessageType.WHISPER, roomOwner.getRoomUser().getInstanceId(), statusMessage));
        }
    }

    @Override
    public String getUsageArguments() {
        return "[segundos opcionales]";
    }

    @Override
    public String getDescription() {
        return "Activa o detiene los colores del regulador de luces de tu sala";
    }
}
