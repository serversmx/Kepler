package org.alexdev.kepler.game.commands.clientside;

import org.alexdev.kepler.game.commands.Command;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.fuserights.Fuseright;

public class EventsCommand extends Command {
    @Override
    public void addPermissions() {
        this.permissions.add(Fuseright.DEFAULT);
    }

    @Override
    public void handleCommand(Entity entity, String message, String[] args) {

    }

    @Override
    public String getDescription() {
        return "Muestra los eventos organizados por otros usuarios (lo abre el cliente)";
    }
}
