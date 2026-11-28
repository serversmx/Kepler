package org.alexdev.kepler.messages.outgoing.catalogue;

import org.alexdev.kepler.game.catalogue.CatalogueItem;
import org.alexdev.kepler.game.catalogue.CataloguePackage;
import org.alexdev.kepler.game.catalogue.CataloguePage;
import org.alexdev.kepler.game.item.base.ItemBehaviour;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.LocaleUtil;
import org.alexdev.kepler.util.StringUtil;

import java.util.List;

public class CATALOGUE_PAGE extends MessageComposer {
    private final List<CatalogueItem> catalogueItems;
    private final CataloguePage page;

    private final String locale;
    private final String bodyOverride;

    public CATALOGUE_PAGE(CataloguePage cataloguePage, List<CatalogueItem> cataloguePageItems) {
        this(cataloguePage, cataloguePageItems, LocaleUtil.DEFAULT_LOCALE, null);
    }

    public CATALOGUE_PAGE(CataloguePage cataloguePage, List<CatalogueItem> cataloguePageItems, String locale) {
        this(cataloguePage, cataloguePageItems, locale, null);
    }

    /**
     * @param bodyOverride already localised body replacing the page body (rare cycle countdown), or null
     */
    public CATALOGUE_PAGE(CataloguePage cataloguePage, List<CatalogueItem> cataloguePageItems, String locale, String bodyOverride) {
        this.page = cataloguePage;
        this.catalogueItems = cataloguePageItems;
        this.locale = LocaleUtil.normalize(locale);
        this.bodyOverride = bodyOverride;
    }

    @Override
    public void compose(NettyResponse response) {
        //Ai:Recycler{13}n:Recycler{13}l:ctlg_recycler{13}g:catalog_recycler_headline1{13}
        CataloguePage.Text text = this.page.getText(this.locale);

        response.writeKeyValue("i", this.page.getNameIndex());
        response.writeKeyValue("n", text.getName());
        response.writeKeyValue("l", this.page.getLayout());
        response.writeKeyValue("g", this.page.getImageHeadline());

        if (this.page.getImageTeasers() != null && this.page.getImageTeasers().length() > 0) {
            response.writeKeyValue("e", this.page.getImageTeasers());
        }

        String body = this.bodyOverride != null ? this.bodyOverride : text.getBody();

        if (!StringUtil.isNullOrEmpty(body)) {
            response.writeKeyValue("h", body);
        }

        if (this.page.getLinkList().length() > 0) {
            response.writeKeyValue("u", this.page.getLinkList());
        }

        if (!StringUtil.isNullOrEmpty(text.getLabelPick())) {
            response.writeKeyValue("w", text.getLabelPick());
        }

        if (!StringUtil.isNullOrEmpty(text.getLabelExtra_s())) {
            response.writeKeyValue("s", text.getLabelExtra_s());
        }

        for (int labelDataId = 1; labelDataId < 11; labelDataId++) {
            String extraDataId = "label_extra_t_" + labelDataId;

            if (!text.getLabelExtra().containsKey(extraDataId)) {
                continue;
            }

            response.writeKeyValue("t" + labelDataId, text.getLabelExtra().get(extraDataId));
        }

        for (CatalogueItem item : this.catalogueItems) {
            response.write("p:");
            response.writeDelimeter(item.getName(this.locale), (char) 9);
            response.writeDelimeter(item.getDescription(this.locale), (char) 9);
            response.writeDelimeter(item.getPrice(), (char) 9);
            response.writeDelimeter("", (char) 9);
            response.writeDelimeter(item.getType(), (char) 9);
            response.writeDelimeter(item.getIcon(), (char) 9);
            response.writeDelimeter(item.getSize(), (char) 9);
            response.writeDelimeter(item.getDimensions(), (char) 9);
            response.writeDelimeter(item.getSaleCode(), (char) 9);

            if (item.isPackage() || item.getDefinition().getSprite().equals("poster")) {
                response.writeDelimeter("", (char) 9);
            }

            if (item.isPackage()) {
                response.writeDelimeter(item.getPackages().size(), (char) 9);

                for (CataloguePackage cataloguePackage : item.getPackages()) {
                    response.writeDelimeter(cataloguePackage.getDefinition().getIcon(cataloguePackage.getSpecialSpriteId()), (char) 9);
                    response.writeDelimeter(cataloguePackage.getAmount(), (char) 9);
                    response.writeDelimeter(cataloguePackage.getDefinition().getColour(), (char) 9);
                }

            } else if (!item.getDefinition().hasBehaviour(ItemBehaviour.WALL_ITEM)) {
                response.writeDelimeter(item.getDefinition().getColour(), (char) 9);
            }

            response.write(Character.toString((char) 13));
        }
    }

    @Override
    public short getHeader() {
        return 127; // "A"
    }
}
