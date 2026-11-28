package org.alexdev.kepler.game.catalogue;

import org.alexdev.kepler.dao.mysql.RareDao;
import org.alexdev.kepler.dao.mysql.SettingsDao;
import org.alexdev.kepler.game.item.base.ItemDefinition;
import org.alexdev.kepler.game.player.PlayerRank;
import org.alexdev.kepler.server.rcon.messages.RconHeader;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RareManagerTest {
    private final CatalogueManager catalogue = mock(CatalogueManager.class);
    private final GameConfiguration config = mock(GameConfiguration.class);
    private final Map<String, String> settings = new HashMap<>();
    private MockedStatic<CatalogueManager> catalogueStatic;
    private MockedStatic<GameConfiguration> configStatic;
    private MockedStatic<RareDao> rareDao;
    private MockedStatic<SettingsDao> settingsDao;

    private static CatalogueItem item(String saleCode, String sprite) {
        CatalogueItem item = mock(CatalogueItem.class);
        ItemDefinition definition = mock(ItemDefinition.class);
        when(definition.getSprite()).thenReturn(sprite);
        when(item.getSaleCode()).thenReturn(saleCode);
        when(item.getDefinition()).thenReturn(definition);
        return item;
    }

    @BeforeEach
    void setup() throws Exception {
        catalogueStatic = mockStatic(CatalogueManager.class);
        configStatic = mockStatic(GameConfiguration.class);
        rareDao = mockStatic(RareDao.class);
        settingsDao = mockStatic(SettingsDao.class);

        catalogueStatic.when(CatalogueManager::getInstance).thenReturn(catalogue);
        configStatic.when(GameConfiguration::getInstance).thenReturn(config);

        settings.put("rare.cycle.tick.time", "0");
        settings.put("rare.cycle.pages", "10,3|11,6");
        settings.put("rare.cycle.credits.per.hour", "100");
        settings.put("rare.cycle.reuse.timeunit", "DAYS");
        settings.put("rare.cycle.reuse.interval", "7");
        settings.put("rare.cycle.current", "");

        when(config.getString(anyString())).thenAnswer(i -> settings.getOrDefault(i.getArgument(0), i.getArgument(0)));
        when(config.getString(anyString(), any())).thenAnswer(i -> settings.getOrDefault(i.getArgument(0), i.getArgument(1)));
        when(config.getInteger(anyString())).thenAnswer(i -> Integer.parseInt(settings.getOrDefault(i.getArgument(0), "0")));
        when(config.getLong(anyString())).thenAnswer(i -> Long.parseLong(settings.getOrDefault(i.getArgument(0), "0")));
        when(config.getConfig()).thenReturn(settings);

        rareDao.when(RareDao::getUsedRares).thenReturn(new LinkedHashMap<>());
    }

    @AfterEach
    void cleanup() {
        settingsDao.close();
        rareDao.close();
        configStatic.close();
        catalogueStatic.close();
    }

    private CataloguePage rarePage(int id) {
        CataloguePage page = mock(CataloguePage.class);
        when(page.getId()).thenReturn(id);
        when(page.getMinRole()).thenReturn(PlayerRank.ADMINISTRATOR);
        when(page.getLayout()).thenReturn("ctlg_layout2");
        when(page.getImageHeadline()).thenReturn("catalog_rares_headline1");
        return page;
    }

    @Test
    void parsesPagesAndSkipsMalformedEntries() {
        Map<Integer, Integer> pages = RareManager.parsePages("28,3|x,4|30|31,0|32,-1|33, 6 |");

        assertThat(pages).containsExactly(Map.entry(28, 3), Map.entry(33, 6));
        assertThat(RareManager.parsePages(null)).isEmpty();
        assertThat(RareManager.parsePages("")).isEmpty();
    }

    @Test
    void pickNextSkipsCoolingDownSpritesAndUnpricedItems() {
        CatalogueItem cooling = item("a", "dragon");
        CatalogueItem noPrice = item("b", "lamp");
        CatalogueItem ok = item("c", "throne");

        Deque<CatalogueItem> pool = new ArrayDeque<>(List.of(cooling, noPrice, ok));
        Map<CatalogueItem, Integer> prices = Map.of(cooling, 240, ok, 480);

        assertThat(RareManager.pickNext(pool, Set.of("dragon"), prices)).isSameAs(ok);
        assertThat(RareManager.pickNext(pool, Set.of("dragon"), prices)).isNull();
    }

    @Test
    void rotateHeaderResolves() {
        assertThat(RconHeader.getByHeader("rotate_rare")).isEqualTo(RconHeader.ROTATE_RARE);
        assertThat(RconHeader.getByHeader("ROTATE_RARE")).isEqualTo(RconHeader.ROTATE_RARE);
    }

    @Test
    void rotatingPicksAPricedRareSavesItAndResetsTheTick() throws Exception {
        CatalogueItem a = item("rare_a", "sprite_a");
        CatalogueItem b = item("rare_b", "sprite_b");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(a, b));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());

        RareManager manager = new RareManager();
        CatalogueItem first = manager.getCurrentRare();
        assertThat(first).isNotNull();
        assertThat(manager.getRareCost().get(first)).isEqualTo(300); // 3h x 100 credits/h

        manager.getTick().set(5000);
        assertThat(manager.selectNewRare()).isTrue();

        CatalogueItem second = manager.getCurrentRare();
        assertThat(second).isNotSameAs(first); // the first one's sprite is cooling down
        assertThat(manager.getTick().get()).isZero();
        assertThat(settings.get("rare.cycle.current")).isEqualTo(second.getSaleCode());
        settingsDao.verify(() -> SettingsDao.updateSetting("rare.cycle.current", second.getSaleCode()));
    }

    @Test
    void rotatingKeepsTheCurrentRareWhenEverythingIsCoolingDown() throws Exception {
        CatalogueItem only = item("rare_a", "sprite_a");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(only));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());

        RareManager manager = new RareManager();
        assertThat(manager.getCurrentRare()).isSameAs(only);

        assertThat(manager.selectNewRare()).isFalse();
        assertThat(manager.getCurrentRare()).isSameAs(only);
    }

    @Test
    void reloadRepricesAndRepointsTheCurrentRareWithoutRestart() throws Exception {
        CatalogueItem old = item("rare_a", "sprite_a");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(old));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());

        RareManager manager = new RareManager();
        assertThat(manager.getCurrentRare()).isSameAs(old);
        assertThat(manager.getRareCost().get(old)).isEqualTo(300);
        manager.getTick().set(1234);

        // Catalogue reloaded (new instances) and prices changed in the settings.
        CatalogueItem fresh = item("rare_a", "sprite_a");
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(fresh));
        when(catalogue.getCatalogueItem("rare_a")).thenReturn(fresh);
        settings.put("rare.cycle.credits.per.hour", "120");
        settings.put("rare.cycle.pages", "10,4");

        manager.reload();

        assertThat(manager.getCurrentRare()).isSameAs(fresh);
        assertThat(manager.getRareCost().get(fresh)).isEqualTo(480); // 4h x 120
        assertThat(manager.getRareCost()).doesNotContainKey(old);
        assertThat(manager.getTick().get()).isEqualTo(1234); // countdown untouched
    }

    @Test
    void reloadRotatesWhenTheCurrentRareLosesItsPage() throws Exception {
        CatalogueItem a = item("rare_a", "sprite_a");
        CatalogueItem b = item("rare_b", "sprite_b");
        List<CataloguePage> pages = List.of(rarePage(10), rarePage(11));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(a));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of(b));

        RareManager manager = new RareManager();
        CatalogueItem current = manager.getCurrentRare();
        CatalogueItem other = current == a ? b : a;
        int otherPage = other == a ? 10 : 11;

        // The page of the current rare is removed from the priced pool.
        settings.put("rare.cycle.pages", otherPage + ",3");
        when(catalogue.getCatalogueItem(current.getSaleCode())).thenReturn(current);

        manager.reload();

        assertThat(manager.getCurrentRare()).isSameAs(other);
        assertThat(manager.getRareCost()).containsOnlyKeys(other);
    }

    @Test
    void startupDropsASavedRareThatNoLongerHasAPrice() throws Exception {
        CatalogueItem stale = item("rare_gone", "sprite_gone"); // its page is not in rare.cycle.pages
        CatalogueItem priced = item("rare_a", "sprite_a");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(priced));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());
        when(catalogue.getCatalogueItem("rare_gone")).thenReturn(stale);
        settings.put("rare.cycle.current", "rare_gone");

        RareManager manager = new RareManager();

        assertThat(manager.getCurrentRare()).isSameAs(priced);
        assertThat(manager.getSnapshot().getPrice()).isEqualTo(300);
    }

    @Test
    void startupWithoutAnyPricedRareLeavesNoRareInsteadOfAnUnpricedOne() throws Exception {
        CatalogueItem stale = item("rare_gone", "sprite_gone");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(anyInt())).thenReturn(List.of());
        when(catalogue.getCatalogueItem("rare_gone")).thenReturn(stale);
        settings.put("rare.cycle.current", "rare_gone");

        RareManager manager = new RareManager();

        assertThat(manager.getCurrentRare()).isNull();
        assertThat(manager.getSnapshot().getPrice()).isNull();
    }

    @Test
    void readersAlwaysSeeARareTogetherWithItsPriceWhileReloading() throws Exception {
        CatalogueItem first = item("rare_a", "sprite_a");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(first));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());
        RareManager manager = new RareManager();

        AtomicReference<String> torn = new AtomicReference<>();
        AtomicLong reads = new AtomicLong();
        Thread reader = new Thread(() -> {
            // Only reads the published snapshot (no static mocks involved, which are thread-local).
            while (!Thread.currentThread().isInterrupted()) {
                var snapshot = manager.getSnapshot();
                if (snapshot.getRare() != null && snapshot.getPrice() == null) {
                    torn.set("rare without price");
                    return;
                }
                reads.incrementAndGet();
            }
        });
        reader.start();

        for (int i = 0; i < 300; i++) {
            // Every reload brings new catalogue instances, like CatalogueManager.reset() does.
            CatalogueItem fresh = item("rare_a", "sprite_a");
            when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(fresh));
            when(catalogue.getCatalogueItem("rare_a")).thenReturn(fresh);
            manager.reload();
        }

        reader.interrupt();
        reader.join(2000);

        assertThat(torn.get()).isNull();
        assertThat(reads.get()).isPositive();
        assertThat(manager.getSnapshot().getPrice()).isEqualTo(300);
    }

    @Test
    void theExpiryCheckAndTheRotationShareTheManagerLock() throws Exception {
        CatalogueItem a = item("rare_a", "sprite_a");
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(List.of(a));
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());
        RareManager manager = new RareManager();

        Thread job = new Thread(() -> {
            try {
                manager.performRareManagerJob(new AtomicLong(1));
            } catch (Throwable ignored) {
                // static mocks are thread-local: once the lock is free this thread fails, which is fine
            }
        });

        // While a rotation/reload holds the monitor, the tick job must wait instead of checking expiry.
        synchronized (manager) {
            job.start();
            long deadline = System.currentTimeMillis() + 2000;
            while (job.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertThat(job.getState()).isEqualTo(Thread.State.BLOCKED);
        }

        job.join(2000);
    }

    @Test
    void theTickJobRotatesOnceWhenTheIntervalIsReachedAndRestartsTheCountdown() throws Exception {
        List<CatalogueItem> items = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            items.add(item("rare_" + i, "sprite_" + i));
        }
        List<CataloguePage> pages = List.of(rarePage(10));
        when(catalogue.getCataloguePages()).thenReturn(pages);
        when(catalogue.getCataloguePageItems(10)).thenReturn(items);
        when(catalogue.getCataloguePageItems(11)).thenReturn(List.of());
        settings.put("rare.cycle.refresh.timeunit", "DAYS");
        settings.put("rare.cycle.refresh.interval", "1");

        RareManager manager = new RareManager();
        CatalogueItem first = manager.getCurrentRare();

        manager.getTick().set(86399); // one second to go
        manager.performRareManagerJob(new AtomicLong(1));

        assertThat(manager.getCurrentRare()).isNotSameAs(first);
        assertThat(manager.getTick().get()).isZero();

        // A manual rotation just before the tick resets the counter: the tick must not rotate again.
        CatalogueItem afterJob = manager.getCurrentRare();
        manager.getTick().set(86399);
        manager.selectNewRare();
        CatalogueItem afterManual = manager.getCurrentRare();
        manager.performRareManagerJob(new AtomicLong(1));

        assertThat(manager.getCurrentRare()).isSameAs(afterManual).isNotSameAs(afterJob);
        assertThat(manager.getTick().get()).isEqualTo(1);
    }
}
