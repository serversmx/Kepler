package org.alexdev.kepler.game.pathfinder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Position is the core tile-geometry primitive. These pin the non-obvious
 * behaviours future edits could easily break: getDistanceSquared actually
 * returns the rounded EUCLIDEAN distance (not the square, despite the name),
 * subtract() computes other-minus-this (reversed), equals() compares only X/Y,
 * and the square-in-direction helpers encode the 8-rotation offset table.
 */
class PositionTest {

    @Test
    void constructorsSetCoordinates() {
        Position p = new Position(3, 4);
        assertThat(p.getX()).isEqualTo(3);
        assertThat(p.getY()).isEqualTo(4);
        assertThat(p.getZ()).isEqualTo(0.0);

        assertThat(new Position(3, 4, 2.5).getZ()).isEqualTo(2.5);
    }

    @Test
    void distanceIsRoundedEuclideanNotSquared() {
        // (int) sqrt(3^2 + 4^2) == 5 — NOT 25. The method name is misleading.
        assertThat(new Position(0, 0).getDistanceSquared(new Position(3, 4))).isEqualTo(5);
        assertThat(new Position(0, 0).getDistanceSquared(new Position(0, 0))).isEqualTo(0);
    }

    @Test
    void touchesIncludesOrthogonalAndDiagonalNeighbours() {
        Position origin = new Position(0, 0);
        assertThat(origin.touches(new Position(1, 0))).isTrue();  // orthogonal, dist 1
        assertThat(origin.touches(new Position(1, 1))).isTrue();  // diagonal, sqrt(2)->1
        assertThat(origin.touches(new Position(2, 0))).isFalse(); // dist 2
    }

    @Test
    void addIsCommutativeButSubtractIsOtherMinusThis() {
        assertThat(new Position(5, 5).add(new Position(8, 9)))
                .isEqualTo(new Position(13, 14));

        // subtract returns other - this (reversed from the usual this - other).
        Position diff = new Position(5, 5).subtract(new Position(8, 9));
        assertThat(diff.getX()).isEqualTo(3);
        assertThat(diff.getY()).isEqualTo(4);
    }

    @Test
    void squareInFrontFollowsTheRotationOffsetTable() {
        assertThat(squareInFront(0)).isEqualTo(new Position(5, 4)); // north: Y--
        assertThat(squareInFront(2)).isEqualTo(new Position(6, 5)); // east:  X++
        assertThat(squareInFront(4)).isEqualTo(new Position(5, 6)); // south: Y++
        assertThat(squareInFront(6)).isEqualTo(new Position(4, 5)); // west:  X--
    }

    @Test
    void squareBehindIsOppositeOfFront() {
        Position p = new Position(5, 5);
        p.setRotation(0);
        assertThat(p.getSquareBehind()).isEqualTo(new Position(5, 6)); // behind north is south
    }

    @Test
    void squareRightAndLeftForFacingNorth() {
        Position p = new Position(5, 5);
        p.setRotation(0); // facing north
        assertThat(p.getSquareRight()).isEqualTo(new Position(6, 5)); // east
        assertThat(p.getSquareLeft()).isEqualTo(new Position(4, 5));  // west
    }

    @Test
    void equalsComparesOnlyXAndY() {
        assertThat(new Position(5, 5, 3.0, 1, 1)).isEqualTo(new Position(5, 5, 9.0, 7, 7));
        assertThat(new Position(5, 5)).isNotEqualTo(new Position(6, 5));
        assertThat(new Position(5, 5).equals(null)).isFalse();
        assertThat(new Position(5, 5).equals("[5, 5]")).isFalse();
    }

    @Test
    void setRotationSetsBothHeadAndBodyButGetRotationReturnsBody() {
        Position p = new Position(5, 5);
        p.setRotation(3);
        assertThat(p.getBodyRotation()).isEqualTo(3);
        assertThat(p.getHeadRotation()).isEqualTo(3);
        assertThat(p.getRotation()).isEqualTo(3);
    }

    @Test
    void copyPreservesAllFieldsAsANewInstance() {
        Position original = new Position(5, 6, 1.5, 2, 4);
        Position copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getX()).isEqualTo(5);
        assertThat(copy.getY()).isEqualTo(6);
        assertThat(copy.getZ()).isEqualTo(1.5);
        assertThat(copy.getHeadRotation()).isEqualTo(2);
        assertThat(copy.getBodyRotation()).isEqualTo(4);
    }

    @Test
    void toStringIsBracketedCoordinates() {
        assertThat(new Position(5, 7).toString()).isEqualTo("[5, 7]");
    }

    private Position squareInFront(int rotation) {
        Position p = new Position(5, 5);
        p.setRotation(rotation);
        return p.getSquareInFront();
    }
}
