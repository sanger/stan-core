package uk.ac.sanger.sccp.stan.model;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link Labware}
 * @author dr6
 */
public class TestLabware {
    @Test
    public void testLabwareState() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        assertEquals(Labware.State.empty, lw.getState());
        lw.getFirstSlot().getSamples().add(EntityFactory.getSample());
        assertEquals(Labware.State.active, lw.getState());
        lw.setDiscarded(true);
        assertEquals(Labware.State.discarded, lw.getState());
        lw.setReleased(true);
        assertEquals(Labware.State.released, lw.getState());
        lw.setReleased(false);
        lw.setDestroyed(true);
        assertEquals(Labware.State.destroyed, lw.getState());
        lw.getFirstSlot().getSamples().clear();
        assertEquals(Labware.State.destroyed, lw.getState());
    }
}
