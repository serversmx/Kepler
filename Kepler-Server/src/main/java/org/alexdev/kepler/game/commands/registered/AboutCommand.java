package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.Kepler;
import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;

public class AboutCommand extends Command {

    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity.getType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player)entity;

        player.send(new ALERT(buildText(System.getenv("SENTRY_RELEASE"))));
    }

    /** Builds the client alert, optionally including the build release. */
    public static String buildText(String release) {
        String build = release == null || release.isBlank() ? "" : "<br>Compilación: " + release.strip();
        return "HabboP Hotel<br><br>" + Kepler.SERVER_VERSION + build +
                "<br>Hotel retro basado en Habbo v14.<br><br>" +
                "Basado en Kepler de Quackster (RaGEZONE)<br>" +
                "Colaboradores originales: ThuGie, Webbanditten, Ascii, Sefhriloff, Copyright, " +
                "Raptosaur, Hoshiko, Romuald, Glaceon, Nillus, Holo Team, Meth0d, office.boy, " +
                "killerloader, Alito, wackfx";
    }

    @Override
    public String getDescription() {
        return "Muestra información y créditos de HabboP";
    }
}
