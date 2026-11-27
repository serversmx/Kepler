package org.alexdev.kepler.messages.incoming.trade;

import org.alexdev.kepler.dao.mysql.ItemDao;
import org.alexdev.kepler.game.inventory.Inventory;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.game.room.managers.RoomTradeManager;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TRADE_ACCEPTTest {
    private final Player player = mock(Player.class);
    private final Player partner = mock(Player.class);
    private final RoomPlayer user = new RoomPlayer(player);
    private final RoomPlayer other = new RoomPlayer(partner);

    private void openTrade() {
        when(player.getRoomUser()).thenReturn(user);
        when(partner.getRoomUser()).thenReturn(other);
        when(player.getInventory()).thenReturn(mock(Inventory.class));
        when(partner.getInventory()).thenReturn(mock(Inventory.class));
        user.setRoom(mock(Room.class));
        user.setTradePartner(partner);
        other.setTradePartner(player);
    }

    @Test
    void completesBothSidesBeforeExistingCloseAndResetsTrade() {
        openTrade();
        other.setTradeAccept(true);
        try (var dao = mockStatic(ItemDao.class)) {
            new TRADE_ACCEPT().handle(player, null);
            // Empty offers still exercise both transfer calls without a database.
            dao.verify(() -> ItemDao.updateItems(java.util.List.of()), times(2));
        }

        var messages = ArgumentCaptor.forClass(MessageComposer.class);
        verify(player, times(3)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(MessageComposer::getHeader)
                .containsExactly((short) 108, (short) 112, (short) 110);
        var otherMessages = ArgumentCaptor.forClass(MessageComposer.class);
        verify(partner, times(3)).send(otherMessages.capture());
        assertThat(otherMessages.getAllValues()).extracting(MessageComposer::getHeader)
                .containsExactly((short) 108, (short) 112, (short) 110);
        var body = mock(NettyResponse.class);
        messages.getAllValues().get(1).compose(body);
        verifyNoInteractions(body);
        verify(player.getInventory()).getView("new");
        verify(partner.getInventory()).getView("new");
        assertThat(user.getTradePartner()).isNull();
        assertThat(other.getTradePartner()).isNull();
        assertThat(user.hasAcceptedTrade()).isFalse();
        assertThat(other.hasAcceptedTrade()).isFalse();
    }

    @Test
    void firstAcceptanceDoesNotCompleteOrClose() {
        openTrade();
        new TRADE_ACCEPT().handle(player, null);
        var messages = ArgumentCaptor.forClass(MessageComposer.class);
        verify(player).send(messages.capture());
        assertThat(messages.getValue().getHeader()).isEqualTo((short) 108);
        assertThat(user.getTradePartner()).isSameAs(partner);
    }

    @Test
    void cancellationKeeps110WithoutCompletion() {
        openTrade();
        RoomTradeManager.close(user);
        var messages = ArgumentCaptor.forClass(MessageComposer.class);
        verify(player).send(messages.capture());
        assertThat(messages.getValue().getHeader()).isEqualTo((short) 110);
        verify(partner).send(isA(org.alexdev.kepler.messages.outgoing.trade.TRADE_CLOSE.class));
        assertThat(user.getTradePartner()).isNull();
        assertThat(other.getTradePartner()).isNull();
    }
}
