package org.alexdev.kepler.game.commands;

import io.netty.buffer.Unpooled;
import org.alexdev.kepler.Kepler;
import org.alexdev.kepler.game.commands.registered.AboutCommand;
import org.alexdev.kepler.game.commands.registered.HelpCommand;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.fuserights.FuserightsManager;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerRank;
import org.alexdev.kepler.game.room.entities.RoomEntity;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTextTest {
    @Test
    void aboutKeepsVersionAndOriginalAttribution() {
        String text = AboutCommand.buildText(null);
        assertThat(text).contains("HabboP Hotel", Kepler.SERVER_VERSION,
                "Hotel retro basado en Habbo v14.", "Basado en Kepler de Quackster (RaGEZONE)");
        for (String name : new String[] {"ThuGie", "Webbanditten", "Ascii", "Sefhriloff", "Copyright",
                "Raptosaur", "Hoshiko", "Romuald", "Glaceon", "Nillus", "Holo Team", "Meth0d",
                "office.boy", "killerloader", "Alito", "wackfx"}) {
            assertThat(text).contains(name);
        }
        assertThat(text).doesNotContain("Compilación:");
        assertThat(AboutCommand.buildText("  ")).isEqualTo(text);
        assertThat(AboutCommand.buildText("abc123")).contains("<br>Compilación: abc123");
    }

    @Test
    void alertPreservesSpanishInLatin1() {
        String text = AboutCommand.buildText("abc123") + "<br>á é í ó ú ñ ¿ ¡";
        var buffer = Unpooled.buffer();
        try {
            ALERT alert = new ALERT(text);
            alert.compose(new NettyResponse(alert.getHeader(), buffer));
            // Skip the two-byte header and the trailing string delimiter.
            assertThat(buffer.toString(2, buffer.readableBytes() - 3, StringUtil.getCharset()))
                    .isEqualTo(text);
        } finally {
            buffer.release();
        }
    }

    @Test
    void helpListsEveryVisibleRegisteredCommandAndAliasForEveryRank() {
        CommandManager manager = new CommandManager();
        FuserightsManager rights = new FuserightsManager();
        for (PlayerRank rank : PlayerRank.values()) {
            Entity entity = new RankedEntity(rank, rights);
            String text = HelpCommand.buildText(manager, entity);
            var lines = Arrays.asList(text.split("<br>"));
            int expectedCount = 0;
            for (var entry : manager.getCommands()) {
                Command command = entry.getValue();
                boolean visible = command.getPermissions().isEmpty() || command.getPermissions().stream()
                        .anyMatch(rights.getFuserightsForRank(rank)::contains);
                String line = command.getSyntax(entry.getKey()) + " - " + command.getDescription();
                if (visible) {
                    expectedCount++;
                    assertThat(lines).as("%s: %s", rank, entry.getKey()[0]).contains(line);
                    for (String alias : entry.getKey()) {
                        assertThat(manager.hasCommand(entity, ":" + alias)).isTrue();
                        assertThat(line).contains(":" + alias);
                    }
                } else {
                    assertThat(lines).doesNotContain(line);
                }
            }
            assertThat(lines.stream().filter(line -> line.startsWith(":")).count()).isEqualTo(expectedCount);
        }
    }

    @Test
    void normalPlayersSeeClientCommandsAndAdministratorsSeeLateStaffCommands() {
        CommandManager manager = new CommandManager();
        FuserightsManager rights = new FuserightsManager();
        String normal = HelpCommand.buildText(manager, new RankedEntity(PlayerRank.NORMAL, rights));
        assertThat(normal).contains(":chooser - ", ":furni - ", ":events - ", ":commands", ":help")
                .doesNotContain(":shutdown", ":givecredits", "Page ");
        String admin = HelpCommand.buildText(manager, new RankedEntity(PlayerRank.ADMINISTRATOR, rights));
        assertThat(admin).contains(":givecredits [usuario] [créditos] - ", ":infobus / :bus ",
                ":rgb / :rainbow [segundos opcionales] - ", ":motto [texto opcional] - ");
    }

    /** Uses real rank permission rules without starting the server or database. */
    private static class RankedEntity extends Entity {
        private final PlayerDetails details = new PlayerDetails();
        private final FuserightsManager rights;

        RankedEntity(PlayerRank rank, FuserightsManager rights) {
            details.setRank(rank);
            this.rights = rights;
        }

        @Override public boolean hasFuse(Fuseright permission) { return rights.hasFuseright(permission, details); }
        @Override public PlayerDetails getDetails() { return details; }
        @Override public RoomEntity getRoomUser() { return null; }
        @Override public EntityType getType() { return EntityType.PLAYER; }
        @Override public void dispose() { }
    }
}
