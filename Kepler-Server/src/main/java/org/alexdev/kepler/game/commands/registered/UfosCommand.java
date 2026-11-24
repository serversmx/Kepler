package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.pathfinder.Position;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.messages.outgoing.rooms.items.PLACE_FLOORITEM;
import org.alexdev.kepler.messages.outgoing.rooms.items.SLIDEOBJECTBUNDLE;

import java.util.concurrent.ThreadLocalRandom;

public class UfosCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.ADMINISTRATOR_ACCESS);
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

        if (!room.isOwner(player.getDetails().getId()) && !player.hasFuse(Fuseright.ANY_ROOM_CONTROLLER)) {
            return;
        }

        int ufoAmount = ThreadLocalRandom.current().nextInt(0, 45) + 50;
        String textToSpeech = "¡Ayuda, objetos voladores desconocidos! ¡Los extraterrestres! Una flota de " + ufoAmount + " ovnis viene hacia acá. ¡Están atacando el hotel! Houston, tenemos un problema. ¡Alerta roja! ¡Ovnis por todas partes!";

        TalkCommand.createVoiceSpeakMessage(room, textToSpeech);

        int incremental = 0;

        for (int i = 0; i < ufoAmount; i++)  {
            //final int tempId = i;

           // GameScheduler.getInstance().getService().scheduleAtFixedRate(() -> {
                int ufoId = Integer.MAX_VALUE - (i + 1);

                Item pItem = new Item();
                pItem.setId(ufoId);
                pItem.setPosition(new Position(
                        ThreadLocalRandom.current().nextInt(0, 45),
                        ThreadLocalRandom.current().nextInt(0, 45),
                        ThreadLocalRandom.current().nextInt(-3, 10)));


                pItem.getDefinition().setSprite("nest");
                pItem.getDefinition().setLength(1);
                pItem.getDefinition().setWidth(1);
                room.send(new PLACE_FLOORITEM(pItem));

                int destX = ThreadLocalRandom.current().nextInt(-(20 + (pItem.getPosition().getX() * 2)), 20 + (pItem.getPosition().getY() * 2));
                int destY = ThreadLocalRandom.current().nextInt(-(20 + (pItem.getPosition().getY() * 2)), 20 + (pItem.getPosition().getX() * 2));// + ThreadLocalRandom.current().nextInt(-10, -20);
                float destZ = ThreadLocalRandom.current().nextInt(-9, 10);

                room.send(new SLIDEOBJECTBUNDLE(pItem.getPosition(), destX, destY, destZ, pItem.getId()));
            //}, 0, incremental += 10, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public String getDescription() {
        return "Muestra una invasión de ovnis en la sala";
    }
}
