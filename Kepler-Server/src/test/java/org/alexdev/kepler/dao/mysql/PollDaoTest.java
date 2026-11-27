package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.game.polls.Poll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PollDao against H2 in MariaDB mode with the columns created by the Laravel
 * migration 2026_11_27_120000_create_polls_tables.
 */
class PollDaoTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 18, 0);

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:polls;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");

        try (Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("""
                    CREATE TABLE polls (
                        id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(100) NOT NULL, description TEXT NOT NULL,
                        headline VARCHAR(255) NOT NULL DEFAULT '', thanks TEXT NOT NULL, room_id INT NOT NULL DEFAULT 0,
                        active TINYINT NOT NULL DEFAULT 0, starts_at DATETIME NULL, ends_at DATETIME NULL,
                        created_at DATETIME NULL, updated_at DATETIME NULL)""");
            st.execute("""
                    CREATE TABLE poll_questions (
                        id INT AUTO_INCREMENT PRIMARY KEY, poll_id INT NOT NULL, sort_order SMALLINT NOT NULL DEFAULT 0,
                        type TINYINT NOT NULL DEFAULT 1, question TEXT NOT NULL,
                        min_selections TINYINT NOT NULL DEFAULT 1, max_selections TINYINT NOT NULL DEFAULT 1)""");
            st.execute("""
                    CREATE TABLE poll_question_options (
                        id INT AUTO_INCREMENT PRIMARY KEY, question_id INT NOT NULL,
                        sort_order SMALLINT NOT NULL DEFAULT 0, text VARCHAR(255) NOT NULL)""");
            st.execute("""
                    CREATE TABLE poll_participants (
                        poll_id INT NOT NULL, user_id INT NOT NULL, status VARCHAR(10) NOT NULL,
                        created_at DATETIME NULL, updated_at DATETIME NULL, PRIMARY KEY (poll_id, user_id))""");
            st.execute("""
                    CREATE TABLE poll_answers (
                        id INT AUTO_INCREMENT PRIMARY KEY, poll_id INT NOT NULL, question_id INT NOT NULL,
                        user_id INT NOT NULL, answer_text TEXT NULL, created_at DATETIME NULL,
                        CONSTRAINT poll_answers_question_id_user_id_unique UNIQUE (question_id, user_id))""");
            st.execute("""
                    CREATE TABLE poll_answer_options (
                        answer_id INT NOT NULL, option_id INT NOT NULL, PRIMARY KEY (answer_id, option_id))""");

            st.execute("INSERT INTO polls (id, title, description, headline, thanks, room_id, active) VALUES (1, 'Sala', '¿Ayudas?', 'Encuesta', 'Gracias', 42, 1)");
            // Second question listed first by id but ordered second by sort_order.
            st.execute("INSERT INTO poll_questions (id, poll_id, sort_order, type, question, min_selections, max_selections) VALUES (10, 1, 2, 3, 'Opina', 0, 0)");
            st.execute("INSERT INTO poll_questions (id, poll_id, sort_order, type, question, min_selections, max_selections) VALUES (11, 1, 1, 2, '¿Juegos?', 1, 2)");
            st.execute("INSERT INTO poll_question_options (id, question_id, sort_order, text) VALUES (101, 11, 2, 'B'), (100, 11, 1, 'A')");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void loadsQuestionsAndOptionsInSortOrder() throws SQLException {
        Poll poll = PollDao.getPoll(conn, 1, true, NOW);

        assertThat(poll.getRoomId()).isEqualTo(42);
        assertThat(poll.getDescription()).isEqualTo("¿Ayudas?");
        assertThat(poll.getQuestions()).extracting(q -> q.getId()).containsExactly(11, 10);
        assertThat(poll.getQuestions().get(0).getOptions()).containsExactly("A", "B");
        assertThat(poll.getQuestions().get(0).getOptionIds()).containsExactly(100, 101);
    }

    @Test
    void offerableRespectsRoomActiveDatesAndPreviousParticipation() throws SQLException {
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNotNull();
        assertThat(PollDao.findOfferablePoll(conn, 43, 7, NOW)).isNull();

        exec("UPDATE polls SET starts_at = '2026-11-28 00:00:00'");
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNull();
        exec("UPDATE polls SET starts_at = NULL, ends_at = '2026-11-27 18:00:00'");
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNull();
        assertThat(PollDao.getPoll(conn, 1, true, NOW)).isNull();
        assertThat(PollDao.getPoll(conn, 1, false, NOW)).isNotNull();
        exec("UPDATE polls SET ends_at = NULL, active = 0");
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNull();
        exec("UPDATE polls SET active = 1");

        assertThat(PollDao.addParticipant(conn, 1, 7, PollDao.STATUS_REJECTED, NOW)).isTrue();
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNull();
        assertThat(PollDao.findOfferablePoll(conn, 42, 8, NOW)).isNotNull();
    }

    @Test
    void startParticipationOnlyWhileActiveInItsRoomAndOncePerUser() throws SQLException {
        assertThat(PollDao.startParticipation(conn, 1, 7, 43, NOW)).isFalse(); // other room

        exec("UPDATE polls SET active = 0");
        assertThat(PollDao.startParticipation(conn, 1, 7, 42, NOW)).isFalse(); // deactivated meanwhile
        exec("UPDATE polls SET active = 1, ends_at = '2026-11-27 17:59:59'");
        assertThat(PollDao.startParticipation(conn, 1, 7, 42, NOW)).isFalse(); // expired
        exec("UPDATE polls SET ends_at = NULL");
        assertThat(count("SELECT COUNT(*) FROM poll_participants")).isZero();

        assertThat(PollDao.startParticipation(conn, 1, 7, 42, NOW)).isTrue();
        assertThat(PollDao.getParticipantStatus(conn, 1, 7)).isEqualTo(PollDao.STATUS_STARTED);
        assertThat(PollDao.startParticipation(conn, 1, 7, 42, NOW)).isFalse(); // already taken
    }

    @Test
    void activePollRoomsOnlyListsActiveCurrentPollsWithQuestions() throws SQLException {
        exec("INSERT INTO polls (id, title, description, thanks, room_id, active) VALUES (2, 'Off', 'x', 'g', 50, 0)");
        exec("INSERT INTO polls (id, title, description, thanks, room_id, active) VALUES (3, 'Empty', 'x', 'g', 51, 1)");
        exec("INSERT INTO polls (id, title, description, thanks, room_id, active, starts_at) VALUES (4, 'Later', 'x', 'g', 52, 1, '2026-12-01 00:00:00')");
        exec("INSERT INTO poll_questions (poll_id, type, question) VALUES (2, 3, 'q'), (4, 3, 'q')");

        assertThat(PollDao.getActivePollRoomIds(conn, NOW)).containsExactly(42);
        assertThat(PollDao.getActivePollRoomIds(conn, LocalDateTime.of(2026, 12, 2, 0, 0))).containsExactlyInAnyOrder(42, 52);
    }

    @Test
    void textQuestionsWithoutOptionsSurviveTheJoinedLoad() throws SQLException {
        Poll poll = PollDao.getPoll(conn, 1, false, NOW);

        assertThat(poll.getQuestions().get(1).getOptions()).isEmpty();
        assertThat(poll.getQuestions().get(1).getText()).isEqualTo("Opina");
    }

    @Test
    void pollWithoutQuestionsIsNeverOffered() throws SQLException {
        exec("DELETE FROM poll_questions");
        assertThat(PollDao.findOfferablePoll(conn, 42, 7, NOW)).isNull();
    }

    @Test
    void participationIsUniquePerUser() throws SQLException {
        assertThat(PollDao.addParticipant(conn, 1, 7, PollDao.STATUS_STARTED, NOW)).isTrue();
        assertThat(PollDao.addParticipant(conn, 1, 7, PollDao.STATUS_STARTED, NOW)).isFalse();
        assertThat(PollDao.getParticipantStatus(conn, 1, 7)).isEqualTo(PollDao.STATUS_STARTED);
        assertThat(PollDao.getParticipantStatus(conn, 1, 8)).isNull();
    }

    @Test
    void answersAreStoredOncePerQuestionAndCompleteTheParticipation() throws SQLException {
        PollDao.addParticipant(conn, 1, 7, PollDao.STATUS_STARTED, NOW);

        assertThat(PollDao.saveAnswer(conn, 1, 11, 7, null, List.of(100, 101), NOW)).isTrue();
        assertThat(PollDao.getParticipantStatus(conn, 1, 7)).isEqualTo(PollDao.STATUS_STARTED);

        // Second attempt at the same question is refused and leaves no extra rows.
        assertThat(PollDao.saveAnswer(conn, 1, 11, 7, null, List.of(100), NOW)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM poll_answers")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM poll_answer_options")).isEqualTo(2);

        assertThat(PollDao.saveAnswer(conn, 1, 10, 7, "Me gusta", List.of(), NOW)).isTrue();
        assertThat(PollDao.getParticipantStatus(conn, 1, 7)).isEqualTo(PollDao.STATUS_COMPLETED);
        assertThat(count("SELECT COUNT(*) FROM poll_answers WHERE answer_text = 'Me gusta'")).isEqualTo(1);
        assertThat(conn.getAutoCommit()).isTrue();
    }
}
