package org.alexdev.kepler.game.polls;

import org.alexdev.kepler.dao.mysql.PollDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.log.Log;
import org.alexdev.kepler.messages.outgoing.polls.POLL_CONTENTS;
import org.alexdev.kepler.messages.outgoing.polls.POLL_ERROR;
import org.alexdev.kepler.messages.outgoing.polls.POLL_OFFER;
import org.alexdev.kepler.server.netty.streams.NettyRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server side of the v14 hh_poll cast.
 *
 * The client defines no trigger of its own: it simply shows 316 whenever it
 * arrives. HabboP offers a poll a few seconds after the player enters the
 * poll's room, while the poll is active and inside its date window, and only
 * if the player has not started/answered/rejected it before. Starting counts
 * as participating (the client warns "No podrás continuarla después" when
 * cancelling, and POLL_CANCEL is never registered as a command, so the server
 * is not told about cancellations).
 *
 * Room entry only checks an in-memory set of rooms with an active poll,
 * refreshed every {@link #ROOM_CACHE_REFRESH_SECONDS} seconds on a dedicated
 * executor, so rooms without polls never touch the database and housekeeping
 * changes reach the game within a minute.
 */
public class PollManager {
    public static final int OFFER_DELAY_SECONDS = 3;
    public static final int ROOM_CACHE_REFRESH_SECONDS = 60;
    public static final int MAX_ANSWER_LENGTH = 1000;

    private static PollManager instance;

    private final ScheduledExecutorService executor;
    private volatile Set<Integer> activePollRoomIds = Set.of();

    public static synchronized PollManager getInstance() {
        if (instance == null) {
            instance = new PollManager(createExecutor());
            instance.startRoomCacheRefresh();
        }

        return instance;
    }

    /**
     * @param executor runs the poll DB work (cache refresh and delayed offers),
     *                 never the game/movement scheduler
     */
    PollManager(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    private static ScheduledExecutorService createExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kepler-polls");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void startRoomCacheRefresh() {
        this.executor.scheduleWithFixedDelay(() -> {
            try {
                this.refreshActivePollRooms();
            } catch (Exception ex) {
                Log.getErrorLogger().error("Error refreshing poll rooms: ", ex);
            }
        }, 0, ROOM_CACHE_REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reload the rooms that have an active poll. A failed query keeps the
     * previous set instead of switching polls off.
     */
    public void refreshActivePollRooms() {
        Set<Integer> roomIds = PollDao.getActivePollRoomIds(nowUtc());

        if (roomIds != null) {
            this.activePollRoomIds = Set.copyOf(roomIds);
        }
    }

    public boolean hasActivePoll(int roomId) {
        return this.activePollRoomIds.contains(roomId);
    }

    /**
     * Called from room entry (possibly on the game scheduler): only an
     * in-memory lookup here; any DB work runs later on the poll executor.
     */
    public void scheduleOffer(Player player, Room room) {
        if (player == null || room == null || room.isGameArena() || !this.hasActivePoll(room.getId())) {
            return;
        }

        int roomId = room.getId();

        this.executor.schedule(() -> {
            try {
                this.offer(player, roomId);
            } catch (Exception ex) {
                Log.getErrorLogger().error("Error offering poll: ", ex);
            }
        }, OFFER_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Send 316 if the player is still in the room and there is a poll for them.
     */
    public void offer(Player player, int roomId) {
        if (!isInRoom(player, roomId)) {
            return;
        }

        Poll poll = PollDao.findOfferablePoll(roomId, player.getDetails().getId(), nowUtc());

        if (poll == null || !isOfferable(poll)) {
            return;
        }

        player.send(new POLL_OFFER(poll.getId(), poll.getDescription()));
    }

    /**
     * 234 POLL_START: send the questions (317) or the "no longer available" error (318).
     *
     * The participation row is inserted first and only if the poll is still
     * active right now in the player's room; housekeeping cannot change the
     * questions of a poll that is active or has participants, so the poll
     * loaded afterwards is exactly what gets answered.
     */
    public void start(Player player, int pollId) {
        if (player.getRoomUser() == null || player.getRoomUser().getRoom() == null) {
            player.send(new POLL_ERROR());
            return;
        }

        LocalDateTime now = nowUtc();
        int roomId = player.getRoomUser().getRoom().getId();

        if (!PollDao.startParticipation(pollId, player.getDetails().getId(), roomId, now)) {
            player.send(new POLL_ERROR());
            return;
        }

        Poll poll = PollDao.getPoll(pollId, false, now);

        if (poll == null || !isOfferable(poll)) {
            player.send(new POLL_ERROR());
            return;
        }

        player.send(new POLL_CONTENTS(poll));
    }

    /**
     * 235 POLL_REJECT: never offer this poll to the player again.
     */
    public void reject(Player player, int pollId) {
        LocalDateTime now = nowUtc();

        if (PollDao.getPoll(pollId, false, now) == null) {
            return;
        }

        PollDao.addParticipant(pollId, player.getDetails().getId(), PollDao.STATUS_REJECTED, now);
    }

    /**
     * 236 POLL_ANSWER: int pollId, int questionId, then either
     * int count + int index * count (1-based option positions) for
     * selection questions, or a string for text questions.
     *
     * @return true when the answer was stored
     */
    public boolean answer(Player player, NettyRequest reader) {
        int pollId = reader.readInt();
        int questionId = reader.readInt();
        int userId = player.getDetails().getId();

        if (!PollDao.STATUS_STARTED.equals(PollDao.getParticipantStatus(pollId, userId))) {
            return false;
        }

        // Not only "available": a player who started before the poll was
        // closed may still finish the questions already on their screen.
        LocalDateTime now = nowUtc();
        Poll poll = PollDao.getPoll(pollId, false, now);

        if (poll == null) {
            return false;
        }

        PollQuestion question = poll.getQuestion(questionId);

        if (question == null) {
            return false;
        }

        ParsedAnswer answer = parseAnswer(question, reader);

        if (answer == null) {
            return false;
        }

        return PollDao.saveAnswer(pollId, questionId, userId, answer.text(), answer.optionIds(), now);
    }

    /**
     * Read and validate the type-specific part of POLL_ANSWER.
     *
     * @return the answer, or null when it is malformed or breaks the question limits
     */
    public static ParsedAnswer parseAnswer(PollQuestion question, NettyRequest reader) {
        try {
            if (question.isSelection()) {
                int count = reader.readInt();
                int optionCount = question.getOptionIds().size();

                if (count < question.getEffectiveMin() || count > question.getEffectiveMax() || count > optionCount) {
                    return null;
                }

                Set<Integer> positions = new LinkedHashSet<>();

                for (int i = 0; i < count; i++) {
                    int position = reader.readInt();

                    if (position < 1 || position > optionCount || !positions.add(position)) {
                        return null;
                    }
                }

                List<Integer> optionIds = new ArrayList<>();

                for (int position : positions) {
                    optionIds.add(question.getOptionIds().get(position - 1));
                }

                return new ParsedAnswer(null, optionIds);
            }

            if (question.isText()) {
                String text = cleanAnswerText(reader.readString());

                if (text.isEmpty()) {
                    return null;
                }

                return new ParsedAnswer(text, List.of());
            }
        } catch (RuntimeException malformed) {
            // Truncated packet: readInt/readString ran past the buffer.
            return null;
        }

        return null;
    }

    /**
     * A poll the client can actually render: every selection question needs
     * at least one option (validateQuestion drops it otherwise and the poll
     * would end early).
     */
    public static boolean isOfferable(Poll poll) {
        if (poll.getQuestions().isEmpty()) {
            return false;
        }

        for (PollQuestion question : poll.getQuestions()) {
            if (question.isSelection() && question.getOptions().isEmpty()) {
                return false;
            }

            if (!question.isSelection() && !question.isText()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Text sent to the client: no protocol delimiters (chr 1/2) or other
     * control bytes, and Director line breaks (CR).
     */
    public static String clientText(String value) {
        if (value == null) {
            return "";
        }

        String normalised = value.replace("\r\n", "\r").replace('\n', '\r');
        StringBuilder out = new StringBuilder(normalised.length());

        for (char c : normalised.toCharArray()) {
            if (c == '\r' || c >= 32) {
                out.append(c);
            }
        }

        return out.toString();
    }

    /**
     * Free-text answer as stored: LF line breaks, no control bytes, trimmed
     * and capped at {@link #MAX_ANSWER_LENGTH} characters.
     */
    public static String cleanAnswerText(String value) {
        if (value == null) {
            return "";
        }

        String normalised = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(normalised.length());

        for (char c : normalised.toCharArray()) {
            if (c == '\n' || c >= 32) {
                out.append(c);
            }
        }

        String text = out.toString().trim();
        return text.length() > MAX_ANSWER_LENGTH ? text.substring(0, MAX_ANSWER_LENGTH) : text;
    }

    private static boolean isInRoom(Player player, int roomId) {
        return player != null
                && player.isLoggedIn()
                && player.getRoomUser() != null
                && player.getRoomUser().getRoom() != null
                && player.getRoomUser().getRoom().getId() == roomId;
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record ParsedAnswer(String text, List<Integer> optionIds) {
    }
}
