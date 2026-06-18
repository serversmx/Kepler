package org.alexdev.kepler.game.pathfinder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rotation drives which of the 8 directions an avatar faces and walks. A
 * regression makes avatars face/step the wrong way, so the direction table for
 * every neighbouring tile is pinned here.
 */
class RotationTest {

    // calculateHumanDirection: facing from (x1,y1) toward (x2,y2), from origin (5,5).
    @Test
    void humanDirectionCoversAllEightNeighboursPlusSelf() {
        assertThat(Rotation.calculateHumanDirection(5, 5, 4, 4)).isEqualTo(7); // up-left
        assertThat(Rotation.calculateHumanDirection(5, 5, 6, 6)).isEqualTo(3); // down-right
        assertThat(Rotation.calculateHumanDirection(5, 5, 4, 6)).isEqualTo(5); // down-left
        assertThat(Rotation.calculateHumanDirection(5, 5, 6, 4)).isEqualTo(1); // up-right
        assertThat(Rotation.calculateHumanDirection(5, 5, 4, 5)).isEqualTo(6); // left
        assertThat(Rotation.calculateHumanDirection(5, 5, 6, 5)).isEqualTo(2); // right
        assertThat(Rotation.calculateHumanDirection(5, 5, 5, 6)).isEqualTo(4); // down
        assertThat(Rotation.calculateHumanDirection(5, 5, 5, 4)).isEqualTo(0); // up (default branch)
        assertThat(Rotation.calculateHumanDirection(5, 5, 5, 5)).isEqualTo(0); // same tile
    }

    // calculateWalkDirection uses a different table than human direction.
    @Test
    void walkDirectionCoversAllEightNeighbours() {
        assertThat(Rotation.calculateWalkDirection(5, 5, 5, 6)).isEqualTo(4);
        assertThat(Rotation.calculateWalkDirection(5, 5, 5, 4)).isEqualTo(0);
        assertThat(Rotation.calculateWalkDirection(5, 5, 4, 5)).isEqualTo(6);
        assertThat(Rotation.calculateWalkDirection(5, 5, 4, 6)).isEqualTo(5);
        assertThat(Rotation.calculateWalkDirection(5, 5, 4, 4)).isEqualTo(7);
        assertThat(Rotation.calculateWalkDirection(5, 5, 6, 5)).isEqualTo(2);
        assertThat(Rotation.calculateWalkDirection(5, 5, 6, 6)).isEqualTo(3);
        assertThat(Rotation.calculateWalkDirection(5, 5, 6, 4)).isEqualTo(1);
    }

    @Test
    void walkDirectionPositionOverloadDelegatesToCoordinates() {
        assertThat(Rotation.calculateWalkDirection(new Position(5, 5), new Position(6, 6)))
                .isEqualTo(Rotation.calculateWalkDirection(5, 5, 6, 6));
    }

    @Test
    void headRotationOnlyAdjustsOnEvenBodyRotationAndStaysWithinOneStep() {
        // Even body rotation: head nudges one step toward the target.
        Position even = new Position(5, 5, 0, 0, 2); // bodyRotation 2 (even)
        int adjusted = Rotation.getHeadRotation(2, even, new Position(5, 4)); // target "up" -> dir 0, diff>0
        assertThat(adjusted).isEqualTo(1);

        // Odd body rotation: head is not adjusted, stays equal to the body rotation.
        Position odd = new Position(5, 5, 0, 0, 1); // bodyRotation 1 (odd)
        assertThat(Rotation.getHeadRotation(1, odd, new Position(5, 4))).isEqualTo(1);
    }
}
