package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.infobus.InfobusManager;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.messages.outgoing.rooms.user.CHAT_MESSAGE;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.util.StringUtil;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.stream.IntStream;

public class InfobusCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.ADMINISTRATOR_ACCESS);
    }

    @Override
    public void addArguments() {
        this.arguments.add("subcomando");
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {

        if (entity.getType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) entity;

        if (player.getRoomUser().getRoom() == null) {
            return;
        }

        Room room = player.getRoomUser().getRoom();
        InfobusManager bus = InfobusManager.getInstance();

        if(args[0].equalsIgnoreCase("help")) {

            StringBuilder help = new StringBuilder();
            help.append("COMANDOS DEL INFOBUS:<br>").append("<br>");
            help.append(":infobus help - Muestra esta ayuda").append("<br>");
            help.append(":infobus open - Abre la puerta").append("<br>");
            help.append(":infobus close - Cierra la puerta").append("<br>");
            help.append(":infobus question [texto] - Define la pregunta").append("<br>");
            help.append(":infobus option add [texto] - Agrega una opción").append("<br>");
            help.append(":infobus option remove [número] - Quita una opción").append("<br>");
            help.append(":infobus start - Inicia la encuesta").append("<br>");
            help.append(":infobus status - Muestra la encuesta").append("<br>");
            help.append(":infobus reset - Borra pregunta, opciones y votos");

            player.send(new ALERT(help.toString()));
        }

        if(args[0].equalsIgnoreCase("status")) {
            player.send(new ALERT(bus.constructStatus()));
        }

        if(args[0].equalsIgnoreCase("start")) {
            if(bus.getQuestion() != null && !bus.getQuestion().isEmpty()) {
                //System.out.println("Question is not empty");
                if(bus.getOptions().size() > 0) {
                    //System.out.println("Starting");
                    bus.startPoll();
                } else {
                    player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Agrega opciones a la pregunta: :infobus option add [texto] (remove [número] para quitar una)"));
                }
            } else {
                player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Primero define una pregunta: :infobus question [pregunta]"));
            }
        }

        if(args[0].equalsIgnoreCase("reset")) {
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Se restablecieron la pregunta, las opciones y los votos."));
            bus.reset();
        }

        if(args[0].equalsIgnoreCase("question")) {
            String question = StringUtil.filterInput(String.join(" ", IntStream.range(1, args.length).mapToObj(i -> args[i]).toArray(String[]::new)), true);
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "La pregunta del Infobus ahora es: " + question));
            bus.setQuestion(question);
        }

        if(args[0].equalsIgnoreCase("option") && args[1].equalsIgnoreCase("add")) {

            if(args[2] == null) {
                player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Falta el texto de la opción"));
            }

            String option = StringUtil.filterInput(String.join(" ", IntStream.range(2, args.length).mapToObj(i -> args[i]).toArray(String[]::new)), true);
            bus.addOption(option);
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Opción agregada. Consulta la encuesta con :infobus status"));

        } else if(args[0].equalsIgnoreCase("option") && args[1].equalsIgnoreCase("remove")) {

            if(!NumberUtils.isCreatable(args[2])) {
                player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Para quitar una opción, usa el número que aparece en :infobus status."));
            } else {
                 bus.removeOption(Integer.parseInt(args[2]));
                player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Opción eliminada. Consulta la encuesta con :infobus status"));
            }

        } else if(args[0].equalsIgnoreCase("option") && args[1].isEmpty()) {
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Uso: :infobus option add [texto] o :infobus option remove [número]"));
        }

        if(args[0].equalsIgnoreCase("close")) {
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Puerta del Infobus cerrada."));
            InfobusManager.getInstance().closeDoor(room);
        }

        if(args[0].equalsIgnoreCase("open")) {
            player.send(new CHAT_MESSAGE(CHAT_MESSAGE.ChatMessageType.WHISPER, player.getRoomUser().getInstanceId(), "Puerta del Infobus abierta."));
            InfobusManager.getInstance().openDoor(room);
        }
    }

    @Override
    public String getUsageArguments() {
        return "[help/open/close/question/option/status/start/reset] [datos según subcomando]";
    }

    @Override
    public String getDescription() {
        return "Administra el Infobus y sus encuestas; usa :infobus help";
    }
}