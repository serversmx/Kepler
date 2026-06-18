package org.alexdev.kepler.game.pathfinder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AffectedTile.getAffectedTiles computes the tile footprint of a furni given its
 * length/width, position and rotation. The quirk worth pinning: when length and
 * width differ, rotations 0 and 4 swap the two dimensions (the item lies along
 * the other axis), while other rotations keep them as-is. Square items never
 * swap. This drives placement collision and stacking, so the footprint must be
 * exact.
 */
class AffectedTileTest {

    @Test
    void singleTileForAOneByOne() {
        List<Position> tiles = AffectedTile.getAffectedTiles(1, 1, 5, 5, 0);

        assertThat(tiles).containsExactly(new Position(5, 5));
    }

    @Test
    void rectangleRunsAlongYWhenNotRotatedToSwap() {
        // length=2, width=1, rotation 2 -> no swap: footprint runs along Y.
        List<Position> tiles = AffectedTile.getAffectedTiles(2, 1, 5, 5, 2);

        assertThat(tiles).containsExactlyInAnyOrder(new Position(5, 5), new Position(5, 6));
    }

    @Test
    void rotationZeroSwapsLengthAndWidthForNonSquare() {
        // Same 2x1 item, rotation 0 -> swap: footprint now runs along X.
        List<Position> tiles = AffectedTile.getAffectedTiles(2, 1, 5, 5, 0);

        assertThat(tiles).containsExactlyInAnyOrder(new Position(5, 5), new Position(6, 5));
    }

    @Test
    void rotationFourAlsoSwaps() {
        List<Position> tiles = AffectedTile.getAffectedTiles(2, 1, 5, 5, 4);

        assertThat(tiles).containsExactlyInAnyOrder(new Position(5, 5), new Position(6, 5));
    }

    @Test
    void squareItemCoversAllTilesRegardlessOfRotation() {
        List<Position> rotated = AffectedTile.getAffectedTiles(2, 2, 5, 5, 0);
        List<Position> straight = AffectedTile.getAffectedTiles(2, 2, 5, 5, 2);

        Position[] expected = {
                new Position(5, 5), new Position(5, 6),
                new Position(6, 5), new Position(6, 6),
        };
        assertThat(rotated).containsExactlyInAnyOrder(expected);
        assertThat(straight).containsExactlyInAnyOrder(expected);
    }

    @Test
    void footprintSizeIsLengthTimesWidth() {
        assertThat(AffectedTile.getAffectedTiles(3, 2, 0, 0, 2)).hasSize(6);
        assertThat(AffectedTile.getAffectedTiles(3, 1, 0, 0, 0)).hasSize(3);
    }
}
