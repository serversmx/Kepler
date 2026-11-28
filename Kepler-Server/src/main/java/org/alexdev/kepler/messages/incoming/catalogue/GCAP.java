package org.alexdev.kepler.messages.incoming.catalogue;

import org.alexdev.kepler.game.catalogue.CatalogueItem;
import org.alexdev.kepler.game.catalogue.CatalogueManager;
import org.alexdev.kepler.game.catalogue.CataloguePage;
import org.alexdev.kepler.game.catalogue.RareManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerRank;
import org.alexdev.kepler.messages.outgoing.catalogue.CATALOGUE_PAGE;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.DateUtil;
import org.alexdev.kepler.util.LocaleUtil;
import org.alexdev.kepler.util.config.GameConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class GCAP implements MessageEvent {
    @Override
    public void handle(Player player, NettyRequest reader) {
        String pageName = reader.contents().split("/")[1];

        CatalogueManager catalogue = CatalogueManager.getInstance();
        CataloguePage cataloguePage = catalogue.getCataloguePage(pageName);

        if (cataloguePage == null || player.getDetails().getRank().getRankId() < cataloguePage.getMinRole().getRankId()) {
            // A valid, empty page reaches ChangeWindowView -> hideLoadingScreen.
            // Never serialize the restricted page's products, text or artwork.
            CataloguePage unavailable = new CataloguePage(0, PlayerRank.NORMAL, false, false,
                    pageName, "", "Pagina no disponible", "ctlg_layout1", "", "",
                    "Esta pagina no esta disponible.", "", "", "");
            player.send(new CATALOGUE_PAGE(unavailable, List.of(), player.getDetails().getLocale()));
            return;
        }

        if (player.getDetails().getRank().getRankId() >= cataloguePage.getMinRole().getRankId()) {
            List<CatalogueItem> catalogueItemList = catalogue.getCataloguePageItems(cataloguePage.getId());

            String locale = player.getDetails().getLocale();
            String bodyOverride = null;

            var rareSnapshot = RareManager.getInstance().getSnapshot();

            if (rareSnapshot.getRare() != null && rareSnapshot.getPrice() != null &&
                    cataloguePage.getId() == GameConfiguration.getInstance().getInteger("rare.cycle.page.id")) {

                var currentRare = rareSnapshot.getRare();

                var rareItem = currentRare.copy();
                rareItem.setPrice(rareSnapshot.getPrice());
                catalogueItemList = List.of(rareItem);

                TimeUnit rareManagerUnit = TimeUnit.valueOf(GameConfiguration.getInstance().getString("rare.cycle.refresh.timeunit"));

                long interval = rareManagerUnit.toSeconds(GameConfiguration.getInstance().getInteger("rare.cycle.refresh.interval"));
                long currentTick = RareManager.getInstance().getTick().get();
                long timeUntil = interval - currentTick;

                // Never mutate the shared page: other players read it concurrently in other languages.
                bodyOverride = getRarePageText(locale).replace("{rareCountdown}", DateUtil.getReadableSeconds(timeUntil, locale));
            }

            //Ai:Recycler{13}n:Recycler{13}l:ctlg_recycler{13}g:catalog_recycler_headline1{13}{1}
            player.send(new CATALOGUE_PAGE(
                    cataloguePage,
                    catalogueItemList,
                    locale,
                    bodyOverride));
        }
    }

    /**
     * Rare cycle page text for the locale: rare.cycle.page.text.&lt;locale&gt;, falling back to the Spanish setting.
     */
    static String getRarePageText(String locale) {
        String spanish = GameConfiguration.getInstance().getString("rare.cycle.page.text");

        if (locale == null || locale.equals(LocaleUtil.DEFAULT_LOCALE)) {
            return spanish;
        }

        return LocaleUtil.pick(GameConfiguration.getInstance().getString("rare.cycle.page.text." + locale, ""), spanish);
    }
}
