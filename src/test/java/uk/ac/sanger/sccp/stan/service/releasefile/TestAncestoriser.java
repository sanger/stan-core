package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private Sample sampleB1b;

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
        BioState bioState = EntityFactory.getBioState();
        BioState bs2 = new BioState(bioState.getId()+1, "Asleep");
        sample = new Sample(1, null, tissue, bioState);
        sample1 = new Sample(2, 1, tissue, bioState);
        sample2 = new Sample(3, 2, tissue, bioState);
        Tissue tissue2 = EntityFactory.makeTissue(tissue.getDonor(), EntityFactory.getSpatialLocation());
        sampleB = new Sample(4, null, tissue2, bioState);
        sampleB1 = new Sample(5, 1, tissue2, bioState);
        sampleB1b = new Sample(6, 1, tissue2, bs2);
    }

    @Test
    public void testFindAncestry() {
        Labware lw = EntityFactory.makeLabware(lt, sample);
        Labware lw1 = EntityFactory.makeLabware(lt, sample1);
        Labware lw2 = EntityFactory.makeLabware(lt, sample2);
        Labware lw2beta = EntityFactory.makeLabware(lt, sample2);
        Labware lwB = EntityFactory.makeLabware(lt, sampleB);
        Labware lwB1 = EntityFactory.makeLabware(lt, sampleB1b);

        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        lw3.getFirstSlot().getSamples().addAll(List.of(sample, sample1, sample2, sampleB1));

        final List<Action> actions = makeActions(
                lw, lw1, sample1, sample,
                lw, lw2, sample2, sample,
                lw, lw2beta, sample2, sample,
                lw, lw3, sample, sample,
                lw1, lw3, sample1, sample1,
                lw2, lw3, sample2, sample2,
                lw2beta, lw3, sample2, sample2,
                lwB, lw3, sampleB1, sampleB,
                lw3, lwB1, sampleB1, sampleB1,
                lwB1, lwB1, sampleB1b, sampleB1
        );

        when(mockActionRepo.findAllByDestinationIn(anyCollection())).then(invocation -> {
            final Collection<Slot> slots = invocation.getArgument(0);
            return actions.stream()
                    .filter(ac -> slots.contains(ac.getDestination()))
                    .collect(toList());
        });

        var ancestry = ancestoriser.findAncestry(makeSlotSamples(
                lwB1, sampleB1b,
                lw3, sample2,
                lw3, sample
        ));


        Object[][] expectedData = {
                { lwB1, sampleB1b, lwB1, sampleB1 },
                { lwB1, sampleB1, lw3, sampleB1 },
                { lw3, sampleB1, lwB, sampleB },
                { lw3, sample2, lw2, sample2, lw2beta, sample2 },
                { lw3, sample, lw, sample },
                { lw2beta, sample2, lw, sample },
                { lw2, sample2, lw, sample },
                { lw, sample, },
                { lwB, sampleB, },
        };
        Set<SlotSample> keys = new HashSet<>();
        for (Object[] data : expectedData) {
            SlotSample key = slotSample(data[0], (Sample) data[1]);
            Set<SlotSample> values = new HashSet<>(data.length/2-1);
            for (int i = 2; i < data.length; i+=2) {
                values.add(slotSample(data[i], (Sample) data[i+1]));
            }
            assertEquals(values, ancestry.get(key));
            keys.add(key);
        }
        assertThat(ancestry.keySet()).hasSameElementsAs(keys);

        assertThat(ancestry.getRoots(slotSample(lwB1, sampleB1)))
                .containsOnly(slotSample(lwB, sampleB));
        assertThat(ancestry.getRoots(slotSample(lw3, sample2)))
                .containsOnly(slotSample(lw, sample));

        assertThat(ancestry.ancestors(slotSample(lwB1, sampleB1)))
                .containsOnly(slotSample(lwB1, sampleB1), slotSample(lw3, sampleB1),
                        slotSample(lwB, sampleB));
        assertThat(ancestry.ancestors(slotSample(lw3, sample2)))
                .containsOnly(slotSample(lw3, sample2), slotSample(lw2, sample2),
                        slotSample(lw2beta, sample2), slotSample(lw, sample));
    }

    @Test
    public void testFindPosterity() {
        Labware lw = EntityFactory.makeLabware(lt, sample);
        Labware lw1 = EntityFactory.makeLabware(lt, sample1);
        Labware lw2 = EntityFactory.makeLabware(lt, sample2);
        Labware lw2beta = EntityFactory.makeLabware(lt, sample2);
        Labware lwB = EntityFactory.makeLabware(lt, sampleB);
        Labware lwB1 = EntityFactory.makeLabware(lt, sampleB1b);

        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        lw3.getFirstSlot().getSamples().addAll(List.of(sample, sample1, sample2, sampleB1));

        final List<Action> actions = makeActions(
                lw, lw1, sample1, sample,
                lw, lw2, sample2, sample,
                lw, lw2beta, sample2, sample,
                lw, lw3, sample, sample,
                lw1, lw3, sample1, sample1,
                lw2, lw3, sample2, sample2,
                lw2beta, lw3, sample2, sample2,
                lwB, lw3, sampleB1, sampleB,
                lw3, lwB1, sampleB1, sampleB1,
                lwB1, lwB1, sampleB1b, sampleB1
        );

        when(mockActionRepo.findAllBySourceIn(anyCollection())).then(invocation -> {
            final Collection<Slot> slots = invocation.getArgument(0);
            return actions.stream()
                    .filter(ac -> slots.contains(ac.getSource()))
                    .collect(toList());
        });

        var posterity = ancestoriser.findPosterity(makeSlotSamples(
                lw, sample,
                lwB, sampleB
        ));

        Object[][] expectedData = {
                { lw, sample, lw1, sample1, lw2, sample2, lw2beta, sample2, lw3, sample },
                { lw1, sample1, lw3, sample1 },
                { lw2, sample2, lw3, sample2 },
                { lw2beta, sample2, lw3, sample2 },
                { lwB, sampleB, lw3, sampleB1 },
                { lw3, sampleB1, lwB1, sampleB1 },
                { lwB1, sampleB1, lwB1, sampleB1b },
        };
        Set<SlotSample> keys = new HashSet<>();
        for (Object[] data : expectedData) {
            SlotSample key = slotSample(data[0], (Sample) data[1]);
            Set<SlotSample> values = new HashSet<>(data.length/2-1);
            for (int i = 2; i < data.length; i+=2) {
                SlotSample ss = slotSample(data[i], (Sample) data[i+1]);
                values.add(ss);
                keys.add(ss);
            }
            assertEquals(values, posterity.get(key));
            keys.add(key);
        }
        assertThat(posterity.keySet()).hasSameElementsAs(keys);

        assertThat(posterity.getLeafs()).containsExactlyInAnyOrderElementsOf(makeSlotSamples(
                lw3, sample,
                lw3, sample1,
                lw3, sample2,
                lwB1, sampleB1b
        ));

        assertThat(posterity.getLeafs(slotSample(lw, sample))).containsExactlyInAnyOrderElementsOf(makeSlotSamples(
                lw3, sample1, lw3, sample, lw3, sample2
        ));
        assertThat(posterity.getLeafs(slotSample(lwB, sampleB))).containsExactlyInAnyOrderElementsOf(makeSlotSamples(
                lwB1, sampleB1b
        ));

        assertThat(posterity.descendents(slotSample(lw, sample))).containsExactlyInAnyOrderElementsOf(makeSlotSamples(
                lw1, sample1, lw3, sample1, lw3, sample, lw3, sample2, lw2beta, sample2, lw2, sample2, lw, sample
        ));

        assertThat(posterity.descendents(slotSample(lwB, sampleB))).containsExactlyInAnyOrderElementsOf(makeSlotSamples(
                lwB1, sampleB1, lwB, sampleB, lw3, sampleB1, lwB1, sampleB1b
        ));
    }

    private List<Action> makeActions(Object... objects) {
        int actionId = 100;
        assert objects.length%4 == 0;
        List<Action> actions = new ArrayList<>(objects.length/4);
        for (int i = 0; i < objects.length; i += 4) {
            Action action = action(++actionId, objects[i], objects[i+1], (Sample) objects[i+2], (Sample) objects[i+3]);
            actions.add(action);
        }
        return actions;
    }

    private Action action(int id, Object src, Object dest, Sample sample, Sample sourceSample) {
        final int opId = 10;
        return new Action(id, opId, slot(src), slot(dest), sample, sourceSample);
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
