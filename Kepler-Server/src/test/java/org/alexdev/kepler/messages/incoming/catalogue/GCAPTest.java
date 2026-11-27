package org.alexdev.kepler.messages.incoming.catalogue;

import org.alexdev.kepler.game.catalogue.*;
import org.alexdev.kepler.game.player.*;
import org.alexdev.kepler.messages.outgoing.catalogue.CATALOGUE_PAGE;
import org.alexdev.kepler.server.netty.streams.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class GCAPTest {
    @Test void missingAndRestrictedPagesReturnValidEmptyPages() {
        Player player = mock(Player.class);
        PlayerDetails details = mock(PlayerDetails.class);
        when(player.getDetails()).thenReturn(details);
        when(details.getRank()).thenReturn(PlayerRank.NORMAL);
        CatalogueManager catalogue = mock(CatalogueManager.class);
        NettyRequest request = mock(NettyRequest.class);
        when(request.contents()).thenReturn("production/Secret/en");
        try (var singleton = mockStatic(CatalogueManager.class)) {
            singleton.when(CatalogueManager::getInstance).thenReturn(catalogue);
            new GCAP().handle(player, request);
            CataloguePage restricted = mock(CataloguePage.class);
            when(restricted.getMinRole()).thenReturn(PlayerRank.ADMINISTRATOR);
            when(catalogue.getCataloguePage("Secret")).thenReturn(restricted);
            new GCAP().handle(player, request);
            ArgumentCaptor<CATALOGUE_PAGE> messages = ArgumentCaptor.forClass(CATALOGUE_PAGE.class);
            verify(player, times(2)).send(messages.capture());
            for (CATALOGUE_PAGE message : messages.getAllValues()) {
                NettyResponse response = mock(NettyResponse.class);
                message.compose(response);
                verify(response).writeKeyValue("i", "Secret");
                verify(response).writeKeyValue("l", "ctlg_layout1");
                verify(response).writeKeyValue("g", "");
                verify(response, never()).write("p:");
            }
            verify(catalogue, never()).getCataloguePageItems(anyInt());
        }
    }
}
