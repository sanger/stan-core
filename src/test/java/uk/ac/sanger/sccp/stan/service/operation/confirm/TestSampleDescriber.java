package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link SampleDescriber} */
class TestSampleDescriber {

    static <E extends HasIntId> Map<Integer, E> makeIdMap(E[] items) {
        return Arrays.stream(items).collect(inMap(E::getId));
    }

    @Test
    void testDescribe() {
        SampleDescriber sd = spy(new SampleDescriber(null, null, null));
        final String desc = "sample 10 description";
        doReturn(desc).when(sd).makeDescription(10);
        assertEquals(desc, sd.describe(10));
        assertEquals(desc, sd.describe(10));
        verify(sd).makeDescription(10);
    }

    @Test
    void testDescribeSampleFields() {
        Sample[] samples = EntityFactory.makeSamples(2);
        SampleDescriber sd = new SampleDescriber(null, makeIdMap(samples), null);
        samples[0].setSection("15");
        String xn = samples[0].getTissue().getExternalName();
        assertEquals("section 15 of "+xn, sd.describeSampleFields(samples[0]));
        assertEquals(xn, sd.describeSampleFields(samples[1]));
    }

    @Test
    void testDescribeSampleLocations() {
        Sample[] samples = EntityFactory.makeSamples(3);
        Labware tube1 = EntityFactory.makeTube(samples[0]);
        Labware tube2 = EntityFactory.makeTube(samples[1]);
        Labware plate = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), samples[1], samples[1]);

        Map<Integer, Sample> sampleMap = makeIdMap(samples);
        Map<Integer, Labware> lwMap = makeIdMap(new Labware[]{tube1, tube2, plate});
        Map<Integer, Set<Slot>> sampleSlots = Map.of(
                samples[0].getId(), Set.of(tube1.getFirstSlot()),
                samples[1].getId(), new LinkedHashSet<>(List.of(tube2.getFirstSlot(), plate.getFirstSlot(), plate.getSlots().getLast()))
        );
        SampleDescriber sd = new SampleDescriber(lwMap, sampleMap, sampleSlots);
        assertEquals(tube1.getBarcode(), sd.describeSampleLocations(samples[0].getId()));
        assertEquals(String.format("%s, %s (A1,A2)", tube2.getBarcode(), plate.getBarcode()), sd.describeSampleLocations(samples[1].getId()));
        assertNull(sd.describeSampleLocations(samples[2].getId()));
    }

    @Test
    void testMakeDescription() {
        Sample[] samples = EntityFactory.makeSamples(3);
        Integer[] sampleIds = Arrays.stream(samples).map(Sample::getId).toArray(Integer[]::new);
        SampleDescriber sd = spy(new SampleDescriber(null, makeIdMap(samples), null));
        String[] fieldDescs = {"EXT1", "section 4 of EXT2", "EXT3"};
        Zip.of(Arrays.stream(samples), Arrays.stream(fieldDescs)).forEach((sam, desc) -> doReturn(desc).when(sd).describeSampleFields(sam));
        String[] locDescs = {"STAN-1", "STAN-2 (A1), STAN-3", null};
        Zip.of(Arrays.stream(sampleIds), Arrays.stream(locDescs)).forEach((id, desc) -> doReturn(desc).when(sd).describeSampleLocations(id));

        String[] expected = {String.format("id %s (EXT1) from STAN-1", sampleIds[0]),
                String.format("id %s (section 4 of EXT2) from STAN-2 (A1), STAN-3", sampleIds[1]),
                String.format("id %s (EXT3)", sampleIds[2]),
        };
        Zip.of(Arrays.stream(sampleIds), Arrays.stream(expected))
                .forEach((id, desc) -> assertEquals(desc, sd.makeDescription(id)));
        Integer lastId = sampleIds[2]+1;
        assertEquals("id "+lastId, sd.makeDescription(lastId));
    }
}