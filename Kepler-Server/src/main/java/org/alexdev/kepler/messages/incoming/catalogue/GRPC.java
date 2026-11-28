package org.alexdev.kepler.messages.incoming.catalogue;

import org.alexdev.kepler.dao.mysql.CurrencyDao;
import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.alexdev.kepler.game.catalogue.CatalogueItem;
import org.alexdev.kepler.game.catalogue.CatalogueManager;
import org.alexdev.kepler.game.catalogue.CataloguePage;
import org.alexdev.kepler.game.catalogue.RareManager;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.item.ItemManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.game.texts.TextsManager;
import org.alexdev.kepler.messages.outgoing.catalogue.NO_CREDITS;
import org.alexdev.kepler.messages.outgoing.catalogue.PURCHASE_ERROR;
import org.alexdev.kepler.messages.outgoing.catalogue.PURCHASE_NOT_ALLOWED;
import org.alexdev.kepler.messages.outgoing.rooms.items.ITEM_DELIVERED;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.messages.outgoing.alert.NO_USER_FOUND;
import org.alexdev.kepler.messages.outgoing.user.currencies.CREDIT_BALANCE;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.DateUtil;
import org.alexdev.kepler.util.StringUtil;
import org.alexdev.kepler.util.config.GameConfiguration;

public class GRPC implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) throws Exception {
        String content = reader.contents();
        String[] data = content.split(Character.toString((char) 13), -1);

        if (data.length < 6 || (data[5].equals("1") && data.length < 7)) {
            player.send(new PURCHASE_ERROR());
            return;
        }

        String saleCode = data[3];

        CatalogueManager catalogue = CatalogueManager.getInstance();
        CatalogueItem item = catalogue.getCatalogueItem(saleCode);

        if (item == null) {
            player.send(new PURCHASE_ERROR());
            return;
        }

        int price = item.getPrice();

        // One read of rare + price table: a rotation or reload can't swap one under the other.
        var rareSnapshot = RareManager.getInstance().getSnapshot();
        var currentRare = rareSnapshot.getRare();
        // Compare by sale code: a catalogue reload replaces the item instances.
        boolean isCurrentRare = currentRare != null && (currentRare == item ||
                (item.getSaleCode() != null && item.getSaleCode().equals(currentRare.getSaleCode())));
        // v14 sends the page index in the second CR-delimited field. The daily
        // rare is sold through its public display page, not its staff pool page.
        CataloguePage page = catalogue.getCataloguePage(data[1]);
        boolean displayedRare = isCurrentRare && page != null &&
                page.getId() == GameConfiguration.getInstance().getInteger("rare.cycle.page.id");
        if (page == null || (!item.hasPage(page.getId()) && !displayedRare) ||
                page.getMinRole().getRankId() > player.getDetails().getRank().getRankId()) {
            player.send(new PURCHASE_ERROR());
            return;
        }
        if (page.isClubOnly() && !player.getDetails().hasClubSubscription()) {
            player.send(new PURCHASE_NOT_ALLOWED());
            return;
        }

        if (isCurrentRare) {
            if (!player.hasFuse(Fuseright.CREDITS)) {
                Integer rarePrice = rareSnapshot.getPrice();

                // A rare without a price can't be sold (its page left rare.cycle.pages).
                if (rarePrice == null) {
                    player.send(new PURCHASE_ERROR());
                    return;
                }

                price = rarePrice;
            }
        }

        if (price > player.getDetails().getCredits()) {
            player.send(new NO_CREDITS());
            return;
        }

        if (data[5].equals("1")) { // It's a gift!
            PlayerDetails receivingUserDetails = PlayerManager.getInstance().getPlayerData(PlayerDao.getId(data[6]));

            //if (!data[6].toLowerCase().equals(player.getDetails().getName().toLowerCase())) {
            if (receivingUserDetails == null) {
                player.send(new NO_USER_FOUND(data[6]));
                return;
            }
            //}

            String presentNote = "";
            String extraData = data[4];

            try {
                presentNote = data[7];
            } catch (Exception ignored) {
                presentNote = "";
            }

            if (presentNote.isEmpty()) {
                presentNote = " ";
            }
            
            extraData = extraData.replace(Item.PRESENT_DELIMETER, "");
            presentNote = presentNote.replace(Item.PRESENT_DELIMETER, "");

            if (item.getDefinition() != null && item.getDefinition().getSprite().equals("poster")) {
                extraData = String.valueOf(item.getItemSpecialId());
            }

            Item present = ItemManager.getInstance().createGift(receivingUserDetails, player.getDetails(), item.getSaleCode(), StringUtil.filterInput(presentNote, false), extraData);//new Item();
            /*present.setOwnerId(receivingUserDetails.getId());
            present.setDefinitionId(ItemManager.getInstance().getDefinitionBySprite("present_gen" + ThreadLocalRandom.current().nextInt(1, 7)).getId());
            present.setCustomData(saleCode +
                    Item.PRESENT_DELIMETER + player.getDetails().getName() +
                    Item.PRESENT_DELIMETER + StringUtil.filterInput(presentNote, true) +
                    Item.PRESENT_DELIMETER + extraData +
                    Item.PRESENT_DELIMETER + DateUtil.getCurrentTimeSeconds());

            ItemDao.newItem(present);*/

            Player receiver = PlayerManager.getInstance().getPlayerById(receivingUserDetails.getId());

            if (receiver != null) {
                receiver.getInventory().addItem(present);
                receiver.getInventory().getView("last");
                //receiver.send(new ITEM_DELIVERED());
            }

            player.send(new ALERT(TextsManager.getInstance().getValue("successfully_purchase_gift_for").replace("%user%", receivingUserDetails.getName())));
            //player.send(new DELIVER_PRESENT(present));
        } else {
            String extraData = null;

            if (!item.isPackage()) {
                extraData = data[4];
            }

            // The transaction log must hold what is really charged (the rare cycle price), not the catalogue price.
            if (catalogue.purchase(player, item, extraData, null, DateUtil.getCurrentTimeSeconds(), price).size() > 0) {
                player.getInventory().getView("new");
            }

            boolean showItemDelivered = true;

            // Don't send item delivered if they just buy film
            if (item.getDefinition() != null && item.getDefinition().getSprite().equals("film")) {
                showItemDelivered = false;
            }

            if (showItemDelivered) {
                if (!GameConfiguration.getInstance().getBoolean("disable.purchase.successful.alert")) {
                    player.send(new ITEM_DELIVERED());
                }
            }
        }

        CurrencyDao.decreaseCredits(player.getDetails(), price);
        player.send(new CREDIT_BALANCE(player.getDetails()));
    }
}
