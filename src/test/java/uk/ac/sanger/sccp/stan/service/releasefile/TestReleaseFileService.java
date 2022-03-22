package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp.LW_NOTE_BOND_BARCODE;

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
    OperationTypeRepo mockOpTypeRepo;
    OperationRepo mockOpRepo;
    LabwareNoteRepo mockLwNoteRepo;

    ReagentActionDetailService mockRadService;

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
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockLwNoteRepo = mock(LabwareNoteRepo.class);
        mockRadService = mock(ReagentActionDetailService.class);

        service = spy(new ReleaseFileService(mockAncestoriser, mockSampleRepo, mockLabwareRepo, mockMeasurementRepo,
                mockSnapshotRepo, mockReleaseRepo, mockOpTypeRepo, mockOpRepo, mockLwNoteRepo, mockRadService));

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
        doNothing().when(service).loadMeasurements(any(), any());
        doNothing().when(service).loadLastStain(any());

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
        verify(service).loadMeasurements(entries, ancestry);
        verify(service).loadLastStain(entries);
        verify(service).loadReagentSources(entries);
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

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSelectSourceForCDNA(boolean found) {
        BioState cdna = new BioState(10, "cDNA");
        Sample sam1 = new Sample(90, 1, EntityFactory.getTissue(), cdna);
        BioState bs = (found ? new BioState(11, "Feet") : cdna);
        Sample sam2 = new Sample(sam1.getId()+1, 10, sam1.getTissue(), bs);
        Labware lw1 = EntityFactory.getTube();
        Slot slot1 = lw1.getFirstSlot();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sam2);
        Slot slot2 = lw2.getFirstSlot();
        Ancestry ancestry = mock(Ancestry.class);
        final SlotSample keySlotSample = new SlotSample(slot1, sam1);
        final SlotSample prevSlotSample = new SlotSample(slot2, sam2);
        when(ancestry.ancestors(keySlotSample)).thenReturn(Set.of(keySlotSample, prevSlotSample));

        ReleaseEntry entry = new ReleaseEntry(lw1, slot1, sam1);
        assertSame(found ? prevSlotSample : null, service.selectSourceForCDNA(entry, ancestry));
    }

    @Test
    public void testLoadMeasurements() {
        setupLabware();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample,
                lw1, sample, lw0, sample
        );
        List<Measurement> measurements = List.of(
                new Measurement(1, "Thickness", "8", sample.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(2, "Bananas", "X", sample.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(3, "Thickness", "2", sample1.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(4, "Tissue coverage", "30", sample.getId(), 10, lw0.getFirstSlot().getId())
        );
        when(mockMeasurementRepo.findAllBySlotIdIn(any())).thenReturn(measurements);
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1)
        );

        service.loadMeasurements(entries, ancestry);
        assertEquals("8", entries.get(0).getSectionThickness());
        assertEquals("2", entries.get(1).getSectionThickness());
        assertEquals(30, entries.get(0).getCoverage());
        assertNull(entries.get(1).getCoverage());
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

    @Test
    public void testLoadLastStain_noEntries() {
        service.loadLastStain(List.of());
        verify(service, never()).loadLastOpMap(any(), any());
        verify(service, never()).loadBondBarcodes(any());
    }

    @Test
    public void testLoadLastStain_noStainOps() {
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        when(mockOpTypeRepo.getByName("Stain")).thenReturn(opType);
        doReturn(Map.of()).when(service).loadLastOpMap(any(), any());
        Labware lw = EntityFactory.getTube();
        final ReleaseEntry entry = new ReleaseEntry(lw, lw.getFirstSlot(), EntityFactory.getSample());
        List<ReleaseEntry> entries = List.of(entry);
        service.loadLastStain(entries);
        verify(service).loadLastOpMap(opType, Set.of(lw.getId()));
        verify(service, never()).loadBondBarcodes(any());
        assertNull(entry.getStainType());
        assertNull(entry.getBondBarcode());
    }

    @Test
    public void testLoadLastStain() {
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sample);
        Labware lw3 = EntityFactory.makeLabware(lw1.getLabwareType(), sample);
        List<ReleaseEntry> entries = Stream.of(lw1, lw2, lw3)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), sample))
                .collect(toList());
        OperationType opType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        when(mockOpTypeRepo.getByName("Stain")).thenReturn(opType);
        StainType st1 = new StainType(1, "StainAlpha");
        StainType st2 = new StainType(2, "StainBeta");
        Operation op1 = new Operation(11, opType, null, null, null);
        op1.setStainType(st1);
        Operation op2 = new Operation(12, opType, null, null, null);
        op2.setStainType(st2);
        final Map<Integer, Operation> lwOpMap = Map.of(lw1.getId(), op1, lw2.getId(), op2);
        doReturn(lwOpMap).when(service).loadLastOpMap(any(), any());
        doReturn(Map.of(lw1.getId(), "BONDBC01")).when(service).loadBondBarcodes(any());

        service.loadLastStain(entries);
        verify(service).loadLastOpMap(opType, Set.of(lw1.getId(), lw2.getId(), lw3.getId()));
        verify(service).loadBondBarcodes(lwOpMap);
        String[] expectedStainTypes = {"StainAlpha", "StainBeta", null};
        String[] expectedBondBarcodes = { "BONDBC01", null, null};
        for (int i = 0; i < entries.size(); ++i) {
            ReleaseEntry entry = entries.get(i);
            assertEquals(expectedStainTypes[i], entry.getStainType());
            assertEquals(expectedBondBarcodes[i], entry.getBondBarcode());
        }
    }

    @Test
    public void testLoadLastOpMap() {
        OperationType opType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sample);
        Labware lw3 = EntityFactory.makeLabware(lw1.getLabwareType(), sample);
        List<Operation> ops = List.of(
                makeOp(1, opType, lw1, time(1)),
                makeOp(2, opType, lw1, time(1)),
                makeOp(3, opType, lw2, time(3)),
                makeOp(4, opType, lw2, time(2))
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        Set<Integer> labwareIds = Stream.of(lw1, lw2, lw3).map(Labware::getId).collect(toSet());
        Map<Integer, Operation> opMap = service.loadLastOpMap(opType, labwareIds);
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, labwareIds);

        assertSame(ops.get(1), opMap.get(lw1.getId()));
        assertSame(ops.get(2), opMap.get(lw2.getId()));
        assertNull(opMap.get(lw3.getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "1,1,true",
            "1,-1,true",
            "-1,1,false",
            "-1,-1,false",
            "0,1,true",
            "0,-1,false",
            ",,true",
    })
    public void testOpSupplants(Integer timeDiff, Integer idDiff, boolean expected) {
        Operation newOp, savedOp;
        if (timeDiff==null) {
            savedOp = null;
            newOp = new Operation(1, null, time(0), null, null);
        } else {
            savedOp = new Operation(10, null, time(0), null, null);
            newOp = new Operation(savedOp.getId()+idDiff, null,
                    savedOp.getPerformed().plusDays(timeDiff), null, null);
        }
        assertEquals(expected, ReleaseFileService.opSupplants(newOp, savedOp));
    }

    @Test
    public void testLoadBondBarcodes_noNotes() {
        Operation op1 = new Operation(10, null, null, null, null);
        Operation op2 = new Operation(11, null, null, null, null);
        Map<Integer, Operation> lwOps = Map.of(80, op1, 81, op1, 82, op2);
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).thenReturn(List.of());
        assertThat(service.loadBondBarcodes(lwOps)).isEmpty();
        verify(mockLwNoteRepo).findAllByOperationIdIn(Set.of(op1.getId(), op2.getId()));
    }

    @Test
    public void testLoadBondBarcodes() {
        final int op1id = 10, op2id=11;
        final int lw1id = 80, lw2id=81, lw3id=82;
        final String bondBc1 = "12345678", bondBc2 = "12340000";
        Operation op1 = new Operation(op1id, null, null, null, null);
        Operation op2 = new Operation(op2id, null, null, null, null);
        Map<Integer, Operation> lwOps = Map.of(lw1id, op1, lw2id, op1, lw3id, op2);
        List<LabwareNote> notes = List.of(
                new LabwareNote(1, lw1id, op1id, LW_NOTE_BOND_BARCODE, bondBc1),
                new LabwareNote(2, lw1id, op1id, "Bananas", "yellow"),
                new LabwareNote(3, lw2id, op1id, "Custard", "yellow"),
                new LabwareNote(4, lw2id, op1id, LW_NOTE_BOND_BARCODE, bondBc2),
                new LabwareNote(5, lw3id, op1id, LW_NOTE_BOND_BARCODE, "yellow"),
                new LabwareNote(6, lw1id, op2id, LW_NOTE_BOND_BARCODE, "yellow")
        );
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).thenReturn(notes);
        Map<Integer, String> results = service.loadBondBarcodes(lwOps);
        verify(mockLwNoteRepo).findAllByOperationIdIn(Set.of(op1id, op2id));
        assertEquals(Map.of(lw1id, bondBc1, lw2id, bondBc2), results);
    }

    @Test
    public void testLoadReagentSources() {
        setupLabware();
        Slot slot1 = lw1.getFirstSlot();
        Slot slot2 = lw1.getSlot(new Address(1, 2));
        Slot slot3 = lw2.getFirstSlot();
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, slot1, sample),
                new ReleaseEntry(lw1, slot2, sample),
                new ReleaseEntry(lw2, slot3, sample1)
        );
        final Address A1 = new Address(1, 1);
        final Address B1 = new Address(2, 1);
        final Address B2 = new Address(2,2);
        Map<Integer, List<ReagentActionDetail>> radMap = Map.of(
                slot1.getId(), List.of(
                        new ReagentActionDetail("123", A1, A1, lw1.getId()),
                        new ReagentActionDetail("123", A1, A1, lw1.getId()),
                        new ReagentActionDetail("456", B1, A1, lw1.getId())
                ),
                slot3.getId(), List.of(
                        new ReagentActionDetail("456", B2, A1, lw2.getId())
                )
        );
        when(mockRadService.loadReagentTransfersForSlotIds(any())).thenReturn(radMap);

        service.loadReagentSources(entries);
        verify(mockRadService).loadReagentTransfersForSlotIds(Set.of(slot1.getId(), slot2.getId(), slot3.getId()));

        assertEquals("123 : A1, 456 : B1", entries.get(0).getReagentSource());
        assertNull(entries.get(1).getReagentSource());
        assertEquals("456 : B2", entries.get(2).getReagentSource());
    }

    private LocalDateTime time(int day) {
        return LocalDateTime.of(2021,12,1+day, 12,0,0);
    }

    private Operation makeOp(int id, OperationType opType, Labware lw, LocalDateTime time) {
        Slot slot = lw.getFirstSlot();
        Sample sam = slot.getSamples().get(0);
        Action action = new Action(10*id, id, slot, slot, sam, sam);
        return new Operation(id, opType, time, List.of(action), null);
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
