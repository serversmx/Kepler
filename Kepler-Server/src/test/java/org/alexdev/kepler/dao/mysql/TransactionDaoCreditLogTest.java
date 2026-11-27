package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.game.purse.CreditLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDaoCreditLogTest {
    private static final String JDBC_URL =
            "jdbc:h2:mem:transactions-credit-log;MODE=MariaDB;DB_CLOSE_DELAY=-1";

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(JDBC_URL, "sa", "");

        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS users_transactions");
            st.execute("""
                    CREATE TABLE users_transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        credits_change INT NOT NULL DEFAULT 0,
                        items_change VARCHAR(255) NULL,
                        description VARCHAR(255) NOT NULL DEFAULT '',
                        created_at DATETIME NULL
                    )""");
            st.execute("INSERT INTO users_transactions (user_id, type, credits_change, created_at) VALUES (1, 'catalogue_purchase', -3, '2026-11-01 10:00:00')");
            st.execute("INSERT INTO users_transactions (user_id, type, credits_change, created_at) VALUES (1, 'trade_give', 0, '2026-11-02 10:00:00')");
            st.execute("INSERT INTO users_transactions (user_id, type, credits_change, created_at) VALUES (1, 'catalogue_purchase', -5, NULL)");
            st.execute("INSERT INTO users_transactions (user_id, type, credits_change, created_at) VALUES (2, 'dice_win', 10, '2026-11-03 10:00:00')");
            st.execute("INSERT INTO users_transactions (user_id, type, credits_change, created_at) VALUES (1, 'vote_reward', 50, '2026-11-04 12:30:00')");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    @Test
    void returnsOnlyCreditMovementsOfTheUserNewestFirst() throws Exception {
        List<CreditLogEntry> log = TransactionDao.getCreditLog(conn, 1, 50);

        assertThat(log).extracting(CreditLogEntry::type)
                .containsExactly("vote_reward", "catalogue_purchase", "catalogue_purchase");
        assertThat(log.get(0).creditsChange()).isEqualTo(50);
        assertThat(log.get(0).createdAtUtc()).isEqualTo(LocalDateTime.of(2026, 11, 4, 12, 30));
    }

    @Test
    void toleratesNullCreatedAt() throws Exception {
        List<CreditLogEntry> log = TransactionDao.getCreditLog(conn, 1, 50);

        assertThat(log.get(1).creditsChange()).isEqualTo(-5);
        assertThat(log.get(1).createdAtUtc()).isNull();
    }

    @Test
    void respectsLimit() throws Exception {
        assertThat(TransactionDao.getCreditLog(conn, 1, 1)).hasSize(1);
        assertThat(TransactionDao.getCreditLog(conn, 3, 50)).isEmpty();
    }
}
