package org.alexdev.kepler.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BitUtil packs ints as 4 big-endian bytes for the binary frame headers. The
 * round-trip must hold across the full int range (including the sign bit), and
 * the byte order must stay most-significant-first.
 */
class BitUtilTest {

    @ParameterizedTest
    @ValueSource(ints = {
            0, 1, -1, 2, -2, 127, 128, 255, 256, -256,
            65_535, 65_536, 16_777_215, -16_777_216,
            Integer.MAX_VALUE, Integer.MIN_VALUE,
    })
    void roundTripsThroughBytes(int value) {
        assertThat(BitUtil.bytesToInt(BitUtil.intToBytes(value))).isEqualTo(value);
    }

    @Test
    void intToBytesIsBigEndian() {
        byte[] bytes = BitUtil.intToBytes(0x01020304);

        assertThat(bytes).containsExactly((byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04);
    }

    @Test
    void bytesToIntReadsBigEndian() {
        int value = BitUtil.bytesToInt(new byte[] {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04});

        assertThat(value).isEqualTo(0x01020304);
    }
}
