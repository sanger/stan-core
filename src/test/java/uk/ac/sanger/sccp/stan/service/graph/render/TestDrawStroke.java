package uk.ac.sanger.sccp.stan.service.graph.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDrawStroke {
    @Test
    void testDrawStroke() {
        DrawStroke stroke = new DrawStroke(2, 5,5);
        assertEquals(2, stroke.getWidth());
        assertThat(stroke.getDashArray()).containsExactly(5,5);
        DrawStroke other = new DrawStroke(2, 5,5);
        assertEquals(other, stroke);
        assertEquals(other.hashCode(), stroke.hashCode());
    }
}