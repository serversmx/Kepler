package org.alexdev.kepler.messages.incoming.catalogue;

import org.alexdev.kepler.dao.mysql.CurrencyDao;
import org.alexdev.kepler.game.catalogue.*;
import org.alexdev.kepler.game.player.*;
import org.alexdev.kepler.messages.outgoing.catalogue.*;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

class GRPCTest {
    private final Player player = mock(Player.class);
    private final PlayerDetails details = mock(PlayerDetails.class);
    private final CatalogueManager catalogue = mock(CatalogueManager.class);
    private final RareManager rares = mock(RareManager.class);
    private final GameConfiguration config = mock(GameConfiguration.class);
    private final CataloguePage page = mock(CataloguePage.class);
    private final CatalogueItem item = mock(CatalogueItem.class);
    private MockedStatic<CatalogueManager> catalogueStatic;
    private MockedStatic<RareManager> rareStatic;
    private MockedStatic<GameConfiguration> configStatic;

    @BeforeEach void setup() {
        catalogueStatic = mockStatic(CatalogueManager.class);
        rareStatic = mockStatic(RareManager.class);
        configStatic = mockStatic(GameConfiguration.class);
        catalogueStatic.when(CatalogueManager::getInstance).thenReturn(catalogue);
        rareStatic.when(RareManager::getInstance).thenReturn(rares);
        configStatic.when(GameConfiguration::getInstance).thenReturn(config);
        when(rares.getSnapshot()).thenReturn(new RareManager.RareSnapshot(null, java.util.Map.of()));
        when(player.getDetails()).thenReturn(details);
        when(details.getRank()).thenReturn(PlayerRank.NORMAL);
        when(catalogue.getCatalogueItem("chair")).thenReturn(item);
        when(catalogue.getCataloguePage("Public")).thenReturn(page);
        when(page.getId()).thenReturn(10);
        when(page.getMinRole()).thenReturn(PlayerRank.NORMAL);
        when(item.hasPage(10)).thenReturn(true);
        when(item.getPrice()).thenReturn(5);
    }
    @AfterEach void cleanup() {
        configStatic.close(); rareStatic.close(); catalogueStatic.close();
    }
    private void buy(String content) throws Exception {
        NettyRequest request = mock(NettyRequest.class);
        when(request.contents()).thenReturn(content);
        try (MockedStatic<CurrencyDao> currency = mockStatic(CurrencyDao.class)) {
            new GRPC().handle(player, request);
            currency.verifyNoInteractions();
        }
        verify(catalogue, never()).purchase(any(), any(), any(), any(), anyLong());
    }
    private void buy() throws Exception { buy("production\rPublic\ren\rchair\r\r0"); }

    @Test void missingItemAndMalformedOrderSendError() throws Exception {
        when(catalogue.getCatalogueItem("chair")).thenReturn(null);
        buy();
        buy("bad");
        verify(player, times(2)).send(isA(PURCHASE_ERROR.class));
    }
    @Test void rankIsCheckedEvenWithoutActiveRare() throws Exception {
        when(page.getMinRole()).thenReturn(PlayerRank.ADMINISTRATOR);
        buy();
        verify(player).send(isA(PURCHASE_ERROR.class));
    }
    @Test void unrelatedPageCannotBypassClubGate() throws Exception {
        when(item.hasPage(10)).thenReturn(false);
        buy();
        verify(player).send(isA(PURCHASE_ERROR.class));
    }
    @Test void expiredClubSends296WithIntegerOne() throws Exception {
        when(page.isClubOnly()).thenReturn(true);
        buy();
        verify(player).send(isA(PURCHASE_NOT_ALLOWED.class));
        NettyResponse response = mock(NettyResponse.class);
        new PURCHASE_NOT_ALLOWED().compose(response);
        verify(response).writeInt(1);
        Assertions.assertEquals(296, new PURCHASE_NOT_ALLOWED().getHeader());
    }
    @Test void activeClubCanReachTheBalanceCheck() throws Exception {
        when(page.isClubOnly()).thenReturn(true);
        when(details.hasClubSubscription()).thenReturn(true);
        buy();
        verify(player).send(isA(NO_CREDITS.class));
    }
    @Test void insufficientCreditsKeepTheExisting68Response() throws Exception {
        buy();
        verify(player).send(isA(NO_CREDITS.class));
        Assertions.assertEquals(68, new NO_CREDITS().getHeader());
        Assertions.assertEquals(65, new PURCHASE_ERROR().getHeader());
    }
    @Test void currentRareUsesPublicDisplayPageAndPrice() throws Exception {
        when(rares.getSnapshot()).thenReturn(new RareManager.RareSnapshot(item, java.util.Map.of(item, 25)));
        when(item.hasPage(10)).thenReturn(false);
        when(config.getInteger("rare.cycle.page.id")).thenReturn(10);
        when(details.getCredits()).thenReturn(20);
        buy();
        verify(player).send(isA(NO_CREDITS.class));
    }
    @Test void rareDisplayPageStillChecksRank() throws Exception {
        when(rares.getSnapshot()).thenReturn(new RareManager.RareSnapshot(item, java.util.Map.of(item, 25)));
        when(config.getInteger("rare.cycle.page.id")).thenReturn(10);
        when(page.getMinRole()).thenReturn(PlayerRank.ADMINISTRATOR);
        buy();
        verify(player).send(isA(PURCHASE_ERROR.class));
    }
    @Test void currentRareWithoutPriceIsNotPurchasable() throws Exception {
        when(rares.getSnapshot()).thenReturn(new RareManager.RareSnapshot(item, java.util.Map.of()));
        when(config.getInteger("rare.cycle.page.id")).thenReturn(10);
        when(details.getCredits()).thenReturn(1000);
        buy();
        verify(player).send(isA(PURCHASE_ERROR.class));
    }
    @Test void transactionLogGetsTheChargedRarePriceNotTheCatalogueOne() throws Exception {
        when(rares.getSnapshot()).thenReturn(new RareManager.RareSnapshot(item, java.util.Map.of(item, 25)));
        when(config.getInteger("rare.cycle.page.id")).thenReturn(10);
        when(details.getCredits()).thenReturn(1000);
        when(catalogue.purchase(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(java.util.List.of());

        NettyRequest request = mock(NettyRequest.class);
        when(request.contents()).thenReturn("production\rPublic\ren\rchair\r\r0");
        try (MockedStatic<CurrencyDao> currency = mockStatic(CurrencyDao.class)) {
            new GRPC().handle(player, request);
            currency.verify(() -> CurrencyDao.decreaseCredits(details, 25));
        }

        // catalogue price is 5, the rare cycle charges 25: the log must say 25
        verify(catalogue).purchase(eq(player), eq(item), any(), isNull(), anyLong(), eq(25));
    }
}
