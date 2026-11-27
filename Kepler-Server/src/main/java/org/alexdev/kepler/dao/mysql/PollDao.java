package org.alexdev.kepler.dao.mysql;

import org.alexdev.kepler.dao.Storage;
import org.alexdev.kepler.game.polls.Poll;
import org.alexdev.kepler.game.polls.PollQuestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Polls are managed from Laravel housekeeping (tables polls, poll_questions,
 * poll_question_options) and answered in game (poll_participants,
 * poll_answers, poll_answer_options). Nothing is cached: housekeeping edits
 * apply on the next room entry without an RCON reload.
 *
 * Dates are UTC; callers pass the current UTC time instead of relying on the
 * DB session NOW().
 */
public class PollDao {
    public static final String STATUS_STARTED = "started";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_COMPLETED = "completed";

    private static final String OFFERABLE_SQL =
            "SELECT p.id FROM polls p " +
            "WHERE p.room_id = ? AND p.active = 1 " +
            "AND (p.starts_at IS NULL OR p.starts_at <= ?) " +
            "AND (p.ends_at IS NULL OR p.ends_at > ?) " +
            "AND EXISTS (SELECT 1 FROM poll_questions q WHERE q.poll_id = p.id) " +
            "AND NOT EXISTS (SELECT 1 FROM poll_participants pp WHERE pp.poll_id = p.id AND pp.user_id = ?) " +
            "ORDER BY p.id LIMIT 1";

    private static final String POLL_SQL =
            "SELECT id, room_id, description, headline, thanks FROM polls WHERE id = ? " +
            "AND (? = 0 OR (active = 1 AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at > ?)))";

    /** Questions and options in one round trip (no N+1). */
    private static final String QUESTIONS_SQL =
            "SELECT q.id, q.type, q.question, q.min_selections, q.max_selections, o.id AS option_id, o.text AS option_text " +
            "FROM poll_questions q LEFT JOIN poll_question_options o ON o.question_id = q.id " +
            "WHERE q.poll_id = ? ORDER BY q.sort_order, q.id, o.sort_order, o.id";

    private static final String ACTIVE_ROOMS_SQL =
            "SELECT DISTINCT p.room_id FROM polls p " +
            "WHERE p.active = 1 AND p.room_id > 0 " +
            "AND (p.starts_at IS NULL OR p.starts_at <= ?) " +
            "AND (p.ends_at IS NULL OR p.ends_at > ?) " +
            "AND EXISTS (SELECT 1 FROM poll_questions q WHERE q.poll_id = p.id)";

    /**
     * Registers the "started" row only while the poll is still active, inside
     * its window, in that room and with questions. INSERT ... SELECT reads the
     * polls row with a shared lock on InnoDB, so it serialises with the
     * housekeeping edit (SELECT ... FOR UPDATE on the same row).
     */
    private static final String START_SQL =
            "INSERT INTO poll_participants (poll_id, user_id, status, created_at, updated_at) " +
            "SELECT p.id, ?, ?, ?, ? FROM polls p " +
            "WHERE p.id = ? AND p.room_id = ? AND p.active = 1 " +
            "AND (p.starts_at IS NULL OR p.starts_at <= ?) " +
            "AND (p.ends_at IS NULL OR p.ends_at > ?) " +
            "AND EXISTS (SELECT 1 FROM poll_questions q WHERE q.poll_id = p.id)";

    public interface SqlWork<T> {
        T run(Connection conn) throws SQLException;
    }

    private static <T> T withConnection(SqlWork<T> work, T fallback) {
        Connection conn = null;

        try {
            conn = Storage.getStorage().getConnection();

            if (conn == null) {
                return fallback;
            }

            return work.run(conn);
        } catch (SQLException e) {
            Storage.logError(e);
            return fallback;
        } finally {
            Storage.closeSilently(conn);
        }
    }

    /**
     * @return the full active poll offered in this room that the user has not
     * answered nor rejected yet, or null.
     */
    public static Poll findOfferablePoll(int roomId, int userId, LocalDateTime nowUtc) {
        return withConnection(conn -> findOfferablePoll(conn, roomId, userId, nowUtc), null);
    }

