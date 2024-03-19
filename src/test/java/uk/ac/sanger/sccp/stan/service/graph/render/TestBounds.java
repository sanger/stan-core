package uk.ac.sanger.sccp.stan.service.graph.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test {@link Bounds} */
class TestBounds {

    @Test
    void testBounds() {
        Bounds b = new Bounds(5,10,20,30);
        assertEquals(5, b.x());
        assertEquals(10, b.y());
        assertEquals(20, b.width());
        assertEquals(30, b.height());
        assertEquals(15, b.centreX());
        assertEquals(25, b.centreY());
    }
}