package org.alexdev.kepler.messages.incoming.rooms.items;

import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.item.Item;
import org.alexdev.kepler.game.item.base.ItemBehaviour;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * REMOVESTUFF (99): the infostand "Borrar" button for floor furni. The client
 * sends REMOVEITEM (85) only for wall items, so floor furni needs its own handler.
 */
public class REMOVESTUFF implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        Room room = player.getRoomUser().getRoom();

        if (room == null) {
            return;
        }

        String contents = reader.contents().trim();

        if (!StringUtils.isNumeric(contents) || contents.length() > 9) {
            return;
        }

        Item item = room.getItemManager().getById(Integer.parseInt(contents));

        if (item == null || item.hasBehaviour(ItemBehaviour.WALL_ITEM)) {
            return;
        }

        if (!canRemove(room.isOwner(player.getDetails().getId()), player.hasFuse(Fuseright.ANY_ROOM_CONTROLLER))) {
            return;
        }

        if (player.getRoomUser().getCurrentItem() == item
                && item.getDefinition().getInteractionType().getTrigger() != null) {
            item.getDefinition().getInteractionType().getTrigger().onEntityLeave(player, player.getRoomUser(), item);
        }

        room.getMapping().pickupItem(player, item);
        item.delete();
    }

    /**
     * Deleting furni is permanent: only the room owner, or staff who control any room.
     */
    public static boolean canRemove(boolean owner, boolean anyRoomController) {
        return owner || anyRoomController;
    }
}