    static Poll findOfferablePoll(Connection conn, int roomId, int userId, LocalDateTime nowUtc) throws SQLException {
        Timestamp now = Timestamp.valueOf(nowUtc);
        int pollId = 0;

        try (PreparedStatement ps = conn.prepareStatement(OFFERABLE_SQL)) {
            ps.setInt(1, roomId);
            ps.setTimestamp(2, now);
            ps.setTimestamp(3, now);
            ps.setInt(4, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    pollId = rs.getInt("id");
                }
            }
        }

        return pollId > 0 ? getPoll(conn, pollId, true, nowUtc) : null;
    }

    /**
     * Load a poll with its questions and options.
     *
     * @param onlyAvailable when true, the poll must be active and inside its date window
     */
    public static Poll getPoll(int pollId, boolean onlyAvailable, LocalDateTime nowUtc) {
        return withConnection(conn -> getPoll(conn, pollId, onlyAvailable, nowUtc), null);
    }

    static Poll getPoll(Connection conn, int pollId, boolean onlyAvailable, LocalDateTime nowUtc) throws SQLException {
        Timestamp now = Timestamp.valueOf(nowUtc);
        int roomId;
        String description;
        String headline;
        String thanks;

        try (PreparedStatement ps = conn.prepareStatement(POLL_SQL)) {
            ps.setInt(1, pollId);
            ps.setInt(2, onlyAvailable ? 1 : 0);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                roomId = rs.getInt("room_id");
                description = nullToEmpty(rs.getString("description"));
                headline = nullToEmpty(rs.getString("headline"));
                thanks = nullToEmpty(rs.getString("thanks"));
            }
        }

        List<PollQuestion> questions = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(QUESTIONS_SQL)) {
            ps.setInt(1, pollId);

            try (ResultSet rs = ps.executeQuery()) {
                int currentId = -1;
                int type = 0;
                String text = "";
                int min = 0;
                int max = 0;
                List<Integer> optionIds = new ArrayList<>();
                List<String> options = new ArrayList<>();

                while (rs.next()) {
                    int questionId = rs.getInt("id");

                    if (questionId != currentId) {
                        if (currentId != -1) {
                            questions.add(new PollQuestion(currentId, type, text, min, max, optionIds, options));
                        }

                        currentId = questionId;
                        type = rs.getInt("type");
                        text = nullToEmpty(rs.getString("question"));
                        min = rs.getInt("min_selections");
                        max = rs.getInt("max_selections");
                        optionIds = new ArrayList<>();
                        options = new ArrayList<>();
                    }

                    int optionId = rs.getInt("option_id");

                    if (!rs.wasNull()) {
                        optionIds.add(optionId);
                        options.add(nullToEmpty(rs.getString("option_text")));
                    }
                }

                if (currentId != -1) {
                    questions.add(new PollQuestion(currentId, type, text, min, max, optionIds, options));
                }
            }
        }

        return new Poll(pollId, roomId, description, headline, thanks, questions);
    }

    /**
     * Rooms that currently have an active poll with questions, for the
     * in-memory room-entry filter.
     */
    public static Set<Integer> getActivePollRoomIds(LocalDateTime nowUtc) {
        return withConnection(conn -> getActivePollRoomIds(conn, nowUtc), null);
    }

    static Set<Integer> getActivePollRoomIds(Connection conn, LocalDateTime nowUtc) throws SQLException {
        Set<Integer> roomIds = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement(ACTIVE_ROOMS_SQL)) {
            ps.setTimestamp(1, Timestamp.valueOf(nowUtc));
            ps.setTimestamp(2, Timestamp.valueOf(nowUtc));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roomIds.add(rs.getInt("room_id"));
                }
            }
        }

        return roomIds;
    }

    /**
     * Atomically record that the user started the poll, only if it is still
     * available in that room.
     *
     * @return false when the poll is no longer available or the user already participated
     */
    public static boolean startParticipation(int pollId, int userId, int roomId, LocalDateTime nowUtc) {
        return withConnection(conn -> startParticipation(conn, pollId, userId, roomId, nowUtc), false);
    }

    static boolean startParticipation(Connection conn, int pollId, int userId, int roomId, LocalDateTime nowUtc) throws SQLException {
        Timestamp now = Timestamp.valueOf(nowUtc);

        try (PreparedStatement ps = conn.prepareStatement(START_SQL)) {
            ps.setInt(1, userId);
            ps.setString(2, STATUS_STARTED);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.setInt(5, pollId);
            ps.setInt(6, roomId);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                return false;
            }

            throw e;
        }
    }

    public static String getParticipantStatus(int pollId, int userId) {
        return withConnection(conn -> getParticipantStatus(conn, pollId, userId), null);
    }

    static String getParticipantStatus(Connection conn, int pollId, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM poll_participants WHERE poll_id = ? AND user_id = ?")) {
            ps.setInt(1, pollId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("status") : null;
            }
        }
    }

    /**
     * Record that the user started or rejected a poll.
     *
     * @return false when the user already has a row for this poll (or on error)
     */
    public static boolean addParticipant(int pollId, int userId, String status, LocalDateTime nowUtc) {
        return withConnection(conn -> addParticipant(conn, pollId, userId, status, nowUtc), false);
    }

    static boolean addParticipant(Connection conn, int pollId, int userId, String status, LocalDateTime nowUtc) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO poll_participants (poll_id, user_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, pollId);
            ps.setInt(2, userId);
            ps.setString(3, status);
            ps.setTimestamp(4, Timestamp.valueOf(nowUtc));
            ps.setTimestamp(5, Timestamp.valueOf(nowUtc));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                return false;
            }

            throw e;
        }
    }

    /**
     * Save one answer (text or selected option ids) atomically and mark the
     * participation completed once every question has an answer.
     *
     * @return false when the question was already answered by this user (or on error)
     */
    public static boolean saveAnswer(int pollId, int questionId, int userId, String text, List<Integer> optionIds, LocalDateTime nowUtc) {
        return withConnection(conn -> saveAnswer(conn, pollId, questionId, userId, text, optionIds, nowUtc), false);
    }

    static boolean saveAnswer(Connection conn, int pollId, int questionId, int userId, String text, List<Integer> optionIds, LocalDateTime nowUtc) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            int answerId;

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO poll_answers (poll_id, question_id, user_id, answer_text, created_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, pollId);
                ps.setInt(2, questionId);
                ps.setInt(3, userId);
                ps.setString(4, text);
                ps.setTimestamp(5, Timestamp.valueOf(nowUtc));
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("poll_answers insert returned no id");
                    }

                    answerId = keys.getInt(1);
                }
            } catch (SQLException e) {
                if (isDuplicateKey(e)) {
                    conn.rollback();
                    return false;
                }

                throw e;
            }

            if (optionIds != null && !optionIds.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO poll_answer_options (answer_id, option_id) VALUES (?, ?)")) {
                    for (int optionId : optionIds) {
                        ps.setInt(1, answerId);
                        ps.setInt(2, optionId);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE poll_participants SET status = ?, updated_at = ? WHERE poll_id = ? AND user_id = ? AND status = ? " +
                    "AND (SELECT COUNT(*) FROM poll_answers a WHERE a.poll_id = ? AND a.user_id = ?) >= " +
                    "(SELECT COUNT(*) FROM poll_questions q WHERE q.poll_id = ?)")) {
                ps.setString(1, STATUS_COMPLETED);
                ps.setTimestamp(2, Timestamp.valueOf(nowUtc));
                ps.setInt(3, pollId);
                ps.setInt(4, userId);
                ps.setString(5, STATUS_STARTED);
                ps.setInt(6, pollId);
                ps.setInt(7, userId);
                ps.setInt(8, pollId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    static boolean isDuplicateKey(SQLException e) {
        String state = e.getSQLState();
        return e instanceof java.sql.SQLIntegrityConstraintViolationException
                || e.getErrorCode() == 1062
                || (state != null && state.startsWith("23"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
