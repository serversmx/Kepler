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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerDaoLastOnlineTest {
    private static final String JDBC_URL =
            "jdbc:h2:mem:player-last-online;MODE=MariaDB;DB_CLOSE_DELAY=-1";

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(JDBC_URL, "sa", "");

        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS users_activity_daily");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("""
                    CREATE TABLE users (
                        id INT NOT NULL PRIMARY KEY,
                        username VARCHAR(50) NOT NULL,
                        last_online BIGINT NOT NULL DEFAULT 0,
                        online_minutes BIGINT NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE users_activity_daily (
                        user_id INT NOT NULL,
                        activity_date DATE NOT NULL,
                        online_minutes BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (user_id, activity_date)
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
        assertThat(dailyMinutesFor(1, "2023-11-14")).isEqualTo(1L);
        assertThat(dailyMinutesFor(2, "2023-11-14")).isEqualTo(1L);
        assertThat(dailyMinutesFor(3, "2023-11-14")).isZero();
        assertThat(conn.getAutoCommit()).isTrue();
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
        assertThat(dailyMinutesFor(1, "2023-11-14")).isEqualTo(2L);
        assertThat(dailyMinutesFor(2, "2023-11-14")).isEqualTo(1L);
    }

    @Test
    void dailyActivityRollsOverAtMidnightInMexicoCity() throws Exception {
        PlayerDetails first = details(1);

        PlayerDao.saveLastOnline(conn, List.of(first), Instant.parse("2026-09-17T05:59:00Z").getEpochSecond());
        PlayerDao.saveLastOnline(conn, List.of(first), Instant.parse("2026-09-17T06:00:00Z").getEpochSecond());

        assertThat(dailyMinutesFor(1, "2026-09-16")).isEqualTo(1L);
        assertThat(dailyMinutesFor(1, "2026-09-17")).isEqualTo(1L);
        assertThat(onlineMinutesFor(1)).isEqualTo(2L);
    }

    @Test
    void repeatedPlayersAccrueOnlyOneMinutePerBatch() throws Exception {
        PlayerDetails first = details(1);
        PlayerDetails duplicate = details(1);

        PlayerDao.saveLastOnline(conn, List.of(first, duplicate, first), 1_700_000_123L);

        assertThat(onlineMinutesFor(1)).isEqualTo(1L);
        assertThat(dailyMinutesFor(1, "2023-11-14")).isEqualTo(1L);
        assertThat(first.getLastOnline()).isEqualTo(1_700_000_123L);
        assertThat(duplicate.getLastOnline()).isEqualTo(1_700_000_123L);
    }

    @Test
    void dailyWriteFailureRollsBackBothCountersAndLeavesMemoryUnchanged() throws Exception {
        PlayerDetails first = details(1);
        PlayerDetails second = details(2);
        first.setLastOnline(50L);
        second.setLastOnline(75L);
        try (Statement statement = conn.createStatement()) {
            statement.execute("ALTER TABLE users_activity_daily ADD CONSTRAINT reject_second_user CHECK (user_id <> 2)");
        }

        assertThatThrownBy(() -> PlayerDao.saveLastOnline(conn, List.of(first, second), 1_700_000_123L))
                .isInstanceOf(SQLException.class);

        assertThat(onlineMinutesFor(1)).isZero();
        assertThat(onlineMinutesFor(2)).isZero();
        assertThat(lastOnlineFor(1)).isZero();
        assertThat(lastOnlineFor(2)).isZero();
        assertThat(dailyMinutesFor(1, "2023-11-14")).isZero();
        assertThat(dailyMinutesFor(2, "2023-11-14")).isZero();
        assertThat(first.getLastOnline()).isEqualTo(50L);
        assertThat(second.getLastOnline()).isEqualTo(75L);
        assertThat(conn.getAutoCommit()).isTrue();
    }

    @Test
    void missingDailyTableDoesNotPartiallyIncrementLifetimeCounter() throws Exception {
        PlayerDetails first = details(1);
        try (Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE users_activity_daily");
        }

        assertThatThrownBy(() -> PlayerDao.saveLastOnline(conn, List.of(first), 1_700_000_123L))
                .isInstanceOf(SQLException.class);

        assertThat(onlineMinutesFor(1)).isZero();
        assertThat(lastOnlineFor(1)).isZero();
        assertThat(conn.getAutoCommit()).isTrue();
    }

    @Test
    void lifetimeMinutesAreNotBackfilledIntoDailyActivity() throws Exception {
        try (Statement statement = conn.createStatement()) {
            statement.execute("UPDATE users SET online_minutes = 4000 WHERE id = 1");
        }

        PlayerDao.saveLastOnline(conn, List.of(details(1)), 1_700_000_123L);

        assertThat(onlineMinutesFor(1)).isEqualTo(4001L);
        assertThat(dailyMinutesFor(1, "2023-11-14")).isEqualTo(1L);
    }

    @Test
    void deletedPlayersDoNotCreateOrphanActivityRows() throws Exception {
        PlayerDao.saveLastOnline(conn, List.of(details(999)), 1_700_000_123L);

        assertThat(dailyMinutesFor(999, "2023-11-14")).isZero();
    }

    private long dailyMinutesFor(int id, String date) throws SQLException {
        try (var statement = conn.prepareStatement("SELECT online_minutes FROM users_activity_daily WHERE user_id = ? AND activity_date = ?")) {
            statement.setInt(1, id);
            statement.setString(2, date);

            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong("online_minutes") : 0;
            }
        }
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
