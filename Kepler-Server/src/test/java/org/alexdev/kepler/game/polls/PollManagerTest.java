package org.alexdev.kepler.game.polls;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.dao.mysql.PollDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.messages.outgoing.polls.POLL_CONTENTS;
import org.alexdev.kepler.messages.outgoing.polls.POLL_ERROR;
import org.alexdev.kepler.messages.outgoing.polls.POLL_OFFER;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.encoding.Base64Encoding;
import org.alexdev.kepler.util.encoding.VL64Encoding;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * POLL_START (234), POLL_REJECT (235) and POLL_ANSWER (236) as sent by
 * hh_poll's Poll_Component_Class.
 */
class PollManagerTest {
    private static final PollQuestion SINGLE = new PollQuestion(11, 1, "¿Color?", 1, 1, List.of(100, 101), List.of("Rojo", "Azul"));
    private static final PollQuestion MULTIPLE = new PollQuestion(12, 2, "¿Juegos?", 1, 2, List.of(200, 201, 202), List.of("A", "B", "C"));
    private static final PollQuestion TEXT = new PollQuestion(13, 3, "Opina", 0, 0, List.of(), List.of());
    private static final Poll POLL = new Poll(5, 42, "desc", "head", "gracias", List.of(SINGLE, MULTIPLE, TEXT));

    /** Builds a client packet: B64 header, VL64 ints, B64-length strings. */
    private static NettyRequest request(Object... parts) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(Base64Encoding.encode(236, 2));

        for (Object part : parts) {
            if (part instanceof Integer value) {
                buf.writeBytes(VL64Encoding.encode(value));
            } else {
                byte[] bytes = part.toString().getBytes(StandardCharsets.ISO_8859_1);
                buf.writeBytes(Base64Encoding.encode(bytes.length, 2));
                buf.writeBytes(bytes);
            }
        }

