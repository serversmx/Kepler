package org.alexdev.kepler.game.catalogue;

import org.alexdev.kepler.dao.Storage;
import org.alexdev.kepler.dao.mysql.RareDao;
import org.alexdev.kepler.dao.mysql.SettingsDao;
import org.alexdev.kepler.util.DateUtil;
import org.alexdev.kepler.util.config.GameConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RareManager {
    private static final Logger log = LoggerFactory.getLogger(RareManager.class);
    private static RareManager instance;

    static final String RARE_TICK_SETTING = "rare.cycle.tick.time";

    /** Sale code of the rare on sale, persisted so a restart (or the CMS) knows the real current rare. */
    static final String RARE_CURRENT_SETTING = "rare.cycle.current";

    private LinkedList<CatalogueItem> rareList;
    /** Rare on sale + price table, always replaced together so readers never mix old and new. */
    private final AtomicReference<RareSnapshot> snapshot = new AtomicReference<>(RareSnapshot.EMPTY);
    private Map<String, Long> daysSinceUsed;

    private Long currentRareTime;
    private AtomicLong tickTime;

    public RareManager() {
        this.rareList = new LinkedList<>();
        this.daysSinceUsed = new LinkedHashMap<>();
        this.tickTime = new AtomicLong(GameConfiguration.getInstance().getLong(RARE_TICK_SETTING));

        try {
            Map<CatalogueItem, Integer> costs = this.buildRareCost();
            this.daysSinceUsed = RareDao.getUsedRares();

            // Prefer the persisted current rare; fall back to the most recently used one (older installs).
            CatalogueItem candidate = this.resolveSavedCurrentRare();

            if (candidate == null && this.daysSinceUsed.size() > 0) {
                var currentItemData = RareDao.getCurrentRare();
                candidate = CatalogueManager.getInstance().getCatalogueItem(currentItemData.getKey());
                this.currentRareTime = currentItemData.getValue(); // Get the active item
            }

            // A rare without a price (its page left rare.cycle.pages) can't be sold: draw another one.
            if (candidate != null && !costs.containsKey(candidate)) {
                candidate = null;
            }

            this.snapshot.set(new RareSnapshot(candidate, costs));
            this.loadRares();

            // If there was no current rare, or the current rare time ran out, then cycle to the next rare
            if (candidate == null) {
                this.selectNewRare();
            }

        } catch (Exception ex) {
            Storage.logError(ex);
        }
    }

    /**
     * Re-read the rare settings and the catalogue without restarting the server: rebuilds the
     * price table (rare.cycle.pages x rare.cycle.credits.per.hour) and the pool, and re-points the
     * current rare at the freshly loaded catalogue instance. Intervals and reuse windows are read
     * live from the configuration, so they need no work here. The tick (time already elapsed on the
     * current rare) is kept, so changing the interval never restarts the countdown by itself.
     *
     * Call it after CatalogueManager.reset() and/or GameConfiguration.reset().
     */
    public synchronized void reload() {
        try {
            Map<CatalogueItem, Integer> newCost = this.buildRareCost();
            CatalogueItem previous = this.snapshot.get().getRare();

            CatalogueItem resolved = previous != null
                    ? CatalogueManager.getInstance().getCatalogueItem(previous.getSaleCode())
                    : this.resolveSavedCurrentRare();

            // The rare on sale must have a price, otherwise buying it would fail; pick another one.
            boolean keepCurrent = resolved != null && newCost.containsKey(resolved);

            // One atomic swap of price table + current rare: a purchase never sees a mismatch.
            this.snapshot.set(new RareSnapshot(keepCurrent ? resolved : null, newCost));
            this.loadRares();

            if (!keepCurrent) {
                this.selectNewRare();
            }
        } catch (Exception ex) {
            Storage.logError(ex);
        }
    }

    /**
     * Parse "pageId,hours|pageId,hours" into page -> hours. Malformed entries and entries with
     * non-positive hours are ignored instead of aborting the whole setting.
     */
    static Map<Integer, Integer> parsePages(String raw) {
        Map<Integer, Integer> pages = new LinkedHashMap<>();

        if (raw == null) {
            return pages;
        }

        for (String entry : raw.split("\\|")) {
            String[] parts = entry.trim().split(",");

            if (parts.length != 2) {
                continue;
            }

            try {
                int pageId = Integer.parseInt(parts[0].trim());
                int hours = Integer.parseInt(parts[1].trim());

                if (hours > 0) {
                    pages.put(pageId, hours);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }

        return pages;
    }

    private Map<CatalogueItem, Integer> buildRareCost() {
        Map<CatalogueItem, Integer> costs = new HashMap<>();

        for (var page : parsePages(GameConfiguration.getInstance().getString("rare.cycle.pages")).entrySet()) {
            for (CatalogueItem item : CatalogueManager.getInstance().getCataloguePageItems(page.getKey())) {
                costs.put(item, getHandoutAmountInHours(page.getValue()));
            }
        }

        return costs;
    }

    private CatalogueItem resolveSavedCurrentRare() {
        String saleCode = GameConfiguration.getInstance().getString(RARE_CURRENT_SETTING, "");

        if (saleCode == null || saleCode.isBlank()) {
            return null;
        }

        return CatalogueManager.getInstance().getCatalogueItem(saleCode);
    }

    /**
     * Finds all rares that can't be accessed normally by the user, and then shuffles the list.
     */
    private void loadRares() {
        this.rareList = new LinkedList<>();

        for (CataloguePage cataloguePage : CatalogueManager.getInstance().getCataloguePages()) {
            // Skip pages where normal users can access
            if (!(cataloguePage.getMinRole().getRankId() > 1)) {
                continue;
            }

            // Search in rares pages only
            if (!cataloguePage.getLayout().equals("ctlg_layout2") || !cataloguePage.getImageHeadline().equals("catalog_rares_headline1")) {
                continue;
            }

            this.rareList.addAll(CatalogueManager.getInstance().getCataloguePageItems(cataloguePage.getId()));
        }

        Collections.shuffle(this.rareList);
    }

    /**
     * Pop candidates until one is not cooling down (same sprite used recently) and has a price.
     * Returns null when the list is exhausted.
     */
    static CatalogueItem pickNext(Deque<CatalogueItem> pool, Set<String> blockedSprites, Map<CatalogueItem, Integer> prices) {
        CatalogueItem candidate;

        while ((candidate = pool.pollFirst()) != null) {
            if (candidate.getDefinition() == null || blockedSprites.contains(candidate.getDefinition().getSprite())) {
                continue;
            }

            if (!prices.containsKey(candidate)) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    /**
     * Selects a new rare, adds it to the database so it can only be selected once every X interval defined (default is 3 days).
     * If every rare of the pool is cooling down, the current rare is kept.
     *
     * @return true when a new rare was selected
     */
    public synchronized boolean selectNewRare() throws SQLException {
        TimeUnit reuseTimeUnit = TimeUnit.valueOf(GameConfiguration.getInstance().getString("rare.cycle.reuse.timeunit"));
        long interval = reuseTimeUnit.toSeconds(GameConfiguration.getInstance().getInteger("rare.cycle.reuse.interval"));

        List<String> toRemove = new ArrayList<>();

        // Remove expired rares
        for (var kvp : this.daysSinceUsed.entrySet()) {
            if (DateUtil.getCurrentTimeSeconds() > kvp.getValue()) {
                toRemove.add(kvp.getKey());
            }
        }

        for (var sprite : toRemove) {
            this.daysSinceUsed.remove(sprite);
        }

        if (!toRemove.isEmpty()) {
            RareDao.removeRares(toRemove);
        }

        Set<String> blocked = this.daysSinceUsed.keySet();
        Map<CatalogueItem, Integer> costs = this.snapshot.get().getCosts();
        CatalogueItem rare = pickNext(this.rareList, blocked, costs);

        // The shuffled list ran out: reload it once and look again.
        if (rare == null) {
            this.loadRares();
            rare = pickNext(this.rareList, blocked, costs);
        }

        if (rare == null) {
            log.warn("[RareManager] No rare available (pool empty, all cooling down or without price); keeping the current rare");
            return false;
        }

        this.snapshot.set(new RareSnapshot(rare, costs));

        // Handle override by using "rare.cycle.reuse.CATALOGUE_SALE_CODE.timeunit" and "rare.cycle.reuse.CATALOGUE_SALE_CODE.interval"
        String overrideUnit = GameConfiguration.getInstance().getString("rare.cycle.reuse." + rare.getSaleCode() + ".timeunit", null);

        if (overrideUnit != null) {
            reuseTimeUnit = TimeUnit.valueOf(overrideUnit);
            interval = reuseTimeUnit.toSeconds(GameConfiguration.getInstance().getInteger("rare.cycle.reuse." + rare.getSaleCode() + ".interval"));
        }

        // Add rare to expiry table so it can't be used for a certain X number of days
        this.daysSinceUsed.put(rare.getDefinition().getSprite(), DateUtil.getCurrentTimeSeconds() + interval);

        RareDao.removeRares(List.of(rare.getDefinition().getSprite()));
        RareDao.addRare(rare.getDefinition().getSprite(), DateUtil.getCurrentTimeSeconds() + interval);

        this.tickTime.set(0);
        this.saveTick();
        this.saveCurrent(rare);

        log.info("[RareManager] New rare: {}", rare.getSaleCode());
        return true;
    }

    /**
     * Get the credit amount handout but in hours.
     *
     * @param hours the hours to select for
     * @return the amount of rareCost
     */
    public int getHandoutAmountInHours(int hours) {
        // Rare prices are fixed in credits per hour of play, independent of the
        // online credits handout, so changing the handout never reprices rares.
        int creditsPerHour = GameConfiguration.getInstance().getInteger("rare.cycle.credits.per.hour");

        if (creditsPerHour > 0) {
            return hours * creditsPerHour;
        }

        TimeUnit unit = TimeUnit.valueOf(GameConfiguration.getInstance().getString("credits.scheduler.timeunit"));
        long interval = unit.toMinutes(GameConfiguration.getInstance().getInteger("credits.scheduler.interval"));

        long minutesInHour = 60;
        long minutes = minutesInHour / interval;

        return (int) ((hours * minutes) * GameConfiguration.getInstance().getInteger("credits.scheduler.amount"));
    }

    /**
     * Tick manager for checking expiry of the rare on sale.
     *
     * @param tickTime the global tick counter instance
     * @throws SQLException for when selectNewRare() fails
     */
    public void performRareManagerJob(AtomicLong tickTime) throws SQLException {
        // Same lock as selectNewRare()/reload(): the expiry check and the rotation are one step, so a
        // manual rotation (RCON) that resets the counter in between can't be followed by a stale rotation.
        synchronized (this) {
            TimeUnit rareManagerUnit = TimeUnit.valueOf(GameConfiguration.getInstance().getString("rare.cycle.refresh.timeunit"));
            long interval = rareManagerUnit.toSeconds(GameConfiguration.getInstance().getInteger("rare.cycle.refresh.interval"));

            this.tickTime.incrementAndGet();

            // Save tick time every 60 seconds...
            if (tickTime.get() % 60 == 0) {
                this.saveTick();
            }

            // Select new rare (expiry re-evaluated under the lock)
            if (this.tickTime.get() >= interval) {
                // Nothing available: wait a whole interval before trying again instead of every second.
                if (!this.selectNewRare()) {
                    this.tickTime.set(0);
                }
            }
        }
    }

    /**
     * Remove the colour tag from the sprite name. eg pillow*1 to pillow, used for
     * comparing the same items which are just different colours.
     *
     * @param sprite the sprite to remove the colour tag from
     * @return the new sprite
     */
    private String stripColor(String sprite) {
        return sprite.contains("*") ? sprite.split("\\*")[0] : sprite;
    }

    /**
     * Get the current random rare
     * @return the random rare
     */
    public CatalogueItem getCurrentRare() {
        return this.snapshot.get().getRare();
    }

    /**
     * Get the rares costs.
     *
     * @return the map of rares costs
     */
    public Map<CatalogueItem, Integer> getRareCost() {
        return this.snapshot.get().getCosts();
    }

    /**
     * Get the rare on sale together with its price table, read in one step. Prefer this over
     * getCurrentRare() + getRareCost(), which could mix two different generations.
     */
    public RareSnapshot getSnapshot() {
        return this.snapshot.get();
    }

    /**
     * Get the {@link RareManager} instance
     *
     * @return the rare manager instance
     */
    public static RareManager getInstance() {
        if (instance == null) {
            instance = new RareManager();
        }

        return instance;
    }

    /**
     * Get the current tick.
     * @return the tick time
     */
    public AtomicLong getTick() {
        return tickTime;
    }

    /**
     * Save the tick time to database
     */
    public void saveTick() {
        GameConfiguration.getInstance().getConfig().put(RARE_TICK_SETTING, String.valueOf(this.tickTime.get()));
        SettingsDao.updateSetting(RARE_TICK_SETTING, GameConfiguration.getInstance().getString(RARE_TICK_SETTING));
    }

    /**
     * Save the sale code of the rare on sale, so a restart and the CMS know which one it is.
     */
    private void saveCurrent(CatalogueItem rare) {
        if (rare == null) {
            return;
        }

        GameConfiguration.getInstance().getConfig().put(RARE_CURRENT_SETTING, rare.getSaleCode());
        SettingsDao.updateSetting(RARE_CURRENT_SETTING, rare.getSaleCode());
    }

    /**
     * Immutable pair of the rare on sale and the price table it was built with.
     */
    public static final class RareSnapshot {
        static final RareSnapshot EMPTY = new RareSnapshot(null, Map.of());

        private final CatalogueItem rare;
        private final Map<CatalogueItem, Integer> costs;

        public RareSnapshot(CatalogueItem rare, Map<CatalogueItem, Integer> costs) {
            this.rare = rare;
            this.costs = Collections.unmodifiableMap(new HashMap<>(costs));
        }

        /** The rare on sale, or null. */
        public CatalogueItem getRare() {
            return rare;
        }

        public Map<CatalogueItem, Integer> getCosts() {
            return costs;
        }

        /** Price of the rare on sale in credits, or null when there is none or it has no price. */
        public Integer getPrice() {
            return rare == null ? null : costs.get(rare);
        }
    }
}
