package org.alexdev.kepler.messages.incoming.user;

import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.messages.outgoing.user.UPDATE_OK;
import org.alexdev.kepler.messages.outgoing.user.USER_OBJECT;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UPDATETest {
    @Test
    void acknowledgesSavedSsoFigureAndMottoAfterUnknownFieldWithoutDroppingChanges() throws Exception {
        var player = mock(Player.class);
        var details = new PlayerDetails();
        details.markSsoAuthenticated("active-ticket");
        var roomUser = mock(RoomPlayer.class);
        when(player.isLoggedIn()).thenReturn(true);
        when(player.getDetails()).thenReturn(details);
        when(player.getRoomUser()).thenReturn(roomUser);
        when(roomUser.getRoom()).thenReturn(mock(Room.class));
        var reader = mock(NettyRequest.class);
        when(reader.remainingBytes()).thenReturn(new byte[]{1});
        when(reader.readBase64()).thenReturn(4, 6, 5, 999);
        when(reader.readString()).thenReturn("10001180012700129001", "Nueva mision", "f");
        var manager = new PlayerManager();

        try (var managers = mockStatic(PlayerManager.class); var dao = mockStatic(PlayerDao.class)) {
            managers.when(PlayerManager::getInstance).thenReturn(manager);
            new UPDATE().handle(player, reader);
            assertThat(details.getFigure()).isEqualTo("10001180012700129001");
            assertThat(details.getMotto()).isEqualTo("Nueva mision");
            assertThat(details.getSex()).isEqualTo('F');
            assertThat(details.matchesProfileAuthToken("active-ticket")).isTrue();
            dao.verify(() -> PlayerDao.saveDetails(details));
            dao.verify(() -> PlayerDao.saveMotto(details));
            var order = inOrder(player, roomUser);
            order.verify(player).send(isA(USER_OBJECT.class));
            order.verify(roomUser).refreshAppearance();
            order.verify(player).send(isA(UPDATE_OK.class));
            verify(player, times(2)).send(any());
        }
    }

    @Test
    void doesNotAcknowledgeFailedSave() {
        var player = mock(Player.class);
        when(player.isLoggedIn()).thenReturn(true);
        when(player.getDetails()).thenReturn(new PlayerDetails());
        var reader = mock(NettyRequest.class);
        when(reader.remainingBytes()).thenReturn(new byte[0]);
        var manager = new PlayerManager();
        try (var managers = mockStatic(PlayerManager.class); var dao = mockStatic(PlayerDao.class)) {
            managers.when(PlayerManager::getInstance).thenReturn(manager);
            dao.when(() -> PlayerDao.saveDetails(any())).thenThrow(new IllegalStateException("save failed"));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> new UPDATE().handle(player, reader));
            verify(player, never()).send(any());
        }
    }

    @Test
    void unauthenticatedUpdateDoesNotAcknowledgeOrRead() throws Exception {
        var player = mock(Player.class);
        var reader = mock(NettyRequest.class);
        new UPDATE().handle(player, reader);
        verifyNoInteractions(reader);
        verify(player, never()).send(any());
    }

    @Test
    void updateOkIs211WithoutPayload() {
        var message = new UPDATE_OK();
        var body = mock(NettyResponse.class);
        assertThat(message.getHeader()).isEqualTo((short) 211);
        message.compose(body);
        verifyNoInteractions(body);
    }
}
