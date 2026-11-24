package org.alexdev.kepler.messages.incoming.rooms.items;

import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.item.base.ItemBehaviour;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.apache.commons.lang3.StringUtils;

import java.sql.SQLException;

public class REMOVEITEM implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) throws SQLException {
        Room room = player.getRoomUser().getRoom();

        if (room == null) {
            return;
        }

        String contents = reader.contents();

        if (!StringUtils.isNumeric(contents) || contents.length() > 9) {
            return;
        }

        int itemId = Integer.parseInt(contents);
        Item item = room.getItemManager().getById(itemId);

        // REMOVEITEM is the client's wall-item delete; floor furni never goes through here.
        if (item == null || !item.hasBehaviour(ItemBehaviour.WALL_ITEM)) {
            return;
        }

        if (!canRemove(room.isOwner(player.getDetails().getId()),
                item.hasBehaviour(ItemBehaviour.PHOTO), item.hasBehaviour(ItemBehaviour.POST_IT),
                player.hasFuse(Fuseright.REMOVE_PHOTOS), player.hasFuse(Fuseright.REMOVE_STICKIES))) {
            return;
        }


        // Set up trigger for leaving a current item
        if (player.getRoomUser().getCurrentItem() != null) {
            if (player.getRoomUser().getCurrentItem().getDefinition().getInteractionType().getTrigger() != null) {
                player.getRoomUser().getCurrentItem().getDefinition().getInteractionType().getTrigger().onEntityLeave(player, player.getRoomUser(), player.getRoomUser().getCurrentItem());
            }
        }

        room.getMapping().pickupItem(player, item);
        item.delete();
    }

    /**
     * Owners may delete any wall item in their room. Anyone else needs the fuse
     * that matches the item: fuse_remove_photos for photos and
     * fuse_remove_stickies for post-its (the old check had both inverted).
     */
    public static boolean canRemove(boolean owner, boolean photo, boolean postIt,
                                    boolean removePhotos, boolean removeStickies) {
        return owner || (photo && removePhotos) || (postIt && removeStickies);
    }
}
