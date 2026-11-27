package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.dao.Storage;
import org.alexdev.kepler.game.moderation.ModerationActionType;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class ModerationDao {
   public static void addLog(ModerationActionType type, int userId, int targetId, String message, String extraNotes) {
        Connection sqlConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            sqlConnection = Storage.getStorage().getConnection();
            preparedStatement = Storage.getStorage().prepare("INSERT INTO housekeeping_audit_log (action, user_id, target_id, message, extra_notes) VALUES (?, ?, ?, ?, ?)", sqlConnection);
            preparedStatement.setString(1, type.name().toLowerCase());
            preparedStatement.setInt(2, userId);
            preparedStatement.setInt(3, targetId);
            preparedStatement.setString(4, message);
            preparedStatement.setString(5, extraNotes);
            preparedStatement.execute();

        } catch (Exception e) {
            Storage.logError(e);
        } finally {
            Storage.closeSilently(preparedStatement);
            Storage.closeSilently(sqlConnection);
        }
    }

    /**
     * Registra un reporte de jugador en el log de auditoria visible en housekeeping.
     * Usa solo columnas base (action es string libre).
     */
    public static void addReport(String action, int userId, int targetId, String message, String dedupePrefix) {
        Connection sqlConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            sqlConnection = Storage.getStorage().getConnection();
            // One row per reporter and reported item: repeated clicks do not flood the log.
            preparedStatement = Storage.getStorage().prepare(
                    "INSERT INTO housekeeping_audit_log (action, user_id, target_id, message, created_at) " +
                    "SELECT ?, ?, ?, ?, UTC_TIMESTAMP() FROM DUAL WHERE NOT EXISTS (" +
                    "SELECT 1 FROM housekeeping_audit_log WHERE action = ? AND user_id = ? AND message LIKE ?)", sqlConnection);
            preparedStatement.setString(1, action);
            preparedStatement.setInt(2, userId);
            preparedStatement.setInt(3, targetId);
            preparedStatement.setString(4, message);
            preparedStatement.setString(5, action);
            preparedStatement.setInt(6, userId);
            preparedStatement.setString(7, dedupePrefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            preparedStatement.execute();
        } catch (Exception e) {
            Storage.logError(e);
        } finally {
            Storage.closeSilently(preparedStatement);
            Storage.closeSilently(sqlConnection);
        }
    }
}
