package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.commands.CommandManager;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;

public class HelpCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity instanceof Player) {
            ((Player) entity).send(new ALERT(buildText(CommandManager.getInstance(), entity)));
        }
    }

    /** Render the actual registry using the same permission check as execution. */
    public static String buildText(CommandManager manager, Entity entity) {
        StringBuilder help = new StringBuilder("Comandos disponibles (los parámetros opcionales se indican):<br><br>");
        for (var entry : manager.getCommands()) {
            Command command = entry.getValue();
            if (manager.hasCommandPermission(entity, command)) {
                help.append(command.getSyntax(entry.getKey()))
                        .append(" - ").append(command.getDescription()).append("<br>");
            }
        }
        return help.toString();
    }

    @Override
    public String getDescription() {
        return "Lista todos los comandos disponibles para tu rango";
    }
}
