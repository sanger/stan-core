package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
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
    SnapshotRepo mockSnapshotRepo;
    Ancestoriser mockAncestoriser;

    ReleaseFileService service;

    private User user;
    private ReleaseDestination destination;
    private ReleaseRecipient recipient;
    private Sample sample, sample1;
    private Labware lw1, lw2;
    private Release release1, release2;
    private Snapshot snap1, snap2;

    @BeforeEach
    void setup() {
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockSnapshotRepo = mock(SnapshotRepo.class);
        mockAncestoriser = mock(Ancestoriser.class);

        service = spy(new ReleaseFileService(mockReleaseRepo, mockSampleRepo, mockLabwareRepo,
                mockMeasurementRepo, mockSnapshotRepo, mockAncestoriser));

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
        snap1 = EntityFactory.makeSnapshot(lw1);
        snap2 = EntityFactory.makeSnapshot(lw2);
        release1 = release(1, lw1, snap1);
        release2 = release(2, lw2, snap2);
    }

    private Map<Integer, Snapshot> snapMap() {
        return Map.of(snap1.getId(), snap1, snap2.getId(), snap2);
    }

    private Release release(int id, Labware lw, Snapshot snap) {
        return new Release(id, lw, user, destination, recipient, snap.getId(), LocalDateTime.now());
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testGetReleaseFileContent(boolean includeStorageAddresses) {
        assertThat(service.getReleaseFileContent(List.of()).getEntries()).isEmpty();

        setupReleases();
        if (includeStorageAddresses) {
            release1.setLocationBarcode("STO-A1");
            release1.setStorageAddress(new Address(1,1));
            release2.setLocationBarcode("STO-A1");
            release2.setStorageAddress(new Address(1,2));
        }
        final Map<Integer, Snapshot> snapshots = snapMap();
        doReturn(snapshots).when(service).loadSnapshots(any());
        List<Release> releases = List.of(this.release1, release2);
        doReturn(releases).when(service).getReleases(anyCollection());

        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample)
        );
        if (includeStorageAddresses) {
            for (int i = 0; i < entries.size(); ++i) {
                entries.get(i).setStorageAddress(new Address(1, 1+i));
            }
        }
        Map<Integer, Sample> sampleMap = Map.of(sample.getId(), sample, sample1.getId(), sample1);
        doReturn(sampleMap).when(service).loadSamples(anyCollection(), any());
        doReturn(entries.subList(0,2).stream()).when(service).toReleaseEntries(this.release1, sampleMap, snapshots, includeStorageAddresses);
        doReturn(entries.subList(2,3).stream()).when(service).toReleaseEntries(release2, sampleMap, snapshots, includeStorageAddresses);
        var ancestry = makeAncestry(lw1, sample1, lw2, sample);
        doReturn(ancestry).when(service).findAncestry(any());
        ReleaseFileMode mode = ReleaseFileMode.NORMAL;
        doReturn(mode).when(service).checkMode(any());
        doNothing().when(service).loadLastSection(any());
        doNothing().when(service).loadSources(any(), any(), any());
        doNothing().when(service).loadSectionThickness(any(), any());

        List<Integer> releaseIds = List.of(this.release1.getId(), release2.getId());
        ReleaseFileContent rfc = service.getReleaseFileContent(releaseIds);
        assertEquals(entries, rfc.getEntries());
        assertEquals(mode, rfc.getMode());

        verify(service).getReleases(releaseIds);
        verify(service).loadSamples(releases, snapshots);
        verify(service).toReleaseEntries(release1, sampleMap, snapshots, includeStorageAddresses);
        verify(service).toReleaseEntries(release2, sampleMap, snapshots, includeStorageAddresses);
        verify(service).loadLastSection(entries);
        verify(service).findAncestry(entries);
        verify(service).loadSources(entries, ancestry, mode);
        verify(service).loadSectionThickness(entries, ancestry);
    }

    @ParameterizedTest
    @MethodSource("shouldIncludeStorageAddressArgs")
    public void testShouldIncludeStorageAddresses(List<String> locationBarcodes, List<Address> addresses, boolean expected) {
        Iterator<Address> addressIter = addresses.iterator();
        Labware lw = EntityFactory.getTube();
        List<Release> releases = locationBarcodes.stream()
                .map(bc -> new Release(100, lw, user, destination, recipient, 120, null, bc, addressIter.next()))
                .collect(toList());
        assertEquals(expected, service.shouldIncludeStorageAddress(releases));
    }

    static Stream<Arguments> shouldIncludeStorageAddressArgs() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        return Arrays.stream(new Object[][] {
                {List.of(), List.of(), false},
                {List.of("STO-1"), List.of(A1), true},
                {List.of("STO-1", "STO-1"), List.of(A1, A2), true},
                {List.of("STO-1"), Collections.singletonList(null), false},
                {List.of("STO-1", "STO-2"), List.of(A1, A2), false},
                {Collections.singletonList(null), List.of(A1), false},
                {Arrays.asList("STO-1", "STO-1", "STO-1"), Arrays.asList(A1, A2, null), false},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("checkModeArgs")
    public void testCheckMode(Collection<Sample> samples, Object expectedOutcome) {
        if (expectedOutcome instanceof String) {
            String expectedErrorMessage = (String) expectedOutcome;
            assertThat(
                    assertThrows(IllegalArgumentException.class, () -> service.checkMode(samples))
            ).hasMessage(expectedErrorMessage);
        } else {
            assertEquals(expectedOutcome, service.checkMode(samples));
        }
    }

    static Stream<Arguments> checkModeArgs() {
        Tissue tissue = EntityFactory.getTissue();
        String errorMessage = "Cannot create a release file with a mix of " +
                "cDNA and other bio states.";
        BioState[] bss = IntStream.range(1, 4)
                .mapToObj(i -> new BioState(i, i==1 ? "Tissue" : i==2 ? "RNA" : "cDNA"))
                .toArray(BioState[]::new);
        Sample[] samples = IntStream.range(1,5)
                .mapToObj(i -> new Sample(i, i, tissue, bss[Math.min(i, bss.length)-1]))
                .toArray(Sample[]::new);
        return Stream.of(
                Arguments.of(List.of(samples[0], samples[1]), ReleaseFileMode.NORMAL),
                Arguments.of(List.of(samples[2], samples[3]), ReleaseFileMode.CDNA),
                Arguments.of(List.of(samples[0], samples[2]), errorMessage)
        );
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
        var snapshots = snapMap();
        snap1.getElements().add(new SnapshotElement(200, snap1.getId(), 800, otherSampleId));

        Map<Integer, Sample> result = service.loadSamples(List.of(release1, release2), snapshots);

        verify(mockSampleRepo).getAllByIdIn(otherSampleIds);

        assertThat(result).hasSize(3);
        Stream.of(sample, sample1, otherSample).forEach(
                sam -> assertEquals(sam, result.get(sam.getId()))
        );
    }

    @Test
    public void testLoadSnapshots() {
        setupReleases();
        when(mockSnapshotRepo.findAllByIdIn(any())).then(invocation -> {
            Collection<Integer> snapIds = invocation.getArgument(0);
            return Stream.of(snap1, snap2).filter(snap -> snapIds.contains(snap.getId()))
                    .collect(toList());
        });

        assertEquals(snapMap(), service.loadSnapshots(List.of(release1, release2)));

        release2.setSnapshotId(-1);
        assertThat(assertThrows(EntityNotFoundException.class, () -> service.loadSnapshots(List.of(release1, release2))))
                .hasMessage("Labware snapshot missing for release ids: ["+release2.getId()+"]");
    }

    @Test
    public void testLoadLastSection() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sampleA = EntityFactory.getSample();
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

        List<ReleaseEntry> entries = Arrays.stream(labware)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().get(0)))
                .collect(toList());

        service.loadLastSection(entries);

        Integer[] expectedLastSection = {6, 6, 2, null, null, null};
        IntStream.range(0, expectedLastSection.length).forEach(i ->
            assertEquals(expectedLastSection[i], entries.get(i).getLastSection(), "element "+i)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testToReleaseEntries(boolean includeStorageAddresses) {
        setupReleases();
        final Address A2 = new Address(1, 2);
        release1.setLocationBarcode("STO-1");
        release1.setStorageAddress(A2);
        Map<Integer, Sample> sampleMap = Stream.of(sample, sample1)
                .collect(toMap(Sample::getId, s -> s));

        List<ReleaseEntry> entries = service.toReleaseEntries(release1, sampleMap, snapMap(), includeStorageAddresses).collect(toList());
        assertThat(entries).containsOnly(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample, includeStorageAddresses ? A2 : null),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1, includeStorageAddresses ? A2 : null),
                new ReleaseEntry(lw1, lw1.getSlots().get(1), sample, includeStorageAddresses ? A2 : null)
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

    @ParameterizedTest
    @EnumSource(ReleaseFileMode.class)
    public void testLoadSources(ReleaseFileMode mode) {
        setupLabware();
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample)
        );
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample
        );
        doNothing().when(service).loadOriginalBarcodes(any(), any());
        doNothing().when(service).loadSourcesForCDNA(any(), any());
        service.loadSources(entries, ancestry, mode);
        verify(service, times(mode==ReleaseFileMode.NORMAL ? 1 : 0)).loadOriginalBarcodes(entries, ancestry);
        verify(service, times(mode==ReleaseFileMode.CDNA ? 1 : 0)).loadSourcesForCDNA(entries, ancestry);
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
        assertEquals(lw0.getBarcode(), entries.get(0).getSourceBarcode());
        assertEquals(lw1.getBarcode(), entries.get(1).getSourceBarcode());
        assertEquals(lw0.getBarcode(), entries.get(2).getSourceBarcode());
    }

    @Test
    public void testLoadSourcesForCDNA() {
        BioState bs = new BioState(1, "Tissue");
        BioState cdna = new BioState(3, "cDNA");
        Tissue tissue = EntityFactory.getTissue();
        Sample[] samples = {
                new Sample(1, 1, tissue, bs),
                new Sample(2, 1, tissue, bs),
                new Sample(3, 1, tissue, cdna),
                new Sample(4, 1, tissue, cdna),
        };
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = Arrays.stream(samples)
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .toArray(Labware[]::new);
        var ancestry = makeAncestry(
                labware[1], samples[1], labware[0], samples[0],
                labware[2], samples[2], labware[1], samples[1],
                labware[3], samples[3], labware[2], samples[2]
        );
        Arrays.stream(labware).forEach(lw -> when(mockLabwareRepo.getById(lw.getId())).thenReturn(lw));
        List<ReleaseEntry> entries = IntStream.of(2,3)
                .mapToObj(i -> new ReleaseEntry(labware[i], labware[i].getFirstSlot(), samples[i]))
                .collect(toList());
        service.loadSourcesForCDNA(entries, ancestry);
        final Address A1 = new Address(1, 1);
        for (var entry : entries) {
            assertEquals(labware[1].getBarcode(), entry.getSourceBarcode());
            assertEquals(A1, entry.getSourceAddress());
        }
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
