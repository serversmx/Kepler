package org.alexdev.kepler.util.encoding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base64 is the fixed-width, big-endian, 6-bits-per-byte integer encoding used
 * for length prefixes and other non-negative fields in the v14 protocol. The
 * contract: encode(value, numBytes) yields exactly numBytes bytes and
 * decode round-trips it, for any value that fits in numBytes*6 bits.
 */
class Base64EncodingTest {

    @ParameterizedTest
    @CsvSource({
            "0,1", "1,1", "5,1", "63,1",
            "64,2", "1000,2", "4095,2",
            "4096,3", "100000,3", "262143,3",
            "1000000,4", "16777215,4",
    })
    void roundTripsWithinTheByteBudget(int value, int numBytes) {
        byte[] encoded = Base64Encoding.encode(value, numBytes);

        assertThat(encoded).hasSize(numBytes);
        assertThat(Base64Encoding.decode(encoded)).isEqualTo(value);
    }

    @Test
    void encodesBigEndianSixBitGroups() {
        // 64 == 0b1000000 -> high group 1, low group 0 over two bytes.
        byte[] encoded = Base64Encoding.encode(64, 2);

        assertThat(encoded[0]).isEqualTo((byte) (0x40 + 1));
        assertThat(encoded[1]).isEqualTo((byte) 0x40);
    }

    @Test
    void everyByteIsInThePrintableBase64Range() {
        // Each output byte is 0x40 + a 6-bit group, so it must land in [0x40, 0x7f].
        for (byte b : Base64Encoding.encode(16_777_215, 4)) {
            assertThat(b & 0xff).isBetween(0x40, 0x7f);
        }
    }
}
