package org.alexdev.kepler.game.commands.registered;

import org.alexdev.kepler.Kepler;
import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.entity.EntityType;
import org.alexdev.kepler.game.fuserights.Fuseright;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.messages.outgoing.alert.ALERT;
import org.alexdev.kepler.util.DateUtil;

public class UptimeCommand extends Command {
    private static final int UPTIME_COMMAND_INTERVAL_SECONDS = 5;
    private static long UPTIME_COMMAND_EXPIRY = 0L;

    private static final int CPU_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final String CPU_ARCHITECTURE = System.getProperty("os.arch");
    private static final String JVM_NAME = System.getProperty("java.vm.name");
    private static final String OPERATING_SYSTEM_NAME = System.getProperty("os.name");

    private static int MEMORY_USAGE = 0;
    private static int ACTIVE_PLAYERS = 0;
    private static int AUTHENTICATED_PLAYERS = 0;

    public UptimeCommand(){
        UPTIME_COMMAND_EXPIRY = DateUtil.getCurrentTimeSeconds();
    }

    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {
        if (entity.getType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) entity;

        if (DateUtil.getCurrentTimeSeconds() > UPTIME_COMMAND_EXPIRY) {
            AUTHENTICATED_PLAYERS = PlayerManager.getInstance().getPlayers().size();
            ACTIVE_PLAYERS = PlayerManager.getInstance().getActivePlayers().size();

            Runtime runtime = Runtime.getRuntime();
            MEMORY_USAGE = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);

            UPTIME_COMMAND_EXPIRY = DateUtil.getCurrentTimeSeconds() + UPTIME_COMMAND_INTERVAL_SECONDS;
        }

        long uptime = (DateUtil.getCurrentTimeSeconds() - Kepler.getStartupTime()) * 1000;
        long days = (uptime / (1000 * 60 * 60 * 24));
        long hours = (uptime - days * (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (uptime - days * (1000 * 60 * 60 * 24) - hours * (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime - days * (1000 * 60 * 60 * 24) - hours * (1000 * 60 * 60) - minutes * (1000 * 60)) / (1000);

        StringBuilder msg = new StringBuilder();
        msg.append("SERVIDOR\r");
        msg.append("Tiempo activo: " + days + " días, " + hours + " horas, " + minutes + " minutos y " + seconds + " segundos<br>");
        msg.append("Hay " + ACTIVE_PLAYERS + " usuarios activos y " + AUTHENTICATED_PLAYERS + " usuarios autenticados<br>");
        msg.append("Máximo de usuarios conectados hoy: " + PlayerManager.getInstance().getDailyPlayerPeak() + "<br>");
        msg.append("<br>");
        msg.append("SISTEMA<br>");
        msg.append("Arquitectura de CPU: " + CPU_ARCHITECTURE + "<br>");
        msg.append("Núcleos de CPU: " + CPU_NUM_THREADS + "<br>");
        msg.append("Memoria en uso: " + MEMORY_USAGE + " MB<br>");
        msg.append("JVM: " + JVM_NAME + "<br>");
        msg.append("Sistema operativo: " + OPERATING_SYSTEM_NAME);

        player.send(new ALERT(msg.toString()));
    }

    @Override
    public String getDescription() {
        return "Muestra el tiempo activo y el estado del servidor";
    }
}
