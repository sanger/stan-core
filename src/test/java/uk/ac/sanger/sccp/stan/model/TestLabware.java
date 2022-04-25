package uk.ac.sanger.sccp.stan.model;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.Labware.State;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link Labware}
 * @author dr6
 */
public class TestLabware {
    @Test
    public void testLabwareState() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        assertEquals(State.empty, lw.getState());
        lw.getFirstSlot().getSamples().add(EntityFactory.getSample());
        assertEquals(State.active, lw.getState());
        lw.setUsed(true);
        assertEquals(State.used, lw.getState());
        lw.setDiscarded(true);
        assertEquals(State.discarded, lw.getState());
        lw.setReleased(true);
        assertEquals(State.released, lw.getState());
        lw.setReleased(false);
        lw.setDestroyed(true);
        assertEquals(State.destroyed, lw.getState());
        lw.getFirstSlot().getSamples().clear();
        assertEquals(State.destroyed, lw.getState());
    }
}
