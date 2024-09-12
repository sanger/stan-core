package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link UCMap}
 * @author dr6
 */
public class TestUCMap {
    @Test
    public void testUCMap() {
        UCMap<Integer> map = new UCMap<>();
        map.put("Alpha", 2);
        map.put("beta", 3);
        map.put("Beta", 4);

        assertEquals(2, map.size());
        assertTrue(map.containsKey("alpha"));
        assertTrue(map.containsKey("BETA"));
        assertFalse(map.containsKey("gamma"));

        assertEquals(2, map.get("alpha"));
        assertEquals(2, map.get("Alpha"));
        assertEquals(4, map.get("beta"));

        assertEquals(Map.of("ALPHA", 2, "BETA", 4), map);
        UCMap<Integer> other = new UCMap<>();
        other.putAll(map);
        assertEquals(other, map);
    }

    @Test
    public void testCollectToUCMap() {
        UCMap<Integer> map = Stream.of(0, 1, 2, 4)
                .collect(UCMap.toUCMap(i -> "Hello".substring(i, i+1)));
        assertEquals(Map.of("H", 0, "E", 1, "L", 2, "O", 4), map);
        assertThrows(IllegalStateException.class, () -> Stream.of(1, 2, 3, 4).collect(UCMap.toUCMap(i -> "Hello".substring(i, i+1))));
    }
}
