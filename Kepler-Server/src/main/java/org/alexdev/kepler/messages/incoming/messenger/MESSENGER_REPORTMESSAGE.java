package org.alexdev.kepler.messages.incoming.messenger;

import org.alexdev.kepler.dao.mysql.MessengerDao;
import org.alexdev.kepler.dao.mysql.ModerationDao;
import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.alexdev.kepler.game.messenger.MessengerMessage;
import org.alexdev.kepler.game.moderation.cfh.CallForHelpManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.types.MessageEvent;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

/**
 * Cliente v14: consola -> "Reportar" sobre el mensaje recibido. Formato: [int messageId], sin respuesta.
 */
public class MESSENGER_REPORTMESSAGE implements MessageEvent {
    private static final long REPORT_COOLDOWN_MS = 10_000;
    private static final java.util.Map<Integer, Long> LAST_REPORT_AT = new java.util.concurrent.ConcurrentHashMap<>();

    static final String AUDIT_ACTION = "messenger_report";
    static final int MAX_TEXT = 300;

    @Override
    public void handle(Player player, NettyRequest reader) throws Exception {
        int messageId = reader.readInt();
        int reporterId = player.getDetails().getId();

        long now = System.currentTimeMillis();
        Long last = LAST_REPORT_AT.get(reporterId);
        if (last != null && now - last < REPORT_COOLDOWN_MS) {
            return;
        }
        LAST_REPORT_AT.put(reporterId, now);

        // Solo se puede reportar un mensaje recibido por uno mismo
        MessengerMessage message = MessengerDao.getMessageForReceiver(messageId, reporterId);

        if (message == null) {
            return;
        }

        String senderName = PlayerDao.getName(message.getFromId());
        String report = buildReport(messageId, senderName, message.getFromId(), message.getMessage());

        // Siempre queda en el log de auditoria (housekeeping)
        ModerationDao.addReport(AUDIT_ACTION, reporterId, message.getFromId(), report, reportPrefix(messageId));

        // Ademas, llamada de ayuda para los moderadores conectados (requiere sala y una sola llamada pendiente)
        if (player.getRoomUser().getRoom() != null && !CallForHelpManager.getInstance().hasPendingCall(player)) {
            CallForHelpManager.getInstance().submitCall(player, report);
        }
    }

    static String reportPrefix(int messageId) {
        return "[Consola] Reporte de mensaje #" + messageId + " ";
    }

    static String buildReport(int messageId, String senderName, int senderId, String body) {
        String text = body == null ? "" : body.replaceAll("[\\p{Cntrl}]", " ").trim();

        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }

        return reportPrefix(messageId) + "de " + (senderName == null ? "?" : senderName)
                + " (id " + senderId + "): " + text;
    }
}
