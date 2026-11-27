package org.alexdev.kepler.game.club;

import org.alexdev.kepler.dao.mysql.CurrencyDao;
import org.alexdev.kepler.game.player.*;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class ClubSubscriptionTest {
    @Test void insufficientCreditsSendAlertWithoutChangingSubscriptionOrBalance() throws Exception {
        Player player = mock(Player.class);
        PlayerDetails details = mock(PlayerDetails.class);
        when(player.getDetails()).thenReturn(details);
        when(details.getCredits()).thenReturn(24);
        try (var currency = mockStatic(CurrencyDao.class)) {
            ClubSubscription.subscribeClub(player, 31, 25);
            ArgumentCaptor<ALERT> alert = ArgumentCaptor.forClass(ALERT.class);
            verify(player).send(alert.capture());
            NettyResponse response = mock(NettyResponse.class);
            alert.getValue().compose(response);
            verify(response).writeString("No tienes suficientes creditos para comprar Habbo Club.");
            verify(details, never()).setClubExpiration(anyLong());
            verify(player, never()).refreshClub();
            currency.verifyNoInteractions();
        }
    }
}
