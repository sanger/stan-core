package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.StainType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test {@link EntityNameFilter}
 * @author dr6
 */
public class TestEntityNameFilter {
    @Test
    public void testFilter() {
        EntityNameFilter<StainType> filter = new EntityNameFilter<>(Set.of("alpha", "beta"));

        assertTrue(filter.test(new StainType(1, "Alpha")));
        assertTrue(filter.test(new StainType(1, "alpha")));
        assertTrue(filter.test(new StainType(2, "BETA")));
        assertFalse(filter.test(new StainType(3, "Gamma")));
        assertFalse(filter.test(new StainType(3, "GAMMA")));
    }
}
