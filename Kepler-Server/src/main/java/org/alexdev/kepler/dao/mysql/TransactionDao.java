package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.dao.Storage;
import org.alexdev.kepler.game.purse.CreditLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionDao {
    /**
     * How many credit movements the purse "Transactions" view shows.
     */
    public static final int CREDIT_LOG_LIMIT = 50;

    public static void logTransaction(int userId, String type, String description, int creditsChange, String itemsChange) {
        Connection sqlConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            sqlConnection = Storage.getStorage().getConnection();
            preparedStatement = Storage.getStorage().prepare(
                // created_at in UTC, same convention as the CMS (Laravel app timezone = UTC)
                "INSERT INTO users_transactions (user_id, type, description, credits_change, items_change, created_at) VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP())",
                sqlConnection
            );
            preparedStatement.setInt(1, userId);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, description);
            preparedStatement.setInt(4, creditsChange);
            preparedStatement.setString(5, itemsChange);
            preparedStatement.executeUpdate();
        } catch (Exception e) {
            Storage.logError(e);
        } finally {
            Storage.closeSilently(preparedStatement);
            Storage.closeSilently(sqlConnection);
        }
    }

    /**
     * Latest credit movements of a user, newest first. Never throws: on a
     * database error the purse simply shows "no transactions".
     */
    public static List<CreditLogEntry> getCreditLog(int userId, int limit) {
        Connection sqlConnection = null;

        try {
            sqlConnection = Storage.getStorage().getConnection();
            return getCreditLog(sqlConnection, userId, limit);
        } catch (Exception e) {
            Storage.logError(e);
            return new ArrayList<>();
        } finally {
            Storage.closeSilently(sqlConnection);
        }
    }

    /**
     * Latest credit movements (credits_change &lt;&gt; 0) of a user, newest first
     * (by id, so legacy rows with a NULL created_at keep their place).
     */
    public static List<CreditLogEntry> getCreditLog(Connection sqlConnection, int userId, int limit) throws SQLException {
        List<CreditLogEntry> entries = new ArrayList<>();

        try (PreparedStatement preparedStatement = sqlConnection.prepareStatement(
                "SELECT type, credits_change, created_at FROM users_transactions " +
                "WHERE user_id = ? AND credits_change <> 0 ORDER BY id DESC LIMIT ?")) {
            preparedStatement.setInt(1, userId);
            preparedStatement.setInt(2, Math.max(0, limit));

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new CreditLogEntry(
                            resultSet.getString("type"),
                            resultSet.getInt("credits_change"),
                            resultSet.getObject("created_at", LocalDateTime.class)
                    ));
                }
            }
        }

        return entries;
    }
}
