package org.alexdev.kepler.game.games.snowstorm.mapping;

import org.alexdev.kepler.game.games.player.GamePlayer;
import org.alexdev.kepler.game.games.snowstorm.SnowStormGame;
import org.alexdev.kepler.game.games.snowstorm.objects.SnowballObject;
import org.alexdev.kepler.game.games.snowstorm.util.SnowStormActivityState;
import org.alexdev.kepler.game.games.snowstorm.util.SnowStormAttributes;
import org.alexdev.kepler.game.pathfinder.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A short snowball must be blocked by an opponent standing on the tile OR
 * walking into it (next goal). The next-goal check used to compare a Position
 * with the GamePlayer itself, so it never matched (bug inherited from upstream).
 */
class SnowStormPathfinderTest {

    private SnowballObject snowball;
    private SnowStormAttributes targetAttributes;

    @BeforeEach
    void setUp() {
        GamePlayer thrower = mock(GamePlayer.class);
        GamePlayer target = mock(GamePlayer.class);
        SnowStormGame game = mock(SnowStormGame.class);
        SnowStormMap map = mock(SnowStormMap.class);

        targetAttributes = spy(new SnowStormAttributes());
        targetAttributes.setCurrentPosition(new Position(1, 1));
        targetAttributes.setNextGoal(new Position(2, 2));
        when(target.getSnowStormAttributes()).thenReturn(targetAttributes);

        when(game.getActivePlayers()).thenReturn(List.of(target));
        when(game.isOppositionPlayer(thrower, target)).thenReturn(true);
        when(game.getMap()).thenReturn(map); // map.getTile(...) -> null: no static obstacle

        snowball = mock(SnowballObject.class);
        when(snowball.getTrajectory()).thenReturn(SnowballObject.SnowballTrajectory.SHORT_TRAJECTORY);
        when(snowball.getGame()).thenReturn(game);
        when(snowball.getThrower()).thenReturn(thrower);
    }

    @Test
    void opponentStandingOnTileBlocksSnowball() {
        assertThat(SnowStormPathfinder.isBlockedTile(snowball, new Position(1, 1))).isTrue();
    }

    @Test
    void opponentWalkingIntoTileBlocksSnowball() {
        assertThat(SnowStormPathfinder.isBlockedTile(snowball, new Position(2, 2))).isTrue();
    }

    @Test
    void unrelatedTileIsNotBlocked() {
        assertThat(SnowStormPathfinder.isBlockedTile(snowball, new Position(5, 5))).isFalse();
    }

    @Test
    void stunnedOpponentDoesNotBlock() {
        // setActivityState() would schedule a reset on GameScheduler; stub the getter instead.
        doReturn(SnowStormActivityState.ACTIVITY_STATE_STUNNED).when(targetAttributes).getActivityState();

        assertThat(SnowStormPathfinder.isBlockedTile(snowball, new Position(2, 2))).isFalse();
    }
}
