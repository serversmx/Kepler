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

    /** The v14 alert window grows to fit its longest line: keep every line at most this long. */
    public static final int MAX_LINE = 58;

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity instanceof Player) {
            CommandManager manager = CommandManager.getInstance();
            String text = args.length > 0 ? buildDetailText(manager, entity, args[0]) : buildText(manager, entity);
            ((Player) entity).send(new ALERT(text));
        }
    }

    /** One short line per command the entity may use; `:help comando` shows the details. */
    public static String buildText(CommandManager manager, Entity entity) {
        StringBuilder help = new StringBuilder("Comandos disponibles:<br>(usa :help comando para ver detalles)<br><br>");
        for (var entry : manager.getCommands()) {
            Command command = entry.getValue();
            if (manager.hasCommandPermission(entity, command)) {
                help.append(wrap(":" + entry.getKey()[0] + " - " + command.getDescription())).append("<br>");
            }
        }
        return help.toString();
    }

    /** Syntax, aliases and description of one command, or a short error if it is unknown or not allowed. */
    public static String buildDetailText(CommandManager manager, Entity entity, String name) {
        String wanted = name.startsWith(":") ? name.substring(1) : name;
        for (var entry : manager.getCommands()) {
            Command command = entry.getValue();
            for (String alias : entry.getKey()) {
                if (alias.equalsIgnoreCase(wanted) && manager.hasCommandPermission(entity, command)) {
                    return wrap(command.getSyntax(entry.getKey())) + "<br><br>" + wrap(command.getDescription());
                }
            }
        }
        return "No existe el comando :" + wanted + "<br>o no está disponible para tu rango.";
    }

    /** Word-wraps text into lines of at most MAX_LINE characters; continuation lines are indented. */
    public static String wrap(String text) {
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > MAX_LINE) {
                out.append(line).append("<br>");
                line.setLength(0);
                line.append("   ");
            }
            if (!line.isEmpty() && !line.toString().isBlank()) {
                line.append(' ');
            }
            // A single word longer than the limit is split hard (e.g. long option lists).
            while (line.length() + word.length() > MAX_LINE) {
                int cut = MAX_LINE - line.length();
                out.append(line).append(word, 0, cut).append("<br>");
                word = word.substring(cut);
                line.setLength(0);
                line.append("   ");
            }
            line.append(word);
        }
        return out.append(line).toString();
    }

    @Override
    public String getDescription() {
        return "Lista todos los comandos disponibles para tu rango";
    }
}
