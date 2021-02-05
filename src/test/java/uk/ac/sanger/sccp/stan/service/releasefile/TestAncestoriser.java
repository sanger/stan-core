package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link Ancestoriser}
 * @author dr6
 */
public class TestAncestoriser {
    ActionRepo mockActionRepo;
    Ancestoriser ancestoriser;
    LabwareType lt;
    User user;
    private Sample sample;
    private Sample sample1;
    private Sample sample2;

    private Sample sampleB;
    private Sample sampleB1;

    @BeforeEach
    void setup() {
        mockActionRepo = mock(ActionRepo.class);
        ancestoriser = new Ancestoriser(mockActionRepo);
        lt = EntityFactory.getTubeType();
        user = EntityFactory.getUser();

        setupSamples();
    }

    private void setupSamples() {
        Tissue tissue = EntityFactory.getTissue();
        sample = new Sample(1, null, tissue);
        sample1 = new Sample(2, 1, tissue);
        sample2 = new Sample(3, 2, tissue);
        Tissue tissue2 = EntityFactory.makeTissue(tissue.getDonor(), EntityFactory.getSpatialLocation());
        sampleB = new Sample(4, null, tissue2);
        sampleB1 = new Sample(5, 1, tissue2);
    }

    @Test
    public void testFindAncestry() {
        Labware lw = EntityFactory.makeLabware(lt, sample);
        Labware lw1 = EntityFactory.makeLabware(lt, sample1);
        Labware lw2 = EntityFactory.makeLabware(lt, sample2);
        Labware lwB = EntityFactory.makeLabware(lt, sampleB);
        Labware lwB1 = EntityFactory.makeLabware(lt, sampleB1);

        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        lw3.getFirstSlot().getSamples().addAll(List.of(sample, sample1, sample2, sampleB1));

        final List<Action> actions = makeActions(
                lw, lw1, sample1,
                lw, lw2, sample2,
                lw, lw3, sample,
                lw1, lw3, sample1,
                lw2, lw3, sample2,
                lwB, lw3, sampleB1,
                lw3, lwB1, sampleB1
        );

        when(mockActionRepo.findAllByDestinationIn(anyCollection())).then(invocation -> {
            final Collection<Slot> slots = invocation.getArgument(0);
            return actions.stream()
                    .filter(ac -> slots.contains(ac.getDestination()))
                    .collect(toList());
        });

        Map<SlotSample, SlotSample> result = ancestoriser.findAncestry(makeSlotSamples(
                lwB1, sampleB1,
                lw3, sample2,
                lw3, sample
        ));

        assertThat(result).hasSize(5);
        assertEquals(slotSample(lw3, sampleB1), result.get(slotSample(lwB1, sampleB1)));
        assertEquals(slotSample(lwB, sampleB), result.get(slotSample(lw3, sampleB1)));
        assertEquals(slotSample(lw2, sample2), result.get(slotSample(lw3, sample2)));
        assertEquals(slotSample(lw, sample), result.get(slotSample(lw3, sample)));
        assertEquals(slotSample(lw, sample), result.get(slotSample(lw2, sample2)));
    }

    @Test
    public void testSource() {
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Slot src = lw.getFirstSlot();
        src.getSamples().addAll(List.of(sample, sample1));
        Slot dest = EntityFactory.makeEmptyLabware(lt).getFirstSlot();
        dest.getSamples().addAll(List.of(sample1, sample2));
        assertEquals(slotSample(src, sample1), ancestoriser.source(action(1, src, dest, sample1)));
        assertEquals(slotSample(src, sample), ancestoriser.source(action(1, src, dest, sample2)));
        assertNull(ancestoriser.source(action(1, src, dest, sampleB)));
    }

    @Test
    public void testSlotSample() {
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        SlotSample ss11 = slotSample(lw1, sample1);
        SlotSample ss11again = slotSample(lw1, sample1);
        SlotSample ss21 = slotSample(lw2, sample1);
        SlotSample ss12 = slotSample(lw1, sample2);

        assertEquals(ss11, ss11again);
        assertEquals(ss11.hashCode(), ss11again.hashCode());
        assertEquals(ss11.toString(), ss11again.toString());

        assertNotEquals(ss11, ss21);
        assertNotEquals(ss11, ss12);
        assertNotEquals(ss11.hashCode(), ss21.hashCode());
        assertNotEquals(ss11.hashCode(), ss12.hashCode());
    }

    private List<Action> makeActions(Object... objects) {
        int actionId = 100;
        assert objects.length%3 == 0;
        List<Action> actions = new ArrayList<>(objects.length/3);
        for (int i = 0; i < objects.length; i += 3) {
            Action action = action(++actionId, objects[i], objects[i+1], (Sample) objects[i+2]);
            actions.add(action);
        }
        return actions;
    }

    private Action action(int id, Object src, Object dest, Sample sample) {
        final int opId = 10;
        return new Action(id, opId, slot(src), slot(dest), sample);
    }

    private List<SlotSample> makeSlotSamples(Object... objects) {
        List<SlotSample> slotSamples = new ArrayList<>(objects.length/2);
        for (int i = 0; i < objects.length; i += 2) {
            slotSamples.add(slotSample(objects[i], (Sample) objects[i+1]));
        }
        return slotSamples;
    }

    private SlotSample slotSample(Object obj, Sample sample) {
        return new SlotSample(slot(obj), sample);
    }

    private Slot slot(Object obj) {
        if (obj instanceof Labware) {
            return ((Labware) obj).getFirstSlot();
        }
        return (Slot) obj;
    }

}
