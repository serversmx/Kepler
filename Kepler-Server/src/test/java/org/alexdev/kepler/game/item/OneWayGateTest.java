package org.alexdev.kepler.game.item;

import org.alexdev.kepler.game.entity.Entity;
import org.alexdev.kepler.game.item.base.ItemDefinition;
import org.alexdev.kepler.game.pathfinder.Position;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.entities.RoomEntity;
import org.alexdev.kepler.game.room.entities.RoomPlayer;
import org.alexdev.kepler.game.room.mapping.RoomMapping;
import org.alexdev.kepler.game.room.mapping.RoomTile;
import org.alexdev.kepler.game.room.models.RoomModel;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Furniture_OneWayDoor_Class computes the tile in front of the door from pDirection[1]:
 * 0 -> (0,-1), 2 -> (+1,0), 4 -> (0,+1), 6 -> (-1,0). The user enters from there and the
 * server lets them out on the opposite side, so the door only works in one direction.
 */
class OneWayGateTest {

    private static Position gateAt(int x, int y, int rotation) {
        Position position = new Position(x, y);
        position.setRotation(rotation);
        return position;
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 4, 5, 6",
            "2, 6, 5, 4, 5",
            "4, 5, 6, 5, 4",
            "6, 4, 5, 6, 5"
    })
    void entranceMatchesTheClientAndExitIsTheOppositeTile(int rotation, int entranceX, int entranceY, int exitX, int exitY) {
        Position gate = gateAt(5, 5, rotation);

        assertThat(OneWayGate.getEntrance(gate)).isEqualTo(new Position(entranceX, entranceY));
        assertThat(OneWayGate.getExit(gate)).isEqualTo(new Position(exitX, exitY));
    }

    @Test
    void entranceAndExitNeverCoincideSoNobodyCrossesBackwards() {
        for (int rotation : new int[] {0, 2, 4, 6}) {
            Position gate = gateAt(5, 5, rotation);

            assertThat(OneWayGate.getEntrance(gate)).isNotEqualTo(OneWayGate.getExit(gate));
            assertThat(OneWayGate.getEntrance(gate)).isNotEqualTo(gate);
            assertThat(OneWayGate.getExit(gate)).isNotEqualTo(gate);
        }
    }

    @Test
    void recognisesGatesByBehaviourOrSprite() {
        assertThat(OneWayGate.isOneWayGate(item(1, "one_way_door*4", ""))).isTrue();
        assertThat(OneWayGate.isOneWayGate(item(2, "something_else", "one_way_gate"))).isTrue();
        assertThat(OneWayGate.isOneWayGate(item(3, "exe_gate", "solid,gate"))).isFalse();
        assertThat(OneWayGate.isOneWayGate(null)).isFalse();
    }

    @Test
    void gateTileIsClosedToEveryoneNotCrossingIt() {
        Item gate = item(10, "one_way_door*1", "");
        gate.setPosition(gateAt(5, 5, 0));

        assertThat(gate.isWalkable(entityAt(5, 4))).isFalse(); // in front, not crossing
        assertThat(gate.isWalkable(entityAt(5, 6))).isFalse(); // other side: no way back
        assertThat(gate.isWalkable(null)).isFalse();
    }

    @Test
    void userStandingInsideTheGateCanAlwaysWalkOut() {
        Item gate = item(11, "one_way_door*1", "");
        gate.setPosition(gateAt(5, 5, 0));

        assertThat(gate.isWalkable(entityAt(5, 5))).isTrue();
    }

    // --- crossings: identity and cleanup ---

    private static Player playerIn(Room room) {
        RoomPlayer roomUser = mock(RoomPlayer.class);
        when(roomUser.getRoom()).thenReturn(room);

        Player player = mock(Player.class);
        when(player.getRoomUser()).thenReturn(roomUser);
        return player;
    }

    @Test
    void leavingTheRoomClosesTheGateAndFreesIt() {
        Room room = mock(Room.class);
        Player player = playerIn(room);

        OneWayGate.Crossing crossing = OneWayGate.begin(100, player, room, new Position(5, 6), true);
        assertThat(crossing).isNotNull();
        assertThat(OneWayGate.begin(100, playerIn(room), room, new Position(5, 6), true)).isNull(); // busy

        OneWayGate.cancel(player);

        verify(room).send(argThat((MessageComposer m) -> m.getHeader() == 312));
        assertThat(OneWayGate.begin(100, playerIn(room), room, new Position(5, 6), true)).isNotNull();
        OneWayGate.cancel(null);
    }

    @Test
    void staleTimerCannotCloseOrUnlockANewerCrossing() {
        Room room = mock(Room.class);
        Player player = playerIn(room);

        OneWayGate.Crossing old = OneWayGate.begin(101, player, room, new Position(5, 6), true);
        OneWayGate.cancel(player); // player left the room

        OneWayGate.Crossing current = OneWayGate.begin(101, player, room, new Position(5, 6), false);
        reset(room);

        OneWayGate.finishCrossing(101, old, true); // old timeout fires late

        verify(room, never()).send(any(MessageComposer.class));
        verify(player.getRoomUser(), never()).setWalkingAllowed(true);

        OneWayGate.finishCrossing(101, current, true);
        verify(room).send(any(MessageComposer.class));
    }

    // --- heights: same rules as the pathfinder ---

    private static Room roomFor(double entranceZ, double gateZ, double exitZ) {
        Room room = mock(Room.class);
        RoomModel model = mock(RoomModel.class);
        RoomMapping mapping = mock(RoomMapping.class);

        when(model.getName()).thenReturn("model_a");
        when(model.getDoorLocation()).thenReturn(new Position(0, 0));
        when(room.getModel()).thenReturn(model);
        when(room.getMapping()).thenReturn(mapping);

        RoomTile entrance = new RoomTile(room, new Position(5, 4), entranceZ);
        RoomTile gate = new RoomTile(room, new Position(5, 5), gateZ);
        RoomTile exit = new RoomTile(room, new Position(5, 6), exitZ);

        when(mapping.getTile(any(Position.class))).thenAnswer(inv -> {
            Position p = inv.getArgument(0);
            if (p.getX() != 5) return null;
            return p.getY() == 4 ? entrance : p.getY() == 5 ? gate : p.getY() == 6 ? exit : null;
        });

        return room;
    }

    @Test
    void flatGateCanBeCrossed() {
        Room room = roomFor(0, 0, 0);
        Entity entity = playerAt(room, 5, 4);

        assertThat(OneWayGate.canCross(room, entity, new Position(5, 4), new Position(5, 5), new Position(5, 6))).isTrue();
    }

    @Test
    void gateOnTopOfASharpStepIsRejectedLikeThePathfinderDoes() {
        Entity entity;

        Room liftRoom = roomFor(0, 0, 3);
        entity = playerAt(liftRoom, 5, 4);
        assertThat(OneWayGate.canCross(liftRoom, entity, new Position(5, 4), new Position(5, 5), new Position(5, 6))).isFalse();

        Room dropRoom = roomFor(0, 0, -4);
        entity = playerAt(dropRoom, 5, 4);
        assertThat(OneWayGate.canCross(dropRoom, entity, new Position(5, 4), new Position(5, 5), new Position(5, 6))).isFalse();
    }

    private static Entity playerAt(Room room, int x, int y) {
        RoomPlayer roomUser = mock(RoomPlayer.class);
        when(roomUser.getRoom()).thenReturn(room);
        when(roomUser.getPosition()).thenReturn(new Position(x, y));

        Player player = mock(Player.class);
        when(player.getRoomUser()).thenReturn(roomUser);
        return player;
    }

    private static Item item(int id, String sprite, String behaviour) {
        ItemDefinition definition = new ItemDefinition(1901, sprite, "Gate", "One at a time!", behaviour,
                "default", 0, 1, 1, "0,0,0", "", true, true);

        Item item = new Item() {
            @Override
            public ItemDefinition getDefinition() {
                return definition;
            }
        };

        item.setId(id);
        return item;
    }

    private static Entity entityAt(int x, int y) {
        RoomEntity roomUser = mock(RoomEntity.class);
        when(roomUser.getPosition()).thenReturn(new Position(x, y));

        Entity entity = mock(Entity.class);
        when(entity.getRoomUser()).thenReturn(roomUser);
        return entity;
    }
}
