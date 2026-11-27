package org.alexdev.kepler.messages.outgoing.purse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.game.purse.CreditLogEntry;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * hh_purse parses 209 as CR-terminated rows of TAB items
 * (date, time, credit_value, real_value, currency, transaction_system_name),
 * reading from line.count - 1 down to 1.
 */
class CREDIT_LOGTest {

    @Test
    void headerIs209() {
        assertThat(new CREDIT_LOG(List.of()).getHeader()).isEqualTo((short) 209);
    }

    @Test
    void formatsRowInMexicoCityTime() {
        // 2026-11-27 18:05 UTC = 12:05 in America/Mexico_City (UTC-6)
        CreditLogEntry entry = new CreditLogEntry("catalogue_purchase", -25, LocalDateTime.of(2026, 11, 27, 18, 5));

        assertThat(CREDIT_LOG.formatLine(entry)).isEqualTo("27-11-2026\t12:05\t-25\t0\t\tstuff_store");
    }

    @Test
    void sendsOldestFirstSoTheNewestEndsOnTopOfPageOne() {
        CreditLogEntry newest = new CreditLogEntry("dice_win", 40, LocalDateTime.of(2026, 11, 27, 6, 0));
        CreditLogEntry oldest = new CreditLogEntry("club_subscribe", -20, LocalDateTime.of(2026, 11, 26, 5, 59));

        String body = CREDIT_LOG.buildBody(List.of(newest, oldest));

        assertThat(body).isEqualTo(
                "25-11-2026\t23:59\t-20\t0\t\tclub_habbo\r" +
                "27-11-2026\t00:00\t40\t0\t\tdice_win\r");

        // Director/DirPlayer count the empty line after the last CR, so
        // "line.count - 1" lands exactly on the newest row.
        String[] lines = body.split("\r", -1);
        assertThat(lines).hasSize(3);
        assertThat(lines[lines.length - 1]).isEmpty();
        assertThat(lines[lines.length - 2]).endsWith("dice_win");
    }

    @Test
    void nullDateLeavesDateAndTimeBlank() {
        CreditLogEntry legacy = new CreditLogEntry("trade_give", 5, null);

        assertThat(CREDIT_LOG.formatLine(legacy)).isEqualTo("\t\t5\t0\t\ttrade_give");
    }

    @Test
    void systemNameNeverCarriesDelimitersOrProtocolBytes() {
        assertThat(CREDIT_LOG.systemName("Vote_Reward")).isEqualTo("vote_reward");
        assertThat(CREDIT_LOG.systemName("evil\ttype\r\u0001\u0002")).isEqualTo("eviltype");
        assertThat(CREDIT_LOG.systemName(null)).isEqualTo("web_internal");
        assertThat(CREDIT_LOG.systemName("\t\r")).isEqualTo("web_internal");
    }

    @Test
    void emptyLogSendsEmptyBody() {
        ByteBuf buffer = Unpooled.buffer();
        new CREDIT_LOG(List.of()).compose(new NettyResponse((short) 209, buffer));

        // Only the Base64 header "CQ"; the client then shows "no transactions".
        assertThat(buffer.toString(StandardCharsets.ISO_8859_1)).isEqualTo("CQ");
    }
}
