package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.dao.mysql.CatalogueDao;
import org.alexdev.kepler.game.catalogue.CatalogueManager;
import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.apache.commons.lang3.StringUtils;

public class SetItemPriceCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.ADMINISTRATOR_ACCESS);
    }

    @Override
    public void addArguments() {
        this.arguments.add("código_venta");
        this.arguments.add("precio");
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity.getType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) entity;
        String saleCode = args[0];

        if (CatalogueManager.getInstance().getCatalogueItem(saleCode) == null) {
            player.send(new ALERT("No se encontró el artículo en el catálogo."));
            return;
        }


        if (!StringUtils.isNumeric(args[1])) {
            player.send(new ALERT("El precio debe ser un número entero positivo o cero."));
            return;
        }

        int newPrice = Integer.parseInt(args[1]);
        var item = CatalogueManager.getInstance().getCatalogueItem(saleCode);

        if (item.getPrice() == newPrice) {
            player.send(new ALERT("El artículo ya tiene ese precio."));
            return;
        }

        CatalogueDao.setPrice(item.getSaleCode(), newPrice);

        String word = "aumentó";

        if (item.getPrice() > newPrice) {
            word = "bajó";
        }

        player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "El precio de " + item.getName() + " " + word + " de " + item.getPrice() + " a " + newPrice + " créditos."));
        item.setPrice(newPrice);
    }

    @Override
    public String getDescription() {
        return "Cambia el precio de un artículo del catálogo";
    }
}
