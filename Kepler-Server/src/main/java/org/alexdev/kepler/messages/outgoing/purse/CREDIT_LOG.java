package org.alexdev.kepler.messages.outgoing.purse;

import org.alexdev.kepler.game.purse.CreditLogEntry;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Purse credit log (hh_purse, Purse Handler Class):
 *
 * <pre>
 *   209:
 *     the itemDelimiter = TAB
 *     repeat with i = tMsg.content.line.count - 1 down to 1
 *       tLine = tMsg.content.line[i]
 *       if tLine = EMPTY then exit repeat
 *       tList["date"] = tLine.item[1]                     -- "-" replaced by "." on screen
 *       tList["time"] = tLine.item[2]
 *       tList["credit_value"] = tLine.item[3]
 *       tList["real_value"] = tLine.item[4]
 *       tList["currency"] = tLine.item[5]
 *       tList["transaction_system_name"] = tLine.item[6]  -- shown as getText("transaction_system_" &amp; name)
 * </pre>
 *
 * Every row ends with a carriage return, so the client's "line.count - 1"
 * skips only the empty line after the last terminator. The client reads
 * bottom-up and fills page 1 first, so rows go out oldest first and the
 * newest movement ends up at the top of page 1. An empty body means
 * "no transactions".
 */
public class CREDIT_LOG extends MessageComposer {
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Mexico_City");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Internal transaction types that have a native client text
     * (transaction_system_* in external_texts). Anything else is sent as the
     * type itself, so external_texts needs a transaction_system_&lt;type&gt; key.
     */
    private static final Map<String, String> SYSTEM_NAMES = Map.of(
            "catalogue_purchase", "stuff_store",
            "club_shop_buy", "stuff_store",
            "club_subscribe", "club_habbo"
    );

    private final List<CreditLogEntry> newestFirst;

    /**
     * @param newestFirst entries as returned by TransactionDao.getCreditLog (newest first)
     */
    public CREDIT_LOG(List<CreditLogEntry> newestFirst) {
        this.newestFirst = newestFirst != null ? newestFirst : List.of();
    }

    @Override
    public void compose(NettyResponse response) {
        response.write(buildBody(this.newestFirst));
    }

    public static String buildBody(List<CreditLogEntry> newestFirst) {
        List<CreditLogEntry> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);

        StringBuilder body = new StringBuilder();

        for (CreditLogEntry entry : oldestFirst) {
            body.append(formatLine(entry)).append('\r');
        }

        return body.toString();
    }

    public static String formatLine(CreditLogEntry entry) {
        String date = "";
        String time = "";

        if (entry.createdAtUtc() != null) {
            ZonedDateTime local = entry.createdAtUtc().atZone(ZoneOffset.UTC).withZoneSameInstant(DISPLAY_ZONE);
            date = local.format(DATE_FORMAT);
            time = local.format(TIME_FORMAT);
        }

        return date + '\t'
                + time + '\t'
                + entry.creditsChange() + '\t'
                + "0" + '\t'   // real_value: only shown when purse.valuefield.active is set
                + "" + '\t'    // currency: not displayed by the v14 purse
                + systemName(entry.type());
    }

    /**
     * Client text suffix for transaction_system_*; restricted to [a-z0-9_] so
     * it can never inject TAB/CR or protocol bytes.
     */
    public static String systemName(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String mapped = SYSTEM_NAMES.getOrDefault(normalized, normalized);
        String safe = mapped.replaceAll("[^a-z0-9_]", "");

        return safe.isEmpty() ? "web_internal" : safe;
    }

    @Override
    public short getHeader() {
        return 209; // "CQ"
    }
}
