package uk.ac.sanger.sccp.stan.service.releasefile;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagDetail;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.stan.service.releasefile.ReleaseFileService.StorageDetail;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

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
    StainTypeRepo mockStainTypeRepo;
    SamplePositionRepo mockSamplePositionRepo;
    OperationCommentRepo mockOpComRepo;
    LabwareProbeRepo mockLwProbeRepo;
    RoiRepo mockRoiRepo;
    SolutionRepo mockSolutionRepo;
    OperationSolutionRepo mockOpSolRepo;
    ResultOpRepo mockRoRepo;

    ReagentActionDetailService mockRadService;
    FlagLookupService mockFlagLookupService;

    ReleaseFileService service;

    private User user;
    private ReleaseDestination destination;
    private ReleaseRecipient recipient;
    private Sample sample, sample1, sample2, sample3;
    private Labware lw1, lw2, lwTOSlide,lw96WellPlate;
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
        mockStainTypeRepo = mock(StainTypeRepo.class);
        mockSamplePositionRepo = mock(SamplePositionRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockLwProbeRepo = mock(LabwareProbeRepo.class);
        mockRoiRepo = mock(RoiRepo.class);
        mockSolutionRepo = mock(SolutionRepo.class);
        mockOpSolRepo = mock(OperationSolutionRepo.class);
        mockRoRepo = mock(ResultOpRepo.class);
        mockFlagLookupService = mock(FlagLookupService.class);

        service = spy(new ReleaseFileService(mockAncestoriser, mockSampleRepo, mockLabwareRepo, mockMeasurementRepo,
                mockSnapshotRepo, mockReleaseRepo, mockOpTypeRepo, mockOpRepo, mockLwNoteRepo, mockStainTypeRepo,
                mockSamplePositionRepo, mockOpComRepo, mockLwProbeRepo, mockRoiRepo, mockRadService, mockSolutionRepo,
                mockOpSolRepo, mockRoRepo, mockFlagLookupService));

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
        lw1.getSlots().get(1).addSample(sample);

        lw2 = EntityFactory.makeLabware(lt, sample);

        LabwareType ltTOSlide = new LabwareType(1, "Visium TO", 4, 2, null, false);
        sample2 = new Sample(12, 1, tissue, bioState);
        lwTOSlide = EntityFactory.makeLabware(ltTOSlide);
        lwTOSlide.getFirstSlot().addSample(sample2);

        LabwareType lt96WellPlate = new LabwareType(2, "96 well plate", 12, 8, null, false);
        lt96WellPlate.setName("96 Well Plate");
        sample3 = new Sample(13, 1, tissue, bioState);
        lw96WellPlate = EntityFactory.makeLabware(lt96WellPlate);
        lw96WellPlate.getFirstSlot().addSample(sample3);
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
    @CsvSource({"NONE,false","NAME,false","BARCODE,false","NAME,true"})
    public void testGetReleaseFileContent(StorageDetail detail, boolean includeVisium) {
        assertThat(service.getReleaseFileContent(List.of(), Set.of()).getEntries()).isEmpty();

        setupReleases();
        if (detail != StorageDetail.NONE) {
            release1.setLocationBarcode("STO-A1");
            release1.setLocationName("Box 1");
            release1.setStorageAddress("A1");
            release2.setLocationBarcode(detail==StorageDetail.BARCODE ? "STO-A2" : "STO-A1");
            release2.setLocationName("Box 1");
            release2.setStorageAddress("42");
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
        if (detail!=StorageDetail.NONE) {
            for (int i = 0; i < entries.size(); ++i) {
                entries.get(i).setStorageAddress(i==0 ? "A1" : "42");
                if (detail==StorageDetail.BARCODE) {
                    entries.get(i).setLocationName(i==0 ? "Box 1 STO-A1" : "Box 1 STO-A2");
                } else {
                    entries.get(i).setLocationName("Box 1");
                }
            }
        }
        Set<Integer> slotIds = entries.stream()
                .map(re -> re.getSlot().getId())
                .collect(toSet());
        Map<Integer, Sample> sampleMap = Map.of(sample.getId(), sample, sample1.getId(), sample1);
        doReturn(sampleMap).when(service).loadSamples(anyCollection(), any());
        doReturn(entries.subList(0,2).stream()).when(service).toReleaseEntries(this.release1, sampleMap, snapshots, detail);
        doReturn(entries.subList(2,3).stream()).when(service).toReleaseEntries(release2, sampleMap, snapshots, detail);
        var ancestry = makeAncestry(lw1, sample1, lw2, sample);
        doReturn(ancestry).when(service).findAncestry(any());
        ReleaseFileMode mode = ReleaseFileMode.NORMAL;
        doReturn(mode).when(service).checkMode(any());
        doNothing().when(service).loadLastSection(any());
        doNothing().when(service).loadSources(any(), any(), any());
        doNothing().when(service).loadMeasurements(any(), any());
        doNothing().when(service).loadStains(any(), any());
        doNothing().when(service).loadSamplePositions(any());
        doNothing().when(service).loadSectionComments(any());
        doNothing().when(service).loadVisiumBarcodes(any(), any());
        doNothing().when(service).loadXeniumFields(any(), any());
        doNothing().when(service).loadSolutions(any());
        doNothing().when(service).loadFlags(any());

        Set<ReleaseFileOption> options = EnumSet.allOf(ReleaseFileOption.class);
        if (!includeVisium) {
            options.remove(ReleaseFileOption.Visium);
        }

        List<Integer> releaseIds = List.of(this.release1.getId(), release2.getId());
        ReleaseFileContent rfc = service.getReleaseFileContent(releaseIds, options);
        assertEquals(entries, rfc.getEntries());
        assertEquals(mode, rfc.getMode());
        assertSame(options, rfc.getOptions());

        verify(service).getReleases(releaseIds);
        verify(service).loadSamples(releases, snapshots);
        verify(service).toReleaseEntries(release1, sampleMap, snapshots, detail);
        verify(service).toReleaseEntries(release2, sampleMap, snapshots, detail);
        verify(service).loadLastSection(entries);
        verify(service).findAncestry(entries);
        verify(service).loadSources(entries, ancestry, mode);
        verify(service).loadMeasurements(entries, ancestry);
        verify(service).loadStains(entries, ancestry);
        verify(service).loadReagentSources(entries);
        verify(service).loadSamplePositions(entries);
        verify(service).loadSectionComments(entries);
        verify(service, times(includeVisium ? 1 : 0)).loadVisiumBarcodes(entries, ancestry);
        verify(service).loadXeniumFields(entries, slotIds);
        verify(service).loadSolutions(entries);
        verify(service).loadFlags(entries);
    }

    @ParameterizedTest
    @CsvSource(delimiter=';', value={
            "null,null;null,null;null,null;NONE",
            "STO-1,null;Box 1,null;A1,null;NAME",
            "STO-1,STO-1,STO-2,null;Box 1,Box 1,Box 2,null;A1,A2,A3,null;NAME",
            "STO-1,STO-1,STO-2,null;Box 1,Box 1,null,null;A1,A2,A3,null;BARCODE",
            "STO-1,STO-2,null;Box 1,Box 1,null;A1,A2,null;BARCODE",
    })
    public void testStorageDetail(String locationBarcodesJoined,
                                  String locationNamesJoined,
                                  String addressesJoined,
                                  StorageDetail expectedDetail) {
        String[] barcodes = splitArg(locationBarcodesJoined);
        String[] addresses = splitArg(addressesJoined);
        String[] names = splitArg(locationNamesJoined);
        Labware lw = EntityFactory.getTube();
        List<Release> releases = IntStream.range(0, barcodes.length)
                .mapToObj(i -> new Release(100, lw, user, destination, recipient, 120, null, barcodes[i], names[i], addresses[i], null))
                .toList();
        assertEquals(expectedDetail, service.storageDetail(releases));
    }

    static String[] splitArg(String arg) {
        String[] values = arg.split(",");
        for (int i = 0; i < values.length; ++i) {
            values[i] = values[i].trim();
            if (values[i].equalsIgnoreCase("null")) {
                values[i] = null;
            }
        }
        return values;
    }

    @ParameterizedTest
    @MethodSource("checkModeArgs")
    public void testCheckMode(Collection<Sample> samples, Object expectedOutcome) {
        if (expectedOutcome instanceof String expectedErrorMessage) {
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
        when(mockSampleRepo.getMapByIdIn(otherSampleIds)).thenReturn(Map.of(otherSample.getId(), otherSample));
        var snapshots = snapMap();
        snap1.getElements().add(new SnapshotElement(200, snap1.getId(), 800, otherSampleId));

        Map<Integer, Sample> result = service.loadSamples(List.of(release1, release2), snapshots);

        verify(mockSampleRepo).getMapByIdIn(otherSampleIds);

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
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .collect(toList());

        service.loadLastSection(entries);

        Integer[] expectedLastSection = {6, 6, 2, null, null, null};
        IntStream.range(0, expectedLastSection.length).forEach(i ->
            assertEquals(expectedLastSection[i], entries.get(i).getLastSection(), "element "+i)
        );
    }

    @ParameterizedTest
    @EnumSource(StorageDetail.class)
    public void testToReleaseEntries(StorageDetail detail) {
        setupReleases();
        release1.setLocationBarcode("STO-1");
        release1.setLocationName("locname");
        release1.setStorageAddress("42");
        Map<Integer, Sample> sampleMap = Stream.of(sample, sample1)
                .collect(toMap(Sample::getId, s -> s));

        String expectedAddress = (detail==StorageDetail.NONE ? null : "42");
        String expectedLocationName = switch (detail) {
            case NONE -> null;
            case NAME -> "locname";
            case BARCODE -> "locname STO-1";
        };

        List<ReleaseEntry> entries = service.toReleaseEntries(release1, sampleMap, snapMap(), detail).collect(toList());
        assertThat(entries).containsOnly(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample, expectedAddress, expectedLocationName),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1, expectedAddress, expectedLocationName),
                new ReleaseEntry(lw1, lw1.getSlots().get(1), sample, expectedAddress, expectedLocationName)
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
    public void testLabwareIdToOp() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample, sample);
        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        op1.setPerformed(LocalDateTime.of(2022,4,8,14,50));
        op2.setPerformed(LocalDateTime.of(2022,4,8,14,51));
        final Address A2 = new Address(1,2);
        op1.setActions(List.of(
                new Action(11, 1, lw1.getFirstSlot(), lw1.getFirstSlot(), sample, sample),
                new Action(12, 1, lw2.getSlot(A2), lw2.getSlot(A2), sample, sample)
        ));

        op2.setActions(List.of(
                new Action(21, 2, lw1.getFirstSlot(), lw1.getFirstSlot(),  null, null),
                new Action(22, 2, lw1.getSlot(A2), lw1.getSlot(A2), sample, sample)
        ));

        Map<Integer, Operation> map = service.labwareIdToOp(List.of(op1, op2));
        assertThat(map).hasSize(2);
        assertSame(op2, map.get(lw1.getId()));
        assertSame(op1, map.get(lw2.getId()));
    }

    @Test
    public void testFindEntryOps() {
        Operation op1 = new Operation();
        op1.setId(1);
        op1.setPerformed(LocalDateTime.of(2022,4,8,15,0));
        Operation op2 = new Operation();
        op2.setId(2);
        op2.setPerformed(op1.getPerformed().plusDays(1));

        final LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        Map<Integer, Operation> lwIdOps = Map.of(lw1.getId(), op1, lw2.getId(), op2);
        Sample sample = EntityFactory.getSample();
        ReleaseEntry re1 = new ReleaseEntry(lw1, lw1.getFirstSlot(), sample);
        ReleaseEntry re2 = new ReleaseEntry(lw2, lw2.getFirstSlot(), sample);
        ReleaseEntry re3 = new ReleaseEntry(lw3, lw3.getFirstSlot(), sample);
        Ancestry ancestry = mock(Ancestry.class);
        when(ancestry.ancestors(any())).then(invocation -> {
            SlotSample ss = invocation.getArgument(0);
            return Set.of(ss);
        });

        final List<ReleaseEntry> entries = List.of(re1, re2, re3);
        var map = service.findEntryOps(entries, lwIdOps, ancestry);

        for (ReleaseEntry entry : entries) {
            verify(service).selectOp(entry, lwIdOps, ancestry);
        }
        assertThat(map).hasSize(2);
        assertSame(op1, map.get(re1));
        assertSame(op2, map.get(re2));
        assertNull(map.get(re3));
    }

    @Test
    public void testSelectOp() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware[] lw = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        Slot[] slots = Arrays.stream(lw).map(Labware::getFirstSlot).toArray(Slot[]::new);
        SlotSample[] sss = Arrays.stream(slots).map(slot -> new SlotSample(slot, sample)).toArray(SlotSample[]::new);
        Ancestry ancestry = new Ancestry();
        ancestry.put(sss[2], Set.of(sss[1]));
        ancestry.put(sss[1], Set.of(sss[0]));

        assertNull(service.selectOp(new ReleaseEntry(lw[0], slots[0], sample), Map.of(), ancestry));
        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        Map<Integer, Operation> lwOps = Map.of(lw[0].getId(), op1, lw[2].getId(), op2);

        assertSame(op2, service.selectOp(new ReleaseEntry(lw[2], slots[2], sample), lwOps, ancestry));
        assertSame(op1, service.selectOp(new ReleaseEntry(lw[1], slots[1], sample), lwOps, ancestry));
        assertSame(op1, service.selectOp(new ReleaseEntry(lw[0], slots[0], sample), lwOps, ancestry));
    }

    @Test
    public void testLoadSectionDate() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);
        Labware lw3 = EntityFactory.makeLabware(lt, sample);
        Labware lw4 = EntityFactory.makeLabware(lt, sample);
        Ancestry ancestry = makeAncestry(lw2, sample, lw1, sample, lw1, sample, lw1, sample, lw3, sample, lw3, sample);
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);

        List<ReleaseEntry> entries = List.of(new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw3, lw3.getFirstSlot(), sample),
                new ReleaseEntry(lw4, lw4.getFirstSlot(), sample));

        Operation op = new Operation();
        op.setPerformed(LocalDateTime.of(2022,1,2, 12, 0));
        final List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);

        final Map<Integer, Operation> lwSectionOpMap = Map.of(lw2.getId(), op);
        doReturn(lwSectionOpMap).when(service).labwareIdToOp(any());
        doReturn(Map.of(entries.getFirst(), op)).when(service).findEntryOps(any(), any(), any());

        List<Measurement> measurements = List.of(new Measurement(10, "Date sectioned", "2024-01-01", sample.getId(), 100, lw4.getFirstSlot().getId()));
        when(mockMeasurementRepo.findAllBySlotIdInAndName(any(), any())).thenReturn(measurements);

        Map<SlotIdSampleId, LocalDate> slotSampleDates = Map.of(
                new SlotIdSampleId(lw4.getFirstSlot(), sample), LocalDate.of(2024,1,2)
        );
        doReturn(slotSampleDates).when(service).findSlotSampleDates(any());
        LocalDate lw4Date = LocalDate.of(2024,1,13);
        doReturn(null).when(service).findEntrySectionDate(any(), any(), any());
        doReturn(lw4Date).when(service).findEntrySectionDate(entries.get(2), slotSampleDates, ancestry);

        service.loadSectionDate(entries, ancestry);

        assertEquals(op.getPerformed().toLocalDate(), entries.get(0).getSectionDate());
        assertNull(entries.get(1).getSectionDate());
        assertEquals(lw4Date, entries.get(2).getSectionDate());

        verify(mockOpTypeRepo).getByName("Section");
        Set<Integer> slotIds = Stream.of(lw1, lw2, lw3).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(service).labwareIdToOp(ops);
        verify(service).findEntryOps(entries, lwSectionOpMap, ancestry);
        verify(mockMeasurementRepo).findAllBySlotIdInAndName(slotIds, "Date sectioned");
        verify(service).findSlotSampleDates(measurements);
        verify(service).findEntrySectionDate(entries.get(2), slotSampleDates, ancestry);
    }

    @Test
    public void testFindSlotSampleDates() {
        List<Measurement> measurements = List.of(
                new Measurement(1, "Date sectioned", "2024-01-13", 10, 1, 11),
                new Measurement(2, "Date sectioned", "2024-01-14", 10, 1, 12),
                new Measurement(3, "Date sectioned", "2024-01-15", 11, 1, 11),
                new Measurement(4, "Date sectioned", "2024-01-16", 11, 1, 11)
        );
        Map<SlotIdSampleId, LocalDate> map = service.findSlotSampleDates(measurements);
        assertThat(map).hasSize(3);
        assertEquals(LocalDate.of(2024,1,13), map.get(new SlotIdSampleId(11, 10)));
        assertEquals(LocalDate.of(2024,1,14), map.get(new SlotIdSampleId(12, 10)));
        assertEquals(LocalDate.of(2024,1,16), map.get(new SlotIdSampleId(11, 11)));
    }

    @ParameterizedTest
    @MethodSource("findEntrySectionDateArgs")
    public void testFindEntrySectionDate(ReleaseEntry entry, Map<SlotIdSampleId, LocalDate> slotSampleDates,
                                         Ancestry ancestry, LocalDate expectedDate) {
        assertEquals(expectedDate, service.findEntrySectionDate(entry, slotSampleDates, ancestry));
    }

    static Stream<Arguments> findEntrySectionDateArgs() {
        Sample[] samples = EntityFactory.makeSamples(4);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = Arrays.stream(samples)
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .toArray(Labware[]::new);
        Slot[] slots = Arrays.stream(labware)
                .map(Labware::getFirstSlot)
                .toArray(Slot[]::new);
        Ancestry ancestry = makeAncestry(labware[0], samples[0], labware[1], samples[1]);
        LocalDate[] dates = IntStream.of(13,14)
                .mapToObj(d -> LocalDate.of(2024,1,d))
                .toArray(LocalDate[]::new);
        Map<SlotIdSampleId, LocalDate> dateMap = Map.of(
                new SlotIdSampleId(slots[1], samples[1]), dates[0],
                new SlotIdSampleId(slots[2], samples[2]), dates[1]
        );
        ReleaseEntry[] entries = IntStream.range(0, labware.length)
                .mapToObj(i -> new ReleaseEntry(labware[i], slots[i], samples[i]))
                .toArray(ReleaseEntry[]::new);
        LocalDate[] expected = { dates[0], dates[0], dates[1], null };
        return IntStream.range(0, expected.length)
                .mapToObj(i -> Arguments.of(entries[i], dateMap, ancestry, expected[i]));
    }

    @Test
    public void testLoadStains_noStainOps() {
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Labware lw = EntityFactory.getTube();
        Sample sample = EntityFactory.getSample();
        Ancestry ancestry = makeAncestry(lw, sample, lw, sample);
        ReleaseEntry entry = new ReleaseEntry(lw, lw.getFirstSlot(), sample);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(List.of());
        doNothing().when(service).loadStainQcComments(any(), any(), any());
        service.loadStains(List.of(entry), ancestry);
        verify(mockOpTypeRepo).getByName("Stain");
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, Set.of(lw.getFirstSlot().getId()));
        verify(service, never()).labwareIdToOp(any());
        verify(service, never()).findEntryOps(any(), any(), any());
        verify(service, never()).loadStainQcComments(any(), any(), any());
        verifyNoInteractions(mockStainTypeRepo);
        verifyNoInteractions(mockLwNoteRepo);
    }

    @Test
    public void testLoadStains_noEntryStainOps() {
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Labware lw = EntityFactory.getTube();
        Sample sample = EntityFactory.getSample();
        Ancestry ancestry = makeAncestry(lw, sample, lw, sample);
        ReleaseEntry entry = new ReleaseEntry(lw, lw.getFirstSlot(), sample);
        Operation op = new Operation();
        final List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        Map<Integer, Operation> labwareStainOp = Map.of(lw.getId(), op);
        doReturn(labwareStainOp).when(service).labwareIdToOp(any());
        doReturn(Map.of()).when(service).findEntryOps(any(), any(), any());
        doNothing().when(service).loadStainQcComments(any(), any(), any());
        final List<ReleaseEntry> entries = List.of(entry);
        service.loadStains(entries, ancestry);
        verify(mockOpTypeRepo).getByName("Stain");
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, Set.of(lw.getFirstSlot().getId()));
        verify(service).labwareIdToOp(ops);
        verify(service).findEntryOps(entries, labwareStainOp, ancestry);
        verify(service, never()).loadStainQcComments(any(), any(), any());

        verifyNoInteractions(mockStainTypeRepo);
        verifyNoInteractions(mockLwNoteRepo);
    }

    @Test
    public void testLoadStains() {
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        Operation[] ops = IntStream.range(0,2)
                .mapToObj(i -> new Operation(100+i, opType, null, null, null))
                .toArray(Operation[]::new);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(Arrays.asList(ops));
        Map<Integer, Operation> labwareStainOp = Map.of(labware[0].getId(), ops[0], labware[1].getId(), ops[1]);
        doReturn(labwareStainOp).when(service).labwareIdToOp(any());

        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(labware[0], labware[0].getFirstSlot(), sample),
                new ReleaseEntry(labware[1], labware[1].getFirstSlot(), sample),
                new ReleaseEntry(labware[2], labware[2].getFirstSlot(), sample)
        );

        doNothing().when(service).loadStainQcComments(any(), any(), any());

        Ancestry ancestry = makeAncestry(labware[1], sample, labware[0], sample);

        Map<ReleaseEntry, Operation> entryStainOp = Map.of(entries.get(0), ops[0], entries.get(1), ops[1]);
        doReturn(entryStainOp).when(service).findEntryOps(entries, labwareStainOp, ancestry);

        StainType st1 = new StainType(1, "Coffee");
        StainType st2 = new StainType(2, "Tea");

        Map<Integer, List<StainType>> stainTypeMap = Map.of(ops[0].getId(), List.of(st1, st2),
                ops[1].getId(), List.of(st1));

        when(mockStainTypeRepo.loadOperationStainTypes(any())).thenReturn(stainTypeMap);

        List<LabwareNote> notes = List.of(
                new LabwareNote(100, labware[0].getId(), ops[0].getId(), LW_NOTE_BOND_BARCODE, "XYZ123"),
                new LabwareNote(101, labware[0].getId(), ops[0].getId(), LW_NOTE_PLEX_RNASCOPE, "15"),
                new LabwareNote(102, labware[0].getId(), ops[0].getId(), LW_NOTE_PLEX_IHC, "16"),
                new LabwareNote(103, labware[1].getId(), ops[1].getId(), LW_NOTE_BOND_BARCODE, "ABC123"),
                new LabwareNote(104, labware[1].getId(), ops[1].getId(), LW_NOTE_PLEX_RNASCOPE, "Alpha"),
                new LabwareNote(105, labware[1].getId(), ops[1].getId(), LW_NOTE_PLEX_IHC, "Beta")
        );
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).thenReturn(notes);

        service.loadStains(entries, ancestry);

        String[] expectedBondBarcodes = { "XYZ123", "ABC123", null };
        Integer[] expectedRnaPlex = { 15, null, null };
        Integer[] expectedIhcPlex = { 16, null, null };

        for (int i = 0; i < expectedBondBarcodes.length; ++i) {
            final ReleaseEntry entry = entries.get(i);
            assertEquals(expectedBondBarcodes[i], entry.getBondBarcode());
            assertEquals(expectedRnaPlex[i], entry.getRnascopePlex());
            assertEquals(expectedIhcPlex[i], entry.getIhcPlex());
        }

        verify(mockOpTypeRepo).getByName("Stain");
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, Set.of(labware[1].getFirstSlot().getId()));
        verify(service).labwareIdToOp(Arrays.asList(ops));
        verify(service).findEntryOps(entries, labwareStainOp, ancestry);
        final Set<Integer> opIds = Set.of(ops[0].getId(), ops[1].getId());
        verify(mockStainTypeRepo).loadOperationStainTypes(opIds);
        verify(mockLwNoteRepo).findAllByOperationIdIn(opIds);
        verify(service).loadStainQcComments(entries, ancestry, Set.of(100,101));
    }

    @Test
    public void testLoadStainQcComments() {
        setupLabware();
        Labware lw3 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Ancestry ancestry = makeAncestry(lw3, sample, lw2, sample, lw2, sample, lw1, sample);

        Set<Integer> stainOpIds = Set.of(3,4);

        Set<Integer> resultOpIds = Set.of(13,14);

        List<ResultOp> rops = List.of(new ResultOp(), new ResultOp());
        rops.get(0).setOperationId(13);
        rops.get(1).setOperationId(14);
        when(mockRoRepo.findAllByRefersToOpIdIn(any())).thenReturn(rops);

        final List<OperationComment> opcoms = makeOpComs(lw3);
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(opcoms);

        List<ReleaseEntry> entries = List.of(new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw3, lw3.getFirstSlot(), sample));

        service.loadStainQcComments(entries, ancestry, stainOpIds);

        verify(mockRoRepo).findAllByRefersToOpIdIn(stainOpIds);
        verify(mockOpComRepo).findAllByOperationIdIn(resultOpIds);

        assertEquals("Banana.", entries.get(0).getStainQcComment());
        assertEquals("Banana. Custard.", entries.get(1).getStainQcComment());
    }

    @NotNull
    private List<OperationComment> makeOpComs(Labware lw3) {
        Comment[] comments = {
                new Comment(1, "Banana.", "cat"),
                new Comment(2, "Custard.", "cat")
        };

        final Integer sampleId = sample.getId();
        return List.of(
                new OperationComment(1, comments[0], 13, sampleId, lw1.getFirstSlot().getId(), null),
                new OperationComment(2, comments[0], 14, sampleId, lw3.getFirstSlot().getId(), null),
                new OperationComment(3, comments[1], 14, sampleId, lw3.getFirstSlot().getId(), null)
        );
    }

    @Test
    public void testLoadMeasurements() {
        setupLabware();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Labware lw96WellPlateSource = EntityFactory.makeLabware(new LabwareType(6, "96 Well Plate", 12, 8, null, false), sample3);

        // lw0 begat lw1 which begat lw2
        var ancestry = makeAncestry(
                lw2, sample, lw1, sample,
                lw1, sample, lw0, sample,
                lw96WellPlate,sample3,lw96WellPlateSource,sample3
        );

        List<Measurement> measurements = List.of(
                new Measurement(1, "Thickness", "8", sample.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(2, "Bananas", "X", sample.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(3, "Thickness", "2", sample1.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(4, "Tissue coverage", "30", sample.getId(), 10, lw0.getFirstSlot().getId()),
                new Measurement(5, "Cq value", "400", sample.getId(), 10, lw1.getFirstSlot().getId()),
                new Measurement(6, "cDNA concentration", "5.5", sample.getId(), 11, lw1.getFirstSlot().getId()),
                new Measurement(7, "cDNA concentration", "6.6", sample.getId(), 12, lw2.getFirstSlot().getId()),
                new Measurement(8, "Library concentration", "3.3", sample2.getId(), 12, lwTOSlide.getFirstSlot().getId()),
                new Measurement(9, "Permeabilisation time", "10", sample2.getId(), 13, lwTOSlide.getFirstSlot().getId()),
                new Measurement(10, "Permeabilisation time", "120", sample3.getId(), 13, lw96WellPlateSource.getFirstSlot().getId())
        );

        Operation op11 = new Operation();
        op11.setOperationType(new OperationType(100, "anything"));
        Operation op12 = new Operation();
        op12.setOperationType(new OperationType(101, "Visium concentration"));
        Operation op13 = new Operation();
        op13.setOperationType(new OperationType(102, "Visium permeabilisation"));
        when(mockOpRepo.findById(11)).thenReturn(Optional.of(op11));
        when(mockOpRepo.findById(12)).thenReturn(Optional.of(op12));
        when(mockOpRepo.findById(13)).thenReturn(Optional.of(op13));

        when(mockMeasurementRepo.findAllBySlotIdIn(any())).thenReturn(measurements);
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lwTOSlide, lwTOSlide.getFirstSlot(), sample2),
                new ReleaseEntry(lw96WellPlate, lw96WellPlate.getFirstSlot(), sample3)
        );

        service.loadMeasurements(entries, ancestry);
        assertEquals("8", entries.get(0).getSectionThickness());
        assertEquals("2", entries.get(1).getSectionThickness());
        assertEquals(30, entries.get(0).getCoverage());
        assertNull(entries.get(1).getCoverage());
        assertEquals("6.6", entries.get(0).getVisiumConcentration());
        assertEquals("cDNA", entries.get(0).getVisiumConcentrationType());
        assertNull(entries.get(1).getVisiumConcentration());
        assertNull(entries.get(1).getVisiumConcentrationType());
        assertEquals("3.3", entries.get(2).getVisiumConcentration());
        assertEquals("Library", entries.get(2).getVisiumConcentrationType());
        assertEquals("400", entries.get(0).getCq());
        assertEquals("10 sec", entries.get(2).getPermTime());
        assertEquals("2 min", entries.get(3).getPermTime());
        assertNull(entries.get(1).getCq());
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
        return Arrays.stream(measurements).collect(groupingBy(Measurement::getSlotId));
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
        final Map<Integer, List<ReagentActionDetail>> radMap = makeRadMap(slot1, slot3);
        when(mockRadService.loadAncestralReagentTransfers(any())).thenReturn(radMap);

        service.loadReagentSources(entries);
        verify(service, times(2)).assembleTagData(any());
        Set<SlotSample> ss = entries.stream()
                .map(e -> new SlotSample(e.getSlot(), e.getSample()))
                .collect(toSet());
        verify(mockRadService).loadAncestralReagentTransfers(ss);

        assertEquals("123 : A1, 456 : B1", entries.get(0).getReagentSource());
        assertNull(entries.get(1).getReagentSource());
        assertEquals("456 : B2", entries.get(2).getReagentSource());
        assertEquals("rt1, rt2", entries.get(0).getReagentPlateType());
        assertEquals("rt2", entries.get(2).getReagentPlateType());
        assertEquals(Map.of("Alpha", "ABC", "Beta", "BCD", "Gamma", "GHI"), entries.get(0).getTagData());
    }

    @NotNull
    private Map<Integer, List<ReagentActionDetail>> makeRadMap(Slot slot1, Slot slot3) {
        final Address A1 = new Address(1, 1);
        final Address B1 = new Address(2, 1);
        final Address B2 = new Address(2,2);
        Map<String, String> tagData = Map.of("Alpha", "ABC", "Beta", "BCD", "Gamma", "GHI");
        return Map.of(
                slot1.getId(), List.of(
                        new ReagentActionDetail("123", "rt1", A1, A1, lw1.getId(), tagData),
                        new ReagentActionDetail("123", "rt1", A1, A1, lw1.getId(), null),
                        new ReagentActionDetail("456", "rt2", B1, A1, lw1.getId(), null)
                ),
                slot3.getId(), List.of(
                        new ReagentActionDetail("456", "rt2", B2, A1, lw2.getId(), null)
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2})
    public void testAssembleTagData(int numDicts) {
        List<Map<String, String>> datas = new ArrayList<>(numDicts);
        if (numDicts>0) {
            datas.add(orderedMap("Alpha", "X", "Beta", "Y"));
        }
        if (numDicts>1) {
            datas.add(Map.of("Alpha", "X", "Beta", "B", "Gamma", "G"));
        }
        Map<String, String> merged = service.assembleTagData(datas.stream());
        if (numDicts==0) {
            assertThat(merged).isEmpty();
            return;
        }
        if (numDicts==1) {
            assertSame(datas.getFirst(), merged);
            return;
        }
        assertThat(merged).containsExactly(
                Map.entry("Alpha", "X"),
                Map.entry("Beta", "Y,B"),
                Map.entry("Gamma", "G")
        );
    }

    @Test
    public void testLoadSamplePositions() {
        setupLabware();
        Slot slot1 = lw1.getFirstSlot();
        Sample sam1 = sample;
        Sample sam2 = sample1;
        SlotRegion top = new SlotRegion(1, "Top");
        SlotRegion bottom = new SlotRegion(2, "Bottom");
        List<SamplePosition> sps = List.of(
                new SamplePosition(slot1.getId(), sam1.getId(), top, 1),
                new SamplePosition(slot1.getId(), sam2.getId(), bottom, 1)
        );
        when(mockSamplePositionRepo.findAllBySlotIdIn(any())).thenReturn(sps);
        final Slot slot2 = lw2.getFirstSlot();
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, slot1, sam1),
                new ReleaseEntry(lw1, slot1, sam2),
                new ReleaseEntry(lw2, slot2, sam1)
        );
        service.loadSamplePositions(entries);
        verify(mockSamplePositionRepo).findAllBySlotIdIn(Set.of(slot1.getId(), slot2.getId()));
        assertEquals(top.getName(), entries.get(0).getSamplePosition());
        assertEquals(bottom.getName(), entries.get(1).getSamplePosition());
        assertNull(entries.get(2).getSamplePosition());
    }

    @Test
    public void testLoadSectionComments() {
        setupLabware();
        int[] slotIds = { lw1.getFirstSlot().getId(), lw2.getFirstSlot().getId() };
        int[] sampleIds = { sample.getId(), sample1.getId() };
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample),
                new ReleaseEntry(lw1, lw1.getFirstSlot(), sample1),
                new ReleaseEntry(lw2, lw2.getFirstSlot(), sample)
        );
        Comment[] coms = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> new Comment(i, "com"+i, "cat"))
                .toArray(Comment[]::new);
        List<OperationComment> opcoms = List.of(
                new OperationComment(1, coms[0], 1, sampleIds[0], slotIds[0], null),
                new OperationComment(2, coms[1], 1, sampleIds[0], slotIds[0], null),
                new OperationComment(3, coms[0], 1, sampleIds[1], slotIds[0], null),
                new OperationComment(4, coms[1], 1, sampleIds[1], slotIds[1], null)
        );
        when(mockOpTypeRepo.getByName("Section")).thenReturn(opType);
        when(mockOpComRepo.findAllBySlotAndOpType(any(), any())).thenReturn(opcoms);

        service.loadSectionComments(entries);
        verify(mockOpTypeRepo).getByName("Section");
        verify(mockOpComRepo).findAllBySlotAndOpType(Set.of(slotIds[0], slotIds[1]), opType);

        assertEquals("com1; com2", entries.get(0).getSectionComment());
        assertEquals("com1", entries.get(1).getSectionComment());
        assertNull(entries.get(2).getSectionComment());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @CsvSource({
            "true,",
            "false,",
            "true,sample_processing&histology",
            "true,visium&xenium"
    })
    public void testComputeColumns(boolean anyTagData, String joinedOptions) {
        Set<ReleaseFileOption> options;
        if (joinedOptions==null) {
            options = Set.of();
        } else {
            options = EnumSet.noneOf(ReleaseFileOption.class);
            for (String s : joinedOptions.split("&")) {
                options.add(ReleaseFileOption.forParameterName(s));
            }
        }
        List<ReleaseEntry> entries = List.of(
                new ReleaseEntry(null, null, null),
                new ReleaseEntry(null, null, null),
                new ReleaseEntry(null, null, null)
        );
        ReleaseFileMode mode = ReleaseFileMode.CDNA;
        if (anyTagData) {
            entries.get(1).setTagData(orderedMap("Alpha", "A", "Beta", "B"));
            entries.get(2).setTagData(orderedMap("Beta", "8", "Gamma", "9"));
        }
        var columns = service.computeColumns(new ReleaseFileContent(mode, entries, options));
        List modeColumns = ReleaseColumn.forModeAndOptions(mode, options);
        if (!anyTagData || !options.contains(ReleaseFileOption.Visium)) {
            assertThat(columns).containsExactlyElementsOf(modeColumns);
            return;
        }
        List<String> tagColumnNames = List.of("Alpha", "Beta", "Gamma");
        var partition = columns.stream().collect(partitioningBy(c -> tagColumnNames.contains(c.toString())));
        assertThat(partition.get(true).stream().map(Object::toString)).containsExactlyElementsOf(tagColumnNames);
        assertThat(partition.get(false)).containsExactlyElementsOf(modeColumns);
    }

    @Test
    public void testLoadXeniumFields() {
        Collection<ReleaseEntry> entries = List.of(new ReleaseEntry(null, null, null));
        Set<Integer> slotIds = Set.of(17);

        List<TriConsumer<ReleaseFileService, Collection<ReleaseEntry>, Set<Integer>>> methods = List.of(
                ReleaseFileService::loadProbeHybridisation,
                ReleaseFileService::loadProbeHybridisationQC,
                ReleaseFileService::loadXeniumAnalyser,
                ReleaseFileService::loadXeniumQC
        );
        methods.forEach(method -> method.accept(doNothing().when(service), any(), any()));

        service.loadXeniumFields(entries, slotIds);

        methods.forEach(method -> method.accept(verify(service), entries, slotIds));
    }

    @ParameterizedTest
    @MethodSource("commentDescOrNullArgs")
    public void testCommentDescOrNull(OperationComment opcom, ReleaseEntry entry, String expected) {
        assertEquals(expected, service.commentDescOrNull(opcom, entry));
    }

    static Stream<Arguments> commentDescOrNullArgs() {
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Slot slot = lw.getFirstSlot();
        final String shortDesc = "Alpha";
        final Comment com = new Comment(1, shortDesc, "cat");
        OperationComment vagueOpCom = new OperationComment(1, com, null, null, null, lw.getId());
        OperationComment wrongLwOpCom = new OperationComment(1, com, null, null, null, lw.getId()+1);
        OperationComment ssOpCom = new OperationComment(1, com, null, sample.getId(), slot.getId(), null);
        OperationComment wrongSampleOpCom = new OperationComment(1, com, null, sample.getId()+1, slot.getId(), null);
        OperationComment wrongSlotOpCom = new OperationComment(1, com, null, sample.getId(), slot.getId()+1, null);

        String fullDesc = String.format("(A1 %s): Alpha", sample.getId());

        return Arrays.stream(new Object[][] {
                {vagueOpCom, new ReleaseEntry(lw, null, null), shortDesc},
                {vagueOpCom, new ReleaseEntry(lw, slot, sample), shortDesc},
                {wrongLwOpCom, new ReleaseEntry(lw, null, null), null},
                {wrongLwOpCom, new ReleaseEntry(lw, slot, sample), null},
                {ssOpCom, new ReleaseEntry(lw, null, null), fullDesc},
                {ssOpCom, new ReleaseEntry(lw, slot, sample), shortDesc},
                {ssOpCom, new ReleaseEntry(lw, slot, null), fullDesc},
                {ssOpCom, new ReleaseEntry(lw, null, sample), fullDesc},
                {wrongSampleOpCom, new ReleaseEntry(lw, null, sample), null},
                {wrongSampleOpCom, new ReleaseEntry(lw, slot, sample), null},
                {wrongSlotOpCom, new ReleaseEntry(lw, slot, sample), null},
        }).map(Arguments::of);
    }

    @Test
    public void testLoadProbeHybridisation() {
        OperationType opType = EntityFactory.makeOperationType("Probe hybridisation Xenium", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        List<ReleaseEntry> entries = Arrays.stream(lws)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .collect(toList());
        Set<Integer> slotIds = Arrays.stream(lws).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lws[0]), List.of(lws[0]));
        List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);
        List<LabwareProbe> probes = List.of(
                new LabwareProbe(10, new ProbePanel(1, "Alpha"), op.getId(), lws[0].getId(), "lot1", 8),
                new LabwareProbe(11, new ProbePanel(2, "Beta"), op.getId(), lws[0].getId(), "lot2", 9)
        );
        when(mockLwProbeRepo.findAllByOperationIdIn(any())).thenReturn(probes);

        service.loadProbeHybridisation(entries, slotIds);
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(mockLwProbeRepo).findAllByOperationIdIn(List.of(op.getId()));

        ReleaseEntry entry = entries.getFirst();
        assertEquals(op.getPerformed(), entry.getHybridStart());
        assertEquals("8, 9", entry.getXeniumPlex());
        assertEquals("Alpha, Beta", entry.getXeniumProbe());
        assertEquals("lot1, lot2", entry.getXeniumProbeLot());
        entry = entries.get(1);
        assertNull(entry.getHybridStart());
        assertNull(entry.getXeniumPlex());
        assertNull(entry.getXeniumProbe());
        assertNull(entry.getXeniumProbeLot());
    }

    @Test
    public void testLoadProbeHybridisationQC() {
        OperationType opType = EntityFactory.makeOperationType("Probe hybridisation QC", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        List<ReleaseEntry> entries = Arrays.stream(lws)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .collect(toList());
        Set<Integer> slotIds = Arrays.stream(lws).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lws[0]), List.of(lws[0]));
        List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);

        Integer opId = op.getId();
        Integer lwId = lws[0].getId();
        List<OperationComment> opcoms = List.of(
                new OperationComment(20, new Comment(1, "California", null), opId, null, null, lwId),
                new OperationComment(21, new Comment(2, "Colorado", null), opId, null, null, lwId)
        );
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(opcoms);

        service.loadProbeHybridisationQC(entries, slotIds);
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(mockOpComRepo).findAllByOperationIdIn(List.of(op.getId()));

        ReleaseEntry entry = entries.getFirst();
        assertEquals(op.getPerformed(), entry.getHybridEnd());
        assertEquals("California. Colorado.", entry.getHybridComment());
        entry = entries.get(1);
        assertNull(entry.getHybridEnd());
        assertNull(entry.getHybridComment());
    }

    @Test
    public void testLoadXeniumAnalyser() {
        OperationType opType = EntityFactory.makeOperationType("Xenium analyser", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        List<ReleaseEntry> entries = Arrays.stream(lws)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .collect(toList());
        Set<Integer> slotIds = Arrays.stream(lws).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lws[0]), List.of(lws[0]));
        op.setEquipment(new Equipment("Xenium 1", AnalyserServiceImp.EQUIPMENT_CATEGORY));
        List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);

        Integer opId = op.getId();
        Integer lwId = lws[0].getId();
        List<Integer> opIds = List.of(opId);

        List<Roi> rois = List.of(
                new Roi(lws[0].getFirstSlot().getId(), sample.getId(), opId, "leng")
        );
        when(mockRoiRepo.findAllByOperationIdIn(any())).thenReturn(rois);

        List<LabwareNote> notes = List.of(
                new LabwareNote(1, lwId, opId, AnalyserServiceImp.LOT_A_NAME, "lot Alpha"),
                new LabwareNote(1, lwId, opId, AnalyserServiceImp.LOT_B_NAME, "lot Beta"),
                new LabwareNote(2, lwId, opId, AnalyserServiceImp.POSITION_NAME, "left"),
                new LabwareNote(3, lwId, opId, AnalyserServiceImp.RUN_NAME, "run1")
        );
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).thenReturn(notes);

        service.loadXeniumAnalyser(entries, slotIds);
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(mockRoiRepo).findAllByOperationIdIn(opIds);
        verify(mockLwNoteRepo).findAllByOperationIdIn(opIds);

        ReleaseEntry entry = entries.getFirst();
        assertEquals(op.getPerformed(), entry.getXeniumStart());
        assertEquals("lot Alpha", entry.getXeniumReagentALot());
        assertEquals("lot Beta", entry.getXeniumReagentBLot());
        assertEquals("left", entry.getXeniumCassettePosition());
        assertEquals("run1", entry.getXeniumRun());
        assertEquals("leng", entry.getXeniumRoi());
        assertEquals("Xenium 1", entry.getEquipment());

        entry = entries.get(1);
        assertNull(entry.getXeniumStart());
        assertNull(entry.getXeniumReagentALot());
        assertNull(entry.getXeniumReagentBLot());
        assertNull(entry.getXeniumCassettePosition());
        assertNull(entry.getXeniumRun());
        assertNull(entry.getXeniumRoi());
    }


    @Test
    public void testLoadXeniumQC() {
        OperationType opType = EntityFactory.makeOperationType("Xenium QC", null);
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        List<ReleaseEntry> entries = Arrays.stream(lws)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .collect(toList());
        Set<Integer> slotIds = Arrays.stream(lws).map(lw -> lw.getFirstSlot().getId()).collect(toSet());
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lws[0]), List.of(lws[0]));
        List<Operation> ops = List.of(op);
        when(mockOpRepo.findAllByOperationTypeAndDestinationSlotIdIn(any(), any())).thenReturn(ops);

        Integer opId = op.getId();
        Integer lwId = lws[0].getId();

        List<OperationComment> opcoms = List.of(
                new OperationComment(20, new Comment(1, "Connecticut", null), opId, null, null, lwId),
                new OperationComment(21, new Comment(2, "Delaware", null), opId, null, null, lwId)
        );
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(opcoms);

        service.loadXeniumQC(entries, slotIds);
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(mockOpComRepo).findAllByOperationIdIn(List.of(opId));

        ReleaseEntry entry = entries.getFirst();
        assertEquals(op.getPerformed(), entry.getXeniumEnd());
        assertEquals("Connecticut. Delaware.", entry.getXeniumComment());

        entry = entries.get(1);
        assertNull(entry.getXeniumEnd());
        assertNull(entry.getXeniumComment());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadFlags(boolean anyFlags) {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lws = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toList();
        List<ReleaseEntry> entries = lws.stream()
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), lw.getFirstSlot().getSamples().getFirst()))
                .toList();
        List<FlagDetail> details;
        if (anyFlags) {
            details = List.of(new FlagDetail(lws.getFirst().getBarcode(), List.of(
                    new FlagDetail.FlagSummary("STAN-1", "Flag 1"),
                    new FlagDetail.FlagSummary("STAN-2", "Flag 2"))));
        } else {
            details = List.of();
        }
        doReturn(details).when(mockFlagLookupService).lookUpDetails(any());
        if (anyFlags) {
            doAnswer(invocation -> {
                List<FlagDetail.FlagSummary> summaries = invocation.getArgument(0);
                return (summaries.isEmpty() ? "" : "FLAG DESC");
            }).when(service).describeFlags(any());
        }

        service.loadFlags(entries);
        for (ReleaseEntry entry : entries) {
            if (anyFlags && entry.getLabware().equals(lws.getFirst())) {
                assertEquals(entry.getFlagDescription(), "FLAG DESC");
            } else {
                assertThat(entry.getFlagDescription()).isEmpty();
            }
        }
        verify(mockFlagLookupService).lookUpDetails(sameElements(lws, true));
        if (anyFlags) {
            verify(service).describeFlags(details.getFirst().getFlags());
        } else {
            verify(service, never()).describeFlags(any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testDescribeFlags(boolean anyFlags) {
        List<FlagDetail.FlagSummary> summaries;
        if (anyFlags) {
            summaries = List.of(
                    new FlagDetail.FlagSummary("STAN-1", "Flag 1."),
                    new FlagDetail.FlagSummary("STAN-2", "Flag 2.")
            );
        } else {
            summaries = List.of();
        }
        String desc = service.describeFlags(summaries);
        if (anyFlags) {
            assertEquals("STAN-1: Flag 1. STAN-2: Flag 2.", desc);
        } else {
            assertThat(desc).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testVisiumBarcode(boolean found) {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        LabwareType visiumLt = new LabwareType(10, "Visium lw", 1, 1, EntityFactory.getLabelType(), true);
        Labware[] lws = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeLabware(lt, sample)).toArray(Labware[]::new);
        lws[3].setExternalBarcode("VIS1");
        if (found) {
            lws[3].setLabwareType(visiumLt);
        }
        Slot[] slots = Arrays.stream(lws).map(Labware::getFirstSlot).toArray(Slot[]::new);
        ReleaseEntry entry = new ReleaseEntry(lws[0], slots[0], sample);
        Ancestry mockAncestry = mock(Ancestry.class);
        Set<SlotSample> ancestors = Arrays.stream(slots)
                .map(slot -> new SlotSample(slot, sample))
                .collect(toLinkedHashSet());
        when(mockAncestry.ancestors(any())).thenReturn(ancestors);
        Map<Integer, Labware> lwMap = Arrays.stream(lws).collect(inMap(Labware::getId));
        assertEquals(found ? "VIS1" : null, service.visiumBarcode(entry, mockAncestry, lwMap));
        verify(mockAncestry).ancestors(new SlotSample(slots[0], sample));
    }

    @Test
    public void testLoadVisiumBarcodes() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware[] lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeLabware(lt, sample)).toArray(Labware[]::new);
        ReleaseEntry[] entries = Arrays.stream(lws)
                .map(lw -> new ReleaseEntry(lw, lw.getFirstSlot(), sample))
                .toArray(ReleaseEntry[]::new);
        Ancestry mockAncestry = mock(Ancestry.class);
        Set<SlotSample> slotSamples = Arrays.stream(lws).map(lw -> new SlotSample(lw.getFirstSlot(), sample)).collect(toSet());
        when(mockAncestry.keySet()).thenReturn(slotSamples);
        when(mockLabwareRepo.findAllByIdIn(any())).thenReturn(Arrays.asList(lws));

        doReturn(null).when(service).visiumBarcode(same(entries[0]), any(), any());
        doReturn("VIS1").when(service).visiumBarcode(same(entries[1]), any(), any());
        doReturn("VIS2").when(service).visiumBarcode(same(entries[2]), any(), any());

        Map<Integer, Labware> expectedLwMap = Arrays.stream(lws).collect(inMap(Labware::getId));
        Set<Integer> lwIds = Arrays.stream(lws).map(Labware::getId).collect(toSet());

        service.loadVisiumBarcodes(Arrays.asList(entries), mockAncestry);

        verify(mockAncestry).keySet();
        verify(mockLabwareRepo).findAllByIdIn(lwIds);
        for (ReleaseEntry entry : entries) {
            verify(service).visiumBarcode(entry, mockAncestry, expectedLwMap);
        }

        assertNull(entries[0].getVisiumBarcode());
        assertEquals("VIS1", entries[1].getVisiumBarcode());
        assertEquals("VIS2", entries[2].getVisiumBarcode());
    }

    private LocalDateTime time(int day) {
        return LocalDateTime.of(2021,12,1+day, 12,0,0);
    }

    private Operation makeOp(int id, OperationType opType, Labware lw, LocalDateTime time) {
        Slot slot = lw.getFirstSlot();
        Sample sam = slot.getSamples().getFirst();
        Action action = new Action(10*id, id, slot, slot, sam, sam);
        return new Operation(id, opType, time, List.of(action), null);
    }

    private static Ancestry makeAncestry(Object... args) {
        Ancestry ancestry = new Ancestry();
        for (int i = 0; i < args.length; i += 4) {
            ancestry.put(slotSample(args[i], args[i+1]), Set.of(slotSample(args[i+2], args[i+3])));
        }
        return ancestry;
    }

    private static SlotSample slotSample(Object arg1, Object arg2) {
        return new SlotSample(slot(arg1), (Sample) arg2);
    }

    private static Slot slot(Object arg) {
        if (arg instanceof Labware) {
            return ((Labware) arg).getFirstSlot();
        }
        return (Slot) arg;
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
