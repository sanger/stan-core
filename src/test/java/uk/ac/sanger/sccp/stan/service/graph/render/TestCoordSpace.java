package uk.ac.sanger.sccp.stan.service.graph.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test {@link CoordSpace} */
class TestCoordSpace {
    @Test
    void testCoordSpace() {
        CoordSpace coords = new CoordSpace();
        coords.setMinZoom(0.5f);
        assertEquals(0.5f, coords.getMinZoom());
        coords.setMaxZoom(2.5f);
        assertEquals(2.5f, coords.getMaxZoom());
        coords.setZoom(0.4f);
        assertEquals(0.5f, coords.getZoom());
        coords.setZoom(2.6f);
        assertEquals(2.5f, coords.getZoom());
        coords.setZoom(2.0f);
        assertEquals(2.0f, coords.getZoom());
        assertEquals(7, coords.toRenderScale(3.5f));

        coords.setWorldOffsetX(5f);
        assertEquals(5f, coords.getWorldOffsetX());
        coords.setWorldOffsetY(15f);
        assertEquals(15f, coords.getWorldOffsetY());
        coords.setRenderOffsetX(100);
        assertEquals(100, coords.getRenderOffsetX());
        coords.setRenderOffsetY(200);
        assertEquals(200, coords.getRenderOffsetY());

        final Bounds rbounds = new Bounds(1090, 1370, 2000, 4000);
        final Bounds wbounds = new Bounds(500, 600, 1000, 2000);
        assertEquals(rbounds, coords.toRender(wbounds));

        assertEquals(wbounds.x(), coords.toWorldX(rbounds.x()));
        assertEquals(wbounds.y(), coords.toWorldY(rbounds.y()));
    }
}