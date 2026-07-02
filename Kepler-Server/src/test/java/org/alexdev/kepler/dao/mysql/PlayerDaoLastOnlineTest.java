package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.game.player.PlayerDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDaoLastOnlineTest {
    private static final String JDBC_URL =
            "jdbc:h2:mem:player-last-online;MODE=MariaDB;DB_CLOSE_DELAY=-1";

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(JDBC_URL, "sa", "");

        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("""
                    CREATE TABLE users (
                        id INT NOT NULL PRIMARY KEY,
                        username VARCHAR(50) NOT NULL,
                        last_online BIGINT NOT NULL DEFAULT 0,
                        online_minutes BIGINT NOT NULL DEFAULT 0
                    )""");
            st.execute("INSERT INTO users (id, username, last_online) VALUES (1, 'online_one', 0)");
            st.execute("INSERT INTO users (id, username, last_online) VALUES (2, 'online_two', 0)");
            st.execute("INSERT INTO users (id, username, last_online) VALUES (3, 'offline', 0)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    @Test
    void saveLastOnlineBatchUpdatesOnlyAuthenticatedPlayers() throws Exception {
        PlayerDetails first = details(1);
        PlayerDetails second = details(2);

        PlayerDao.saveLastOnline(conn, List.of(first, second), 1_700_000_123L);

        assertThat(lastOnlineFor(1)).isEqualTo(1_700_000_123L);
        assertThat(lastOnlineFor(2)).isEqualTo(1_700_000_123L);
        assertThat(lastOnlineFor(3)).isZero();
        assertThat(first.getLastOnline()).isEqualTo(1_700_000_123L);
        assertThat(second.getLastOnline()).isEqualTo(1_700_000_123L);
    }

    @Test
    void saveLastOnlineBatchAccruesOneOnlineMinutePerRun() throws Exception {
        PlayerDetails first = details(1);
        PlayerDetails second = details(2);

        // The GameScheduler runs the batch once per minute, so two runs must
        // accrue exactly two minutes for the players present in the batch.
        PlayerDao.saveLastOnline(conn, List.of(first, second), 1_700_000_060L);
        PlayerDao.saveLastOnline(conn, List.of(first), 1_700_000_120L);

        assertThat(onlineMinutesFor(1)).isEqualTo(2L);
        assertThat(onlineMinutesFor(2)).isEqualTo(1L);
        assertThat(onlineMinutesFor(3)).isZero();
    }

    private PlayerDetails details(int id) throws Exception {
        PlayerDetails details = new PlayerDetails();
        Field idField = PlayerDetails.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.setInt(details, id);

        return details;
    }

    private long lastOnlineFor(int id) throws SQLException {
        return usersColumnFor(id, "last_online");
    }

    private long onlineMinutesFor(int id) throws SQLException {
        return usersColumnFor(id, "online_minutes");
    }

    private long usersColumnFor(int id, String column) throws SQLException {
        try (var statement = conn.prepareStatement("SELECT " + column + " FROM users WHERE id = ?")) {
            statement.setInt(1, id);

            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();

                return result.getLong(column);
            }
        }
    }
}
