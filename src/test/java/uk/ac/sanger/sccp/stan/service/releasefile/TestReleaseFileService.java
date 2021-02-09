package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ReleaseFileService}
 * @author dr6
 */
public class TestReleaseFileService {
    ReleaseRepo mockReleaseRepo;
    SampleRepo mockSampleRepo;
    LabwareRepo mockLabwareRepo;
    MeasurementRepo mockMeasurementRepo;
    Ancestoriser mockAncestoriser;

    ReleaseFileService service;

    private User user;
    private ReleaseDestination destination;
    private ReleaseRecipient recipient;
    private Sample sample, sample1;
    private Labware lw1, lw2;
    private Release release1, release2;

    @BeforeEach
    void setup() {
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockAncestoriser = mock(Ancestoriser.class);

        service = spy(new ReleaseFileService(mockReleaseRepo, mockSampleRepo, mockLabwareRepo,
                mockMeasurementRepo, mockAncestoriser));

        user = EntityFactory.getUser();
        destination = new ReleaseDestination(50, "Venus");
        recipient = new ReleaseRecipient(51, "mekon");
    }

    private void setupLabware() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Tissue tissue = EntityFactory.getTissue();
        BioState bioState = EntityFactory.getBioState();
        sample = new Sample(10, null, tissue, bioState);
        sample1 = new Sample(11, 1, tissue, bioState);
        lw1 = EntityFactory.makeLabware(lt);
        lw1.getFirstSlot().getSamples().addAll(List.of(sample, sample1));
        lw1.getSlots().get(1).getSamples().add(sample);

