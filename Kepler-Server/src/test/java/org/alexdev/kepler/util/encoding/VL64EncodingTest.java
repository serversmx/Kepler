package org.alexdev.kepler.util.encoding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VL64 is the variable-length integer encoding the v14 Habbo client speaks. A
 * regression here corrupts every numeric field on the wire, so the contract we
 * pin is the round-trip: decode(encode(x)) == x across the value ranges the
 * server actually emits (ids, credits, coordinates, timestamps — both signs).
 */
class VL64EncodingTest {

    @ParameterizedTest
    @ValueSource(ints = {
            0, 1, -1, 2, 3, 4, -4, 63, 64, 255, 256, 511, 512,
            4095, 4096, 65535, 65536,
            1_000, -1_000, 1_000_000, -1_000_000,
            16_777_215, 1_073_741_823, 2_000_000_000, -2_000_000_000,
            Integer.MAX_VALUE,
    })
    void roundTripsThroughEncodeAndDecode(int value) {
        assertThat(VL64Encoding.decode(VL64Encoding.encode(value))).isEqualTo(value);
    }

    @Test
    void zeroEncodesToASingleByte() {
        assertThat(VL64Encoding.encode(0)).hasSize(1);
        assertThat(VL64Encoding.decode(VL64Encoding.encode(0))).isZero();
    }

    @Test
    void smallMagnitudesUseOneByteAndLargerOnesGrow() {
        // 2 low bits live in the header byte, so |x| < 4 fits in a single byte.
        assertThat(VL64Encoding.encode(3)).hasSize(1);
        assertThat(VL64Encoding.encode(-3)).hasSize(1);
        // Beyond two bits of magnitude it spills into additional 6-bit bytes.
        assertThat(VL64Encoding.encode(4).length).isGreaterThan(1);
        assertThat(VL64Encoding.encode(1_000_000).length).isGreaterThan(2);
    }

    @Test
    void signIsPreservedIndependentlyOfMagnitude() {
        for (int magnitude : new int[] {1, 7, 64, 9_999, 123_456}) {
            assertThat(VL64Encoding.decode(VL64Encoding.encode(magnitude))).isEqualTo(magnitude);
            assertThat(VL64Encoding.decode(VL64Encoding.encode(-magnitude))).isEqualTo(-magnitude);
        }
    }
}