        return new NettyRequest(buf);
    }

    private static Player playerInRoom(int userId, int roomId) {
        Player player = mock(Player.class);
        PlayerDetails details = mock(PlayerDetails.class);
        RoomPlayer roomUser = mock(RoomPlayer.class);
        Room room = mock(Room.class);
        when(player.isLoggedIn()).thenReturn(true);
        when(player.getDetails()).thenReturn(details);
        when(details.getId()).thenReturn(userId);
        when(player.getRoomUser()).thenReturn(roomUser);
        when(roomUser.getRoom()).thenReturn(room);
        when(room.getId()).thenReturn(roomId);
        return player;
    }

    private static PollManager manager() {
        return new PollManager(mock(ScheduledExecutorService.class));
    }

    @Test
    void singleChoiceMapsOneBasedPositionToOptionId() {
        PollManager.ParsedAnswer answer = PollManager.parseAnswer(SINGLE, request(1, 2));

        assertThat(answer).isNotNull();
        assertThat(answer.optionIds()).containsExactly(101);
        assertThat(answer.text()).isNull();
    }

    @Test
    void multipleChoiceRespectsMinMaxRangeAndDuplicates() {
        assertThat(PollManager.parseAnswer(MULTIPLE, request(2, 1, 3)).optionIds()).containsExactly(200, 202);
        assertThat(PollManager.parseAnswer(MULTIPLE, request(0))).isNull();          // below min
        assertThat(PollManager.parseAnswer(MULTIPLE, request(3, 1, 2, 3))).isNull();  // above max
        assertThat(PollManager.parseAnswer(MULTIPLE, request(2, 1, 1))).isNull();     // duplicate
        assertThat(PollManager.parseAnswer(MULTIPLE, request(1, 4))).isNull();        // out of range
        assertThat(PollManager.parseAnswer(MULTIPLE, request(1, 0))).isNull();        // zero position
        assertThat(PollManager.parseAnswer(MULTIPLE, request(2, 1))).isNull();        // truncated
    }

    @Test
    void textAnswerIsCleanedAndRequired() {
        PollManager.ParsedAnswer answer = PollManager.parseAnswer(TEXT, request("  Me gusta\rmucho" + (char) 2 + " "));

        assertThat(answer.text()).isEqualTo("Me gusta\nmucho");
        assertThat(answer.optionIds()).isEmpty();
        assertThat(PollManager.parseAnswer(TEXT, request("   "))).isNull();
        assertThat(PollManager.parseAnswer(TEXT, request())).isNull();
        assertThat(PollManager.cleanAnswerText("x".repeat(1500))).hasSize(PollManager.MAX_ANSWER_LENGTH);
    }

    @Test
    void pollsWithoutRenderableQuestionsAreNotOffered() {
        assertThat(PollManager.isOfferable(POLL)).isTrue();
        assertThat(PollManager.isOfferable(new Poll(1, 1, "", "", "", List.of()))).isFalse();
        PollQuestion noOptions = new PollQuestion(1, 1, "q", 1, 1, List.of(), List.of());
        assertThat(PollManager.isOfferable(new Poll(1, 1, "", "", "", List.of(noOptions)))).isFalse();
        PollQuestion unknownType = new PollQuestion(1, 9, "q", 0, 0, List.of(), List.of());
        assertThat(PollManager.isOfferable(new Poll(1, 1, "", "", "", List.of(unknownType)))).isFalse();
    }

    @Test
    void offerOnlyWhenStillInTheRoomAndAPollIsPending() {
        Player player = playerInRoom(3, 42);

        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            dao.when(() -> PollDao.findOfferablePoll(eq(42), eq(3), any())).thenReturn(POLL);

            manager().offer(player, 42);
            verify(player).send(any(POLL_OFFER.class));

            manager().offer(player, 43); // player already moved on
            dao.verify(() -> PollDao.findOfferablePoll(eq(43), anyInt(), any()), never());
        }
    }

    @Test
    void roomEntryWithoutAnActivePollNeverTouchesTheDatabaseOrTheExecutor() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        PollManager manager = new PollManager(executor);
        Player player = playerInRoom(3, 42);
        Room room = player.getRoomUser().getRoom();

        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            dao.when(() -> PollDao.getActivePollRoomIds(any())).thenReturn(Set.of(77));
            manager.refreshActivePollRooms();

            manager.scheduleOffer(player, room);
            verifyNoInteractions(executor);
            dao.verify(() -> PollDao.findOfferablePoll(anyInt(), anyInt(), any()), never());

            dao.when(() -> PollDao.getActivePollRoomIds(any())).thenReturn(Set.of(42));
            manager.refreshActivePollRooms();
            manager.scheduleOffer(player, room);
            verify(executor).schedule(any(Runnable.class), eq((long) PollManager.OFFER_DELAY_SECONDS), eq(TimeUnit.SECONDS));

            // A failed refresh (null) keeps the previous set.
            dao.when(() -> PollDao.getActivePollRoomIds(any())).thenReturn(null);
            manager.refreshActivePollRooms();
            assertThat(manager.hasActivePoll(42)).isTrue();
        }
    }

    @Test
    void startRecordsParticipationAndSendsContents() {
        Player player = playerInRoom(3, 42);

        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            dao.when(() -> PollDao.startParticipation(eq(5), eq(3), eq(42), any())).thenReturn(true);
            dao.when(() -> PollDao.getPoll(eq(5), eq(false), any())).thenReturn(POLL);

            manager().start(player, 5);

            verify(player).send(any(POLL_CONTENTS.class));
            verify(player, never()).send(any(POLL_ERROR.class));
        }
    }

    @Test
    void startSendsErrorWhenThePollIsNoLongerActiveInThatRoom() {
        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            // startParticipation refuses: inactive, other room, outside window or already taken.
            Player player = playerInRoom(3, 42);
            manager().start(player, 5);
            verify(player).send(any(POLL_ERROR.class));
            verify(player, never()).send(any(POLL_CONTENTS.class));
            dao.verify(() -> PollDao.getPoll(anyInt(), anyBoolean(), any()), never());

            Player hotelView = playerInRoom(3, 42);
            when(hotelView.getRoomUser().getRoom()).thenReturn(null);
            manager().start(hotelView, 5);
            verify(hotelView).send(any(POLL_ERROR.class));
            // Only the first player's attempt reached the database.
            dao.verify(() -> PollDao.startParticipation(anyInt(), anyInt(), anyInt(), any()), times(1));
        }
    }

    @Test
    void rejectStoresARejectedRowForExistingPollsOnly() {
        Player player = playerInRoom(3, 42);

        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            dao.when(() -> PollDao.getPoll(eq(5), eq(false), any())).thenReturn(POLL);

            manager().reject(player, 5);
            manager().reject(player, 777);

            dao.verify(() -> PollDao.addParticipant(eq(5), eq(3), eq(PollDao.STATUS_REJECTED), any()));
            dao.verify(() -> PollDao.addParticipant(eq(777), anyInt(), any(), any()), never());
        }
    }

    @Test
    void answerRequiresAStartedParticipationAndAQuestionOfThatPoll() {
        Player player = playerInRoom(3, 42);

        try (MockedStatic<PollDao> dao = mockStatic(PollDao.class)) {
            dao.when(() -> PollDao.getPoll(eq(5), eq(false), any())).thenReturn(POLL);
            dao.when(() -> PollDao.saveAnswer(anyInt(), anyInt(), anyInt(), any(), any(), any())).thenReturn(true);

            // Never started (or rejected): ignored.
            assertThat(manager().answer(player, request(5, 12, 1, 2))).isFalse();

            dao.when(() -> PollDao.getParticipantStatus(5, 3)).thenReturn(PollDao.STATUS_STARTED);

            assertThat(manager().answer(player, request(5, 12, 2, 1, 3))).isTrue();
            dao.verify(() -> PollDao.saveAnswer(eq(5), eq(12), eq(3), isNull(), eq(List.of(200, 202)), any()));

            assertThat(manager().answer(player, request(5, 13, "Genial"))).isTrue();
            dao.verify(() -> PollDao.saveAnswer(eq(5), eq(13), eq(3), eq("Genial"), eq(List.of()), any()));

            // Question from another poll / invalid selection.
            assertThat(manager().answer(player, request(5, 999, 1, 1))).isFalse();
            assertThat(manager().answer(player, request(5, 11, 2, 1, 2))).isFalse();
        }
    }
}