        lw2 = EntityFactory.makeLabware(lt, sample);
    }

    private void setupReleases() {
        if (lw1==null) {
            setupLabware();
        }
        release1 = release(1, lw1);
        release2 = release(2, lw2);
    }

    private Release release(int id, Labware lw) {
        Release rel = new Release(id, lw, user, destination, recipient, new Timestamp(System.currentTimeMillis()));
        final List<ReleaseDetail> details = rel.getDetails();

        lw.getSlots().forEach(slot -> slot.getSamples()
                .forEach(sample -> details.add(new ReleaseDetail(10*id+details.size(), id, slot.getId(), sample.getId()))));
        return rel;
    }

    @Test
    public void testGetReleaseEntries() {
        assertThat(service.getReleaseEntries(List.of())).isEmpty();

        setupReleases();
        List<Release> releases = List.of(this.release1, release2);
        doReturn(releases).when(service).getReleases(anyCollection());
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample)
        );
        Map<Integer, Sample> sampleMap = Map.of(sample.getId(), sample, sample1.getId(), sample1);
        doReturn(sampleMap).when(service).loadSamples(anyCollection());
        doReturn(entries.subList(0,2).stream()).when(service).toReleaseEntries(this.release1, sampleMap);
        doReturn(entries.subList(2,3).stream()).when(service).toReleaseEntries(release2, sampleMap);
        var ancestry = makeAncestry(lw1, sample1, lw2, sample);
        doReturn(ancestry).when(service).findAncestry(any());
        doNothing().when(service).loadLastSection(any());
        doNothing().when(service).loadOriginalBarcodes(any(), any());
        doNothing().when(service).loadSectionThickness(any(), any());

        List<Integer> releaseIds = List.of(this.release1.getId(), release2.getId());
        assertEquals(entries, service.getReleaseEntries(releaseIds));

        verify(service).getReleases(releaseIds);
        verify(service).loadSamples(releases);
        verify(service).toReleaseEntries(release1, sampleMap);
        verify(service).toReleaseEntries(release2, sampleMap);
        verify(service).loadLastSection(entries);
        verify(service).findAncestry(entries);
        verify(service).loadOriginalBarcodes(entries, ancestry);
        verify(service).loadSectionThickness(entries, ancestry);
    }

    @Test
    public void testFindAncestry() {
        setupReleases();
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample)
        );
        var ancestry = makeAncestry(lw1, sample1, lw2, sample);
        when(mockAncestoriser.findAncestry(any())).thenReturn(ancestry);

        assertSame(ancestry, service.findAncestry(entries));

        verify(mockAncestoriser).findAncestry(Set.of(
                slotSample(lw1, sample), slotSample(lw1, sample1), slotSample(lw2, sample)
        ));
    }

    @Test
    public void testLoadSamples() {
        setupReleases();
        Sample otherSample = new Sample(800, 3, sample.getTissue(), EntityFactory.getBioState());
        Integer otherSampleId = otherSample.getId();
        Set<Integer> otherSampleIds = Set.of(otherSampleId);
        when(mockSampleRepo.getAllByIdIn(otherSampleIds)).thenReturn(List.of(otherSample));
        release1.getDetails().add(new ReleaseDetail(50, release1.getId(), 800, otherSampleId));

        Map<Integer, Sample> result = service.loadSamples(List.of(release1, release2));

        verify(mockSampleRepo).getAllByIdIn(otherSampleIds);

        assertThat(result).hasSize(3);
        Stream.of(sample, sample1, otherSample).forEach(
                sam -> assertEquals(sam, result.get(sam.getId()))
        );
    }

    @Test
    public void testLoadLastSection() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sampleA = EntityFactory.getSample();
        Tissue tissueA = sampleA.getTissue();
        Tissue tissueB = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        Sample sampleB = new Sample(60, null, tissueB, EntityFactory.getBioState());
        Sample[] samples = { sampleA, sampleB, sampleA, sampleA, sampleB, sampleA };
        boolean[] isBlock = { true, true, true, true, true, false };
        Integer[] blockMaxSection = { 6, 6, 2, null, null, null };

        Labware[] labware = IntStream.range(0, samples.length)
                .mapToObj(i -> {
                    Sample sample = samples[i];
                    Labware lw = EntityFactory.makeLabware(lt, sample);
                    if (isBlock[i]) {
                        Slot slot = lw.getFirstSlot();
                        slot.setBlockSampleId(sample.getId());
                        if (blockMaxSection[i]!=null) {
                            slot.setBlockHighestSection(blockMaxSection[i]);
                        }
                    }
                    return lw;
                })
                .toArray(Labware[]::new);

        when(mockSampleRepo.findMaxSectionForTissueId(tissueA.getId())).thenReturn(OptionalInt.of(4));
        when(mockSampleRepo.findMaxSectionForTissueId(tissueB.getId())).thenReturn(OptionalInt.empty());

        List<ReleaseEntry> entries = Arrays.stream(labware)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().get(0)))
                .collect(toList());

        service.loadLastSection(entries);

        Integer[] expectedLastSection = {6, 6, 4, 4, null, null};
        IntStream.range(0, expectedLastSection.length).forEach(i ->
            assertEquals(expectedLastSection[i], entries.get(i).getLastSection(), "element "+i)
        );
    }

    @Test
    public void testToReleaseEntries() {
        setupReleases();
        Map<Integer, Sample> sampleMap = Stream.of(sample, sample1)
                .collect(toMap(Sample::getId, s -> s));
        List<ReleaseEntry> entries = service.toReleaseEntries(release1, sampleMap).collect(toList());
        assertThat(entries).containsOnly(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw1, lw1.getSlots().get(1), sample)
        );
    }

    @Test
    public void testGetReleases() {
        setupReleases();
        List<Integer> releaseIds = List.of(release1.getId(), release2.getId());
        List<Release> releases = List.of(release1, release2);
        when(mockReleaseRepo.getAllByIdIn(releaseIds)).thenReturn(releases);

        assertSame(releases, service.getReleases(releaseIds));
    }

    @Test
    public void testLoadOriginalBarcodes() {
        setupLabware();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample,
                lw1, sample, lw0, sample
        );
        Stream.of(lw0, lw1, lw2).forEach(lw -> when(mockLabwareRepo.getById(lw.getId())).thenReturn(lw));
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample)
        );
        service.loadOriginalBarcodes(entries, ancestry);
        assertEquals(lw0.getBarcode(), entries.get(0).getOriginalBarcode());
        assertEquals(lw1.getBarcode(), entries.get(1).getOriginalBarcode());
        assertEquals(lw0.getBarcode(), entries.get(2).getOriginalBarcode());
    }

    @Test
    public void testLoadSectionThickness() {
        setupLabware();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample,
                lw1, sample, lw0, sample
        );
        List<Measurement> measurements = List.of(
                new Measurement(1, "Thickness", "8", sample.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(2, "Bananas", "X", sample.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(3, "Thickness", "2", sample1.getId(), 10, lw1.getFirstSlot().getId())
        );
        when(mockMeasurementRepo.findAllBySlotIdIn(any())).thenReturn(measurements);
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1)
        );

        service.loadSectionThickness(entries, ancestry);
        assertEquals("8", entries.get(0).getSectionThickness());
        assertEquals("2", entries.get(1).getSectionThickness());
    }

    @Test
    public void testSelectMeasurement() {
        setupLabware();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample,
                lw1, sample, lw0, sample
        );
        Measurement[] meas = {
                new Measurement(1, "Thickness", "10", sample.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(2, "Thickness", "999", sample1.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(3, "Thickness", "20", sample.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(4, "Thickness", "30", sample.getId(), 10, lw2.getFirstSlot().getId()),
        };
        ReleaseEntry entry = new ReleaseEntry(lw2, lw2.getFirstSlot(), sample);
        assertEquals(meas[3], service.selectMeasurement(entry,  measurementMap(meas), ancestry));
        assertEquals(meas[0], service.selectMeasurement(entry, measurementMap(meas[0], meas[1]), ancestry));
        assertNull(service.selectMeasurement(entry, measurementMap(meas[1], meas[1]), ancestry));
    }

    private Map<Integer, List<Measurement>> measurementMap(Measurement... measurements) {
        Map<Integer, List<Measurement>> map = new HashMap<>(measurements.length);
        for (Measurement meas : measurements) {
            map.computeIfAbsent(meas.getSlotId(), ArrayList::new).add(meas);
        }
        return map;
    }

    private Ancestry makeAncestry(Object... args) {
        Ancestry ancestry = new Ancestry();
        for (int i = 0; i < args.length; i += 4) {
            ancestry.put(slotSample(args[i], args[i+1]), Set.of(slotSample(args[i+2], args[i+3])));
        }
        return ancestry;
    }

    private SlotSample slotSample(Object arg1, Object arg2) {
        return new SlotSample(slot(arg1), (Sample) arg2);
    }

    private Slot slot(Object arg) {
        if (arg instanceof Labware) {
            return ((Labware) arg).getFirstSlot();
        }
        return (Slot) arg;
    }
}
