package org.alexdev.kepler.messages.outgoing.catalogue;

import org.alexdev.kepler.game.catalogue.CataloguePage;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

import org.alexdev.kepler.util.LocaleUtil;

import java.util.List;

public class CATALOGUE_PAGES extends MessageComposer {
    private final List<CataloguePage> cataloguePages;

    private final String locale;

    public CATALOGUE_PAGES(List<CataloguePage> cataloguePages) {
        this(cataloguePages, LocaleUtil.DEFAULT_LOCALE);
    }

    public CATALOGUE_PAGES(List<CataloguePage> cataloguePages, String locale) {
        this.cataloguePages = cataloguePages;
        this.locale = LocaleUtil.normalize(locale);
    }

    @Override
    public void compose(NettyResponse response) {
        for (CataloguePage page : this.cataloguePages) {
            response.writeDelimeter(page.getNameIndex(), (char) 9);
            response.writeDelimeter(page.getText(this.locale).getName(), (char) 13);
        }
    }

    @Override
    public short getHeader() {
        return 126; // "A~"
    }
}
