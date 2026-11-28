package org.alexdev.kepler.game.catalogue;

import org.alexdev.kepler.game.player.PlayerRank;
import org.alexdev.kepler.util.LocaleUtil;
import org.alexdev.kepler.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class CataloguePage {
    private int id;
    private PlayerRank minRole;
    private boolean indexVisible;
    private boolean isClubOnly;
    private String nameIndex;
    private String linkList;
    private String name;
    private String layout;
    private String imageHeadline;
    private String imageTeasers;
    private String body;
    private String labelPick;
    private String labelExtra_s;
    private Map<String, String> labelExtra;
    private volatile Map<String, Text> translations = Map.of();
    private volatile Text baseText;

    public CataloguePage(int id, PlayerRank minRole, boolean indexVisible, boolean isClubOnly, String nameIndex, String linkedList, String name, String layout, String imageHeadline, String imageTeasers, String body, String labelPick, String labelExtra_s, String labelExtra_t) {
        this.id = id;
        this.minRole = minRole;
        this.indexVisible = indexVisible;
        this.isClubOnly = isClubOnly;
        this.nameIndex = nameIndex;
        this.linkList = linkedList;
        this.name = name;
        this.layout = layout;
        this.imageHeadline = imageHeadline;
        this.imageTeasers = imageTeasers;
        this.body = body;
        this.labelPick = labelPick;
        this.labelExtra_s = labelExtra_s;
        this.labelExtra = parseLabelExtra(labelExtra_t);
    }

    /**
     * Parse the label_extra_t column ("id:text" per line) into "label_extra_t_id" keys.
     */
    public static Map<String, String> parseLabelExtra(String labelExtra_t) {
        Map<String, String> labelExtra = new HashMap<>();

        if (!StringUtil.isNullOrEmpty(labelExtra_t)) {
            String[] extraTypedData = labelExtra_t.split("\r\n");

            for (String dbExtraData : extraTypedData) {
                String extraData = StringUtil.filterInput(dbExtraData, true);

                String extraId = extraData.substring(0, extraData.indexOf(':'));
                String data = extraData.substring(extraId.length() + 1);

                labelExtra.put("label_extra_t_" + extraId, data);
            }
        }

        return labelExtra;
    }

    /**
     * The texts of a page in one language. Immutable.
     */
    public static final class Text {
        private final String name;
        private final String body;
        private final String labelPick;
        private final String labelExtra_s;
        private final Map<String, String> labelExtra;

        public Text(String name, String body, String labelPick, String labelExtra_s, Map<String, String> labelExtra) {
            this.name = name;
            this.body = body == null ? "" : body;
            this.labelPick = labelPick;
            this.labelExtra_s = labelExtra_s;
            this.labelExtra = Map.copyOf(labelExtra);
        }

        public String getName() { return name; }
        public String getBody() { return body; }
        public String getLabelPick() { return labelPick; }
        public String getLabelExtra_s() { return labelExtra_s; }
        public Map<String, String> getLabelExtra() { return labelExtra; }
    }

    /**
     * Texts of the page in the given locale; any column without a translation
     * (null or empty) falls back to the Spanish text of the base table.
     */
    public Text getText(String locale) {
        Text translated = this.translations.get(locale);

        if (translated != null) {
            return translated;
        }

        Text base = this.baseText;

        if (base == null || base.getName() != this.name || base.getBody() != this.getBody()) {
            base = new Text(this.name, this.getBody(), this.labelPick, this.labelExtra_s, this.labelExtra);
            this.baseText = base;
        }

        return base;
    }

    /**
     * Build the translated texts from raw columns (null/empty = Spanish value).
     */
    public Text translate(String name, String body, String labelPick, String labelExtra_s, String labelExtra_t) {
        Map<String, String> extra = StringUtil.isNullOrEmpty(labelExtra_t) || labelExtra_t.trim().isEmpty()
                ? this.labelExtra : parseLabelExtra(labelExtra_t);

        return new Text(LocaleUtil.pick(name, this.name), LocaleUtil.pick(body, this.getBody()),
                LocaleUtil.pick(labelPick, this.labelPick), LocaleUtil.pick(labelExtra_s, this.labelExtra_s), extra);
    }

    /**
     * Publish the translations (locale -> texts). Call before sharing the page.
     */
    public void setTranslations(Map<String, Text> translations) {
        this.translations = Map.copyOf(translations);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public PlayerRank getMinRole() {
        return minRole;
    }

    public boolean isIndexVisible() {
        return indexVisible;
    }

    public String getNameIndex() {
        return nameIndex;
    }

    public String getLinkList() {
        return linkList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLayout() {
        return layout;
    }

    public String getImageHeadline() {
        return imageHeadline;
    }

    public String getImageTeasers() {
        return imageTeasers == null ? "" : imageTeasers;
    }

    public String getBody() {
        return body == null ? "" : body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLabelPick() {
        return labelPick;
    }

    public String getLabelExtra_s() {
        return labelExtra_s;
    }

    public Map<String, String> getLabelExtra() {
        return labelExtra;
    }

    public boolean isClubOnly() {
        return isClubOnly;
    }
}
