package org.alexdev.kepler.game.item;

import org.alexdev.kepler.game.GameScheduler;
import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.item.base.ItemBehaviour;
import org.alexdev.kepler.game.pathfinder.Pathfinder;
import org.alexdev.kepler.game.pathfinder.Position;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.messages.outgoing.rooms.items.ONEWAY_DOOR_STATUS;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One way doors (one_way_door*).
 *
 * The client (hh_furni_classes Furniture_OneWayDoor_Class) treats the door as the
 * tile in front of it for rotation 0/2/4/6 = (0,-1)/(+1,0)/(0,+1)/(-1,0), which is
 * exactly Position#getSquareInFront. The user enters from that tile and leaves through
 * the opposite one (Position#getSquareBehind). Its select handler returns 0, so a click
 * falls back to a plain MOVE towards the door tile: WALK hands that to {@link #requestPass}.
 * ENTER_ONEWAY_DOOR (232) from the original double click flow is also supported.
 *
 * The door tile is never walkable, except for the one user crossing it (or one that
 * already stands on it, so nobody can get stuck). While crossing, listener 312 is told
 * the door is open (1), and closed (0) when the user has left.
 */
public final class OneWayGate {
    /** Hard limit for a crossing: the door always closes and the user gets control back. */
    static final long CROSSING_TIMEOUT_MS = 4000;
    /** Delay between the user reaching the other side and the door closing. */
    static final long CLOSE_DELAY_MS = 500;

    private static final Map<Integer, Crossing> CROSSINGS = new ConcurrentHashMap<>();

    private OneWayGate() { }

    static class Crossing {
        private final Player player;
        private final Room room;
        private final Position exit;
        private final boolean previousWalkingAllowed;
        private volatile ScheduledFuture<?> timeout;
        private volatile boolean closing;

        private Crossing(Player player, Room room, Position exit, boolean previousWalkingAllowed) {
            this.player = player;
            this.room = room;
            this.exit = exit;
            this.previousWalkingAllowed = previousWalkingAllowed;
        }
    }

    /**
     * Whether the item is a one way gate, either by behaviour or by its one_way_door* sprite.
     */
    public static boolean isOneWayGate(Item item) {
        if (item == null || item.getDefinition() == null) {
            return false;
        }

        if (item.hasBehaviour(ItemBehaviour.ONE_WAY_GATE)) {
            return true;
        }

        String sprite = item.getDefinition().getSprite();
        return sprite != null && sprite.startsWith("one_way_door");
    }

    /**
     * The tile the user has to stand on to go through (where the client expects them).
     */
    public static Position getEntrance(Position gate) {
        return gate.getSquareInFront();
    }

    /**
     * The tile the user ends up on, on the opposite side of the entrance.
     */
    public static Position getExit(Position gate) {
        return gate.getSquareBehind();
    }

    /**
     * Whether the entity can currently step onto the gate tile: only the user that is crossing
     * it, or one already standing on it (so a stuck user can always walk away).
     */
    public static boolean canWalk(Item gate, Entity entity) {
        if (entity == null) {
            return false;
        }

        Crossing crossing = CROSSINGS.get(gate.getId());

        if (crossing != null && crossing.player == entity) {
            return true;
        }

        return entity.getRoomUser() != null
                && entity.getRoomUser().getPosition() != null
                && entity.getRoomUser().getPosition().equals(gate.getPosition());
    }

    /**
     * Whether someone is crossing the gate right now.
     */
    public static boolean isBusy(Item gate) {
        return CROSSINGS.containsKey(gate.getId());
    }

    /**
     * The user asked to use the gate (clicked it): walk to the entrance first if needed and cross
     * from there. Always consumes the request, an invalid attempt is silently ignored.
     */
    public static void requestPass(Player player, Item gate) {
        RoomPlayer roomUser = player.getRoomUser();
        Room room = gate.getRoom();

        roomUser.setPendingOneWayGateId(-1);

        if (room == null || roomUser.getRoom() != room || isBusy(gate)) {
            return;
        }

        Position position = roomUser.getPosition();
        Position entrance = getEntrance(gate.getPosition());

        // Standing inside the door: walk out to the far side
        if (position.equals(gate.getPosition())) {
            Position exit = getExit(gate.getPosition());
            roomUser.walkTo(exit.getX(), exit.getY());
            return;
        }

        if (position.equals(entrance) && !roomUser.isWalking()) {
            startCrossing(player, gate);
            return;
        }

        roomUser.setPendingOneWayGateId(gate.getId());

        if (!roomUser.walkTo(entrance.getX(), entrance.getY())) {
            roomUser.setPendingOneWayGateId(-1);
        }
    }

    /**
     * Called whenever a user stops walking: finishes a pending approach to a gate and
     * closes the gate once the crossing user reached the other side.
     */
    public static void onPlayerStopped(Player player) {
        RoomPlayer roomUser = player.getRoomUser();
        Room room = roomUser.getRoom();

        for (Map.Entry<Integer, Crossing> entry : CROSSINGS.entrySet()) {
            Crossing crossing = entry.getValue();

            if (crossing.player == player && !crossing.closing && roomUser.getPosition().equals(crossing.exit)) {
                crossing.closing = true;
                GameScheduler.getInstance().getService().schedule(
                        () -> finishCrossing(entry.getKey(), crossing, true), CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }

        int pendingId = roomUser.getPendingOneWayGateId();

        if (pendingId == -1) {
            return;
        }

        roomUser.setPendingOneWayGateId(-1);

        if (room == null) {
            return;
        }

        Item gate = room.getItemManager().getById(pendingId);

        if (gate != null && isOneWayGate(gate) && roomUser.getPosition().equals(getEntrance(gate.getPosition()))) {
            startCrossing(player, gate);
        }
    }

    /**
     * Handles ENTER_ONEWAY_DOOR: the user must already stand on the entrance tile.
     */
    public static void enter(Player player, int itemId) {
        Room room = player.getRoomUser().getRoom();

        if (room == null) {
            return;
        }

        Item gate = room.getItemManager().getById(itemId);

        if (gate == null || !isOneWayGate(gate)) {
            return;
        }

        if (player.getRoomUser().getPosition().equals(getEntrance(gate.getPosition())) && !player.getRoomUser().isWalking()) {
            startCrossing(player, gate);
        }
    }

    private static boolean startCrossing(Player player, Item gate) {
        Room room = gate.getRoom();
        RoomPlayer roomUser = player.getRoomUser();

        if (room == null || roomUser.getRoom() != room || !roomUser.isWalkingAllowed() || hasCrossing(player)) {
            return false;
        }

        Position entrance = getEntrance(gate.getPosition());
        Position exit = getExit(gate.getPosition());

        Crossing crossing = begin(gate.getId(), player, room, exit, roomUser.isWalkingAllowed());

        if (crossing == null) {
            return false;
        }

        // Same walkability and height rules as the pathfinder (the gate is walkable now for this user).
        // If the other side is blocked or the heights do not allow the steps, keep the door shut.
        if (!canCross(room, player, entrance, gate.getPosition(), exit)) {
            CROSSINGS.remove(gate.getId(), crossing);
            return false;
        }

        roomUser.setWalkingAllowed(false);
        room.send(new ONEWAY_DOOR_STATUS(gate.getId(), true));

        crossing.timeout = GameScheduler.getInstance().getService().schedule(
                () -> finishCrossing(gate.getId(), crossing, true), CROSSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (!roomUser.walkAlong(List.of(gate.getPosition().copy(), exit.copy()))) {
            finishCrossing(gate.getId(), crossing, true);
            return false;
        }

        return true;
    }

    /**
     * Validates entrance -> gate -> exit with the pathfinder's own step rules (tile validity,
     * furniture walkability and the lift/drop height limits).
     */
    static boolean canCross(Room room, Entity entity, Position entrance, Position gate, Position exit) {
        return Pathfinder.isValidStep(room, entity, entrance, gate, false)
                && Pathfinder.isValidStep(room, entity, gate, exit, true);
    }

    /**
     * Registers a crossing for the gate, or returns null if the gate is already in use.
     */
    static Crossing begin(int gateId, Player player, Room room, Position exit, boolean previousWalkingAllowed) {
        Crossing crossing = new Crossing(player, room, exit, previousWalkingAllowed);
        return CROSSINGS.putIfAbsent(gateId, crossing) == null ? crossing : null;
    }

    private static boolean hasCrossing(Player player) {
        for (Crossing crossing : CROSSINGS.values()) {
            if (crossing.player == player) {
                return true;
            }
        }

        return false;
    }

    /**
     * Drops every crossing of this user (they left the room or disconnected): the doors close
     * and any pending timer of the old crossings becomes a no-op.
     */
    public static void cancel(Player player) {
        for (Map.Entry<Integer, Crossing> entry : CROSSINGS.entrySet()) {
            if (entry.getValue().player == player) {
                finishCrossing(entry.getKey(), entry.getValue(), false);
            }
        }
    }

    /**
     * Ends a crossing. Scheduled tasks only act if this exact crossing is still the current one
     * for the gate, so a stale timer can never close or unlock a newer crossing.
     */
    static void finishCrossing(int gateId, Crossing crossing, boolean restoreWalking) {
        if (!CROSSINGS.remove(gateId, crossing)) {
            return;
        }

        ScheduledFuture<?> timeout = crossing.timeout;

        if (timeout != null) {
            timeout.cancel(false);
        }

        // The door closes for everyone even if the user already left the room
        crossing.room.send(new ONEWAY_DOOR_STATUS(gateId, false));

        RoomPlayer roomUser = crossing.player.getRoomUser();

        if (restoreWalking && roomUser.getRoom() == crossing.room) {
            roomUser.setWalkingAllowed(crossing.previousWalkingAllowed);
        }
    }
}
