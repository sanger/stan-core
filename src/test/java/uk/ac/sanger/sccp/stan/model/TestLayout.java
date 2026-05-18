package uk.ac.sanger.sccp.stan.model;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link Layout}
 * @author dr6
 */
public class TestLayout {
    @Test
    void testAddressIndex() {
        Layout layout = new Layout(3,5);
        Address[] addresses = Arrays.stream("A1 A2 A4 A5 B1 B5 C1 C5 A6 B6 D1 D2".split("\\s+")).map(Address::valueOf).toArray(Address[]::new);
        int[] indexes = {0,1,3,4,5,9,10,14, -1,-1,-1,-1};
        Zip.of(Arrays.stream(addresses), Arrays.stream(indexes).boxed())
                .forEach((ad, i) -> {
                    assertEquals(i, layout.indexOf(ad));
                    if (i >= 0) {
                        assertEquals(ad, layout.addressAt(i));
                    }
                });
        IntStream.of(-1, 16, 17, 20, 21).forEach(i -> assertNull(layout.addressAt(i)));
    }
}
