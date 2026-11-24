package org.alexdev.kepler.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class REMOVEITEMTest {
    @Test
    void ownersRemoveAnyWallItem() {
        assertThat(REMOVEITEM.canRemove(true, false, false, false, false)).isTrue();
        assertThat(REMOVEITEM.canRemove(true, false, true, false, false)).isTrue();
    }

    @Test
    void othersNeedTheFuseMatchingTheItem() {
        assertThat(REMOVEITEM.canRemove(false, false, true, false, true)).isTrue();
        assertThat(REMOVEITEM.canRemove(false, true, false, true, false)).isTrue();
        assertThat(REMOVEITEM.canRemove(false, false, true, false, false)).isFalse();
        assertThat(REMOVEITEM.canRemove(false, true, false, false, false)).isFalse();
        assertThat(REMOVEITEM.canRemove(false, false, true, true, false)).isFalse();
        assertThat(REMOVEITEM.canRemove(false, false, false, true, true)).isFalse();
    }
}
