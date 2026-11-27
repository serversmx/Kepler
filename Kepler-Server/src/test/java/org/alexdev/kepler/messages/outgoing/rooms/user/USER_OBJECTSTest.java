package org.alexdev.kepler.messages.outgoing.rooms.user;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.game.bot.Bot;
import org.alexdev.kepler.game.bot.BotFigure;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.pathfinder.Position;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Room_Handler_Class.handle_users reads one "key:value" per line. Bots are sent without "[bot]" (that
 * class, Bot_Object_Class, blanks the infostand avatar) and without "a:" (web id of the Habbo Home button).
 */
class USER_OBJECTSTest {

    private static Room lobby() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.getModel().getName()).thenReturn("newbie_lobby");
        return room;
    }

    private static Bot bot(String storedFigure, Room room) {
        Bot bot = new Bot();
        String figure = BotFigure.normalise(storedFigure, "Harry");
        bot.getDetails().fill(0, "Harry", figure, "Feliz de ayudar", BotFigure.sexOf(figure));
        bot.getRoomUser().setRoom(room);
        bot.getRoomUser().setInstanceId(7);
        bot.getRoomUser().setPosition(new Position(8, 21, 0, 2, 2));
        return bot;
    }

    private static List<String> lines(Entity entity) {
        USER_OBJECTS message = new USER_OBJECTS(entity);
        ByteBuf buffer = Unpooled.buffer();
        message.compose(new NettyResponse(message.getHeader(), buffer));
        String body = buffer.toString(StandardCharsets.ISO_8859_1).substring(2); // skip B64 header
        return List.of(body.split("\r"));
    }

    @Test
    void legacyBotIsConvertedAndSentAsAUserWithoutWebId() {
        String harry = "sd=001&sh=003/41,41,41&lg=006/51,51,51&ch=202/139,24,32&lh=001/255,210,179"
                + "&rh=001/255,210,179&hd=001/255,204,153&ey=001&fc=001/255,204,153&hr=203/103,78,59"
                + "&hrb=203/2,3,4&rs=001/255,255,255&ls=001/255,255,255&bd=001/255,204,153";
        List<String> lines = lines(bot(harry, lobby()));

        assertThat(lines).contains("i:7", "n:Harry", "s:M", "c:Feliz de ayudar", "f:8310818501285153000621022");
        assertThat(lines).doesNotContain("[bot]");
        assertThat(lines).noneMatch(line -> line.startsWith("a:"));
    }

    @Test
    void v14BotIsSentAsUser() {
        List<String> lines = lines(bot("8310818501275343000621022", lobby()));

        assertThat(lines).contains("i:7", "n:Harry", "f:8310818501275343000621022", "s:M");
        assertThat(lines).doesNotContain("[bot]");
        assertThat(lines).noneMatch(line -> line.startsWith("a:"));
    }

    @Test
    void v14FemaleBotSendsFemaleSex() {
        assertThat(lines(bot(BotFigure.DEFAULT_FEMALE, lobby()))).contains("s:F").doesNotContain("[bot]");
    }

    @Test
    void playersKeepTheirWebId() {
        PlayerDetails details = new PlayerDetails();
        details.fill(42, "rNt", "1000118001270012900121001", "hola", "M");
        RoomPlayer roomUser = mock(RoomPlayer.class);
        Room room = lobby();
        when(roomUser.getInstanceId()).thenReturn(3);
        when(roomUser.getRoom()).thenReturn(room);
        when(roomUser.getPosition()).thenReturn(new Position(1, 1, 0));
        when(roomUser.getStatuses()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
        Player player = mock(Player.class);
        when(player.getDetails()).thenReturn(details);
        when(player.getRoomUser()).thenReturn(roomUser);
        when(player.getType()).thenReturn(org.alexdev.kepler.game.entity.EntityType.PLAYER);

        assertThat(lines(player)).contains("i:3", "a:42", "n:rNt").doesNotContain("[bot]");
    }
}
