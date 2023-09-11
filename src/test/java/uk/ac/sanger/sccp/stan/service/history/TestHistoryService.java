package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.History;
import uk.ac.sanger.sccp.stan.request.HistoryEntry;
import uk.ac.sanger.sccp.stan.request.SamplePositionResult;
import uk.ac.sanger.sccp.stan.service.SlotRegionService;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.wildcardToLikeSql;

/**
 * Tests {@link HistoryServiceImp}
 * @author dr6
 */
public class TestHistoryService {
    private OperationRepo mockOpRepo;
    private LabwareRepo mockLwRepo;
    private SampleRepo mockSampleRepo;
    private TissueRepo mockTissueRepo;
    private DonorRepo mockDonorRepo;
    private ReleaseRepo mockReleaseRepo;
    private DestructionRepo mockDestructionRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private RoiRepo mockRoiRepo;
    private SnapshotRepo mockSnapshotRepo;
    private WorkRepo mockWorkRepo;
    private MeasurementRepo mockMeasurementRepo;
    private LabwareNoteRepo mockLwNoteRepo;
    private ResultOpRepo mockResultOpRepo;
    private StainTypeRepo mockStainTypeRepo;
    private LabwareProbeRepo mockLwProbeRepo;
    private ReagentActionDetailService mockRadService;
    private SlotRegionService mockSlotRegionService;

    private HistoryServiceImp service;

    private Sample[] samples;
    private Labware[] labware;
    private List<Operation> ops;

    @BeforeEach
    public void setup() {
        mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockDonorRepo = mock(DonorRepo.class);
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockDestructionRepo = mock(DestructionRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockRoiRepo = mock(RoiRepo.class);
        mockSnapshotRepo = mock(SnapshotRepo.class);
        mockWorkRepo = mock(WorkRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockLwNoteRepo = mock(LabwareNoteRepo.class);
        mockResultOpRepo = mock(ResultOpRepo.class);
        mockRadService = mock(ReagentActionDetailService.class);
        mockStainTypeRepo = mock(StainTypeRepo.class);
        mockSlotRegionService = mock(SlotRegionService.class);
        mockLwProbeRepo = mock(LabwareProbeRepo.class);

        service = spy(new HistoryServiceImp(mockOpRepo, mockLwRepo, mockSampleRepo, mockTissueRepo, mockDonorRepo,
                mockReleaseRepo, mockDestructionRepo, mockOpCommentRepo, mockRoiRepo, mockSnapshotRepo, mockWorkRepo,
                mockMeasurementRepo, mockLwNoteRepo, mockResultOpRepo, mockStainTypeRepo, mockLwProbeRepo,
                mockRadService, mockSlotRegionService));
    }

    @Test
    public void testGetHistoryForSampleId() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        when(mockSampleRepo.findById(sample.getId())).thenReturn(Optional.of(sample));
        Sample sample2 = new Sample(sample.getId()+1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(sample.getTissue().getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForSampleId(sample.getId()));

        when(mockSampleRepo.findById(-1)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getHistoryForSampleId(-1));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetHistoryForExternalName(boolean wildcard) {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        String string = tissue.getExternalName();
        if (wildcard) {
            string = "TIS*";
            when(mockTissueRepo.findAllByExternalNameLike("TIS%")).thenReturn(List.of(tissue));
        } else {
            when(mockTissueRepo.getAllByExternalName(tissue.getExternalName())).thenReturn(List.of(tissue));
        }
        Sample sample2 = new Sample(sample.getId()+1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(tissue.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForExternalName(string));
    }

    @Test
    public void testGetHistoryForDonorName() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        final Donor donor = tissue.getDonor();
        Tissue tissue2 = EntityFactory.makeTissue(donor, EntityFactory.getSpatialLocation());
        when(mockTissueRepo.findByDonorId(donor.getId())).thenReturn(List.of(tissue, tissue2));
        when(mockDonorRepo.getByDonorName(donor.getDonorName())).thenReturn(donor);
        Sample sample2 = new Sample(sample.getId()+1, 10, tissue2, sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        when(mockSampleRepo.findAllByTissueIdIn(List.of(tissue.getId(), tissue2.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForDonorName(donor.getDonorName()));
    }

    @Test
    public void testGetHistoryForLabwareBarcode() {
        History history = new History();
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue.getDonor(), EntityFactory.getSpatialLocation());
        Sample sample2 = new Sample(sample.getId()+1, 10, tissue2, sample.getBioState());
        Sample sample3 = new Sample(sample2.getId()+1, 11, tissue2, sample.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, sample, sample2);
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        List<Sample> samples = List.of(sample, sample2, sample3);
        when(mockSampleRepo.findAllByTissueIdIn(Set.of(tissue.getId(), tissue2.getId()))).thenReturn(samples);
        doReturn(history).when(service).getHistoryForSamples(samples);

        assertSame(history, service.getHistoryForLabwareBarcode(lw.getBarcode()));
    }

    @Test
    public void testGetHistoryForWorkNumber() {
        Work work = new Work(10, "SGP10", null, null, null, null, null, Work.Status.active);
        List<Operation> ops = List.of(
                new Operation(20, null, null, null, null),
                new Operation(21, null, null, null, null)
        );
        final List<Integer> opIds = List.of(20, 21);
        work.setOperationIds(opIds);
        final List<Integer> releaseIds = List.of(30,31);
        Labware rlw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        Labware rlw2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        List<Release> releases = List.of(
                new Release(30, rlw1, null, null, null, null, null),
                new Release(31, rlw2, null, null, null, null, null)
        );
        work.setReleaseIds(releaseIds);
        String workNumber = "sgp10";
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        when(mockOpRepo.findAllById(opIds)).thenReturn(ops);
        when(mockReleaseRepo.findAllByIdIn(releaseIds)).thenReturn(releases);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, sam1.getSection()+1, sam1.getTissue(), sam1.getBioState());
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), sam1);
        List<Labware> lws = List.of(lw1, lw2);
        Set<Integer> labwareIds = Set.of(lw1.getId(), lw2.getId());
        doReturn(labwareIds).when(service).labwareIdsFromOps(ops);
        when(mockLwRepo.findAllByIdIn(labwareIds)).thenReturn(lws);
        // use a mutable list for this because it will be sorted
        List<HistoryEntry> entries = new ArrayList<>(2);
        entries.add(new HistoryEntry(200, "Release", makeTime(1), lw1.getId(), lw2.getId(),
                sam1.getId(), "", workNumber));
        entries.add(new HistoryEntry(20, "Bananas", makeTime(2), lw1.getId(), lw2.getId(),
                sam2.getId(), "", workNumber, null, "A1, A2", "Top Right"));
        doReturn(entries.subList(1,2)).when(service).createEntriesForOps(ops, null, lws, null, work.getWorkNumber());

        doReturn(entries.subList(0,1)).when(service).createEntriesForReleases(releases, null, null, work.getWorkNumber());

        List<Sample> samples = List.of(sam1,sam2);
        List<Labware> allLabware = BasicUtils.concat(lws, List.of(rlw1, rlw2));
        doReturn(samples).when(service).referencedSamples(sameElements(entries, true), sameElements(allLabware, true));

        History history = service.getHistoryForWorkNumber(workNumber);
        assertEquals(entries, history.getEntries());
        assertEquals(samples, history.getSamples());
        assertEquals(allLabware, history.getLabware());
    }

    @Test
    public void testGetHistoryForWorkNumber_noOps() {
        Work work = new Work(10, "SGP10", null, null, null, null, null, Work.Status.active);
        work.setOperationIds(List.of());
        final String workNumber = "sgp10";
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        History history = service.getHistoryForWorkNumber(workNumber);
        assertThat(history.getEntries()).isEmpty();
        assertThat(history.getLabware()).isEmpty();
        assertThat(history.getSamples()).isEmpty();
    }

    @Test
    public void testLabwareIdsFromOps() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware[] lws = IntStream.range(0, 4).mapToObj(i -> EntityFactory.makeLabware(lt, sample)).toArray(Labware[]::new);
        Integer[] lwIds = Arrays.stream(lws).map(Labware::getId).toArray(Integer[]::new);
        Operation op1 = EntityFactory.makeOpForLabware(null, List.of(lws[0], lws[1]), List.of(lws[2]));
        Operation op2 = EntityFactory.makeOpForLabware(null, List.of(lws[0]), List.of(lws[3]));
        assertThat(service.labwareIdsFromOps(List.of(op1, op2))).containsExactlyInAnyOrder(lwIds);
    }

    @Test
    public void testReferencesSamples() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Tissue tissue = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        Sample[] samples = IntStream.range(1, 7)
                .mapToObj(i -> new Sample(i, 10+i, tissue, bs))
                .toArray(Sample[]::new);
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.getFirstSlot().getSamples().addAll(List.of(samples[0], samples[1]));
        lw1.getSlots().get(1).getSamples().add(samples[2]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[0], samples[3]);
        List<HistoryEntry> entries = IntStream.range(1, 7)
                .mapToObj(i -> new HistoryEntry(40+i, null, null, lw1.getId(), lw2.getId(), i,
                        null, null))
                .collect(toList());

        when(mockSampleRepo.findAllByIdIn(Set.of(5,6))).thenReturn(List.of(samples[4], samples[5]));
        assertThat(service.referencedSamples(entries, List.of(lw1, lw2))).containsExactlyInAnyOrder(samples);
    }

    @ParameterizedTest
    @CsvSource({
            "by work number,SGP5,,,",
            "by barcode,SGP5,STAN-1,EXT1,DONOR1",
            "by tissues,SGP5,,EXT1,DONOR1",
    })
    public void testGetHistory(String mode, String workNumber, String barcode, String externalName, String donorName) {
        List<Sample> samples = List.of(EntityFactory.getSample());
        History history = new History(null, samples, null);
        if (mode.equalsIgnoreCase("by work number")) {
            doReturn(history).when(service).getHistoryForWorkNumber(workNumber);
        } else {
            if (mode.equalsIgnoreCase("by barcode")) {
                doReturn(samples).when(service).samplesForBarcode(barcode, externalName, donorName);
            } else {
                doReturn(samples).when(service).samplesForTissues(externalName, donorName);
            }
            doReturn(history).when(service).getHistoryForSamples(samples, workNumber);
        }

        assertSame(history, service.getHistory(workNumber, barcode, externalName, donorName));
    }

    @Test
    public void testSamplesForBarcode() {
        String barcode = "STAN-1";
        String externalName = "EXT1";
        String donorName = "donor1";
        Sample[] lwSamples = IntStream.rangeClosed(1,2)
                .mapToObj(i -> {
                    Donor donor = new Donor(i, "donor"+i, null, null);
                    Tissue tissue = EntityFactory.makeTissue(donor, null);
                    tissue.setId(10+i);
                    return new Sample(100+i, i, tissue, null);
                })
                .toArray(Sample[]::new);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeLabware(lt, lwSamples);
        lw.setBarcode(barcode);
        when(mockLwRepo.getByBarcode(barcode)).thenReturn(lw);
        Predicate<Tissue> filter = t -> t.getDonor().getDonorName().equals("donor1");
        doReturn(filter).when(service).tissuePredicate(donorName, externalName);
        List<Sample> returnSamples = List.of(lwSamples[0], EntityFactory.getSample());
        when(mockSampleRepo.findAllByTissueIdIn(any())).thenReturn(returnSamples);

        assertSame(returnSamples, service.samplesForBarcode(barcode, externalName, donorName));
        verify(mockSampleRepo).findAllByTissueIdIn(Set.of(lwSamples[0].getTissue().getId()));
    }

    @ParameterizedTest
    @MethodSource("samplesForTissuesArgs")
    public void testSamplesForTissues(String externalName, String donorName, List<Integer> expectedTissueIds,
                                      List<Sample> samples, List<Tissue> foundTissues) {
        if (externalName == null) {
            Donor donor = EntityFactory.getDonor();
            when(mockDonorRepo.getByDonorName(donorName)).thenReturn(donor);
            when(mockTissueRepo.findByDonorId(donor.getId())).thenReturn(foundTissues);
        } else if (externalName.indexOf('*') >= 0) {
            when(mockTissueRepo.findAllByExternalNameLike(wildcardToLikeSql(externalName)))
                    .thenReturn(foundTissues);
        } else {
            when(mockTissueRepo.getAllByExternalName(externalName))
                    .thenReturn(foundTissues);
        }

        when(mockSampleRepo.findAllByTissueIdIn(any())).thenReturn(samples);

        assertSame(samples, service.samplesForTissues(externalName, donorName));

        verify(mockSampleRepo).findAllByTissueIdIn(expectedTissueIds);
    }

    static Stream<Arguments> samplesForTissuesArgs() {
        Donor[] donors = IntStream.rangeClosed(1,2)
                .mapToObj(i -> new Donor(i, "donor"+i, null, null))
                .toArray(Donor[]::new);
        List<Tissue> tissues = Arrays.stream(donors)
                .map(d -> EntityFactory.makeTissue(d, null))
                .collect(toList());
        List<Integer> tissueIds = tissues.stream().map(Tissue::getId).collect(toList());

        List<Sample> samples = List.of(EntityFactory.getSample());

        return Arrays.stream(new Object[][] {
                {null, "donor1", tissueIds},
                {"ext1", null, tissueIds},
                {"ext1", "donor1", tissueIds.subList(0,1)},
                {"ex*", "donor1", tissueIds.subList(0,1)},
                {"ext1", "donor2", tissueIds.subList(1,2)},
        }).map(arr -> Arguments.of(arr[0], arr[1], arr[2], samples, tissues));
    }

    @ParameterizedTest
    @MethodSource("tissuePredicateArgs")
    public void testTissuePredicate(Tissue tissue, String donorName, String extName, boolean expected) {
        Predicate<Tissue> predicate = service.tissuePredicate(donorName, extName);
        assertEquals(expected, predicate.test(tissue));
    }

    static Stream<Arguments> tissuePredicateArgs() {
        Donor d = new Donor(1, "DONOR1", null, null);
        Tissue tissue = EntityFactory.makeTissue(d, null);
        tissue.setExternalName("EXT1");
        return Arrays.stream(new Object[][] {
                { null, null, true },
                { "donor1", null, true },
                { "donor2", null, false },
                { null, "ext1", true },
                { null, "ext2", false },
                { "donor1", "ext1", true },
                { "donor1", "ext2", false },
                { "donor2", "ext1", false },
                { null, "ext*", true },
                { "donor1", "ext*", true },
                { "donor2", "ext*", false },
                { null, "abc*", false },
                { "donor1", "abc*", false },
        }).map(arr -> Arguments.of(tissue, arr[0], arr[1], arr[2]));
    }

    @Test
    public void testGetHistoryForSamples() {
        Sample sample = EntityFactory.getSample();
        Sample sample2 = new Sample(sample.getId() + 1, 10, sample.getTissue(), sample.getBioState());
        List<Sample> samples = List.of(sample, sample2);
        Set<Integer> sampleIds = Set.of(sample.getId(), sample2.getId());
        List<Operation> ops = List.of(new Operation(100, null, null, null, null));
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeLabware(lt, samples.get(i))).collect(toList());
        Set<Integer> labwareIds = Set.of(labware.get(0).getId(), labware.get(1).getId());
        List<Destruction> destructions = List.of(new Destruction());
        Release rel = new Release();
        rel.setId(50);
        List<Release> releases = List.of(rel);
        Map<Integer, Set<String>> opWork = Map.of(1, Set.of("R&D50"), 2, Set.of());
        when(mockWorkRepo.findWorkNumbersForOpIds(Set.of(100))).thenReturn(opWork);
        Map<Integer, String> releaseWork = Map.of(1, "SGP1");
        when(mockWorkRepo.findWorkNumbersForReleaseIds(List.of(rel.getId()))).thenReturn(releaseWork);

        List<HistoryEntry> opEntries = List.of(new HistoryEntry(1, "op", null, 1, 1, null, "user1", "R&D50"));
        List<HistoryEntry> releaseEntries = List.of(new HistoryEntry(2, "release", null, 1, 1, null, "user2", null));
        List<HistoryEntry> destructionEntries = List.of(new HistoryEntry(3, "destruction", null, 1, 1, null, "user3", null));

        List<HistoryEntry> entries = List.of(opEntries.get(0), releaseEntries.get(0), destructionEntries.get(0));

        when(mockOpRepo.findAllBySampleIdIn(sampleIds)).thenReturn(ops);
        when(mockDestructionRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(destructions);
        when(mockReleaseRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(releases);
        when(mockLwRepo.findAllByIdIn(labwareIds)).thenReturn(labware);

        doReturn(labwareIds).when(service).loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);

        doReturn(opEntries).when(service).createEntriesForOps(ops, sampleIds, labware, opWork, null);
        doReturn(releaseEntries).when(service).createEntriesForReleases(releases, sampleIds, releaseWork, null);
        doReturn(destructionEntries).when(service).createEntriesForDestructions(destructions, sampleIds);

        doReturn(entries).when(service).assembleEntries(List.of(opEntries, releaseEntries, destructionEntries));

        History history = service.getHistoryForSamples(samples);
        assertEquals(history.getEntries(), entries);
        assertEquals(history.getSamples(), samples);
        assertEquals(history.getLabware(), labware);
    }

    private static Stream<Slot> streamSlots(Labware lw, Address... addresses) {
        return Arrays.stream(addresses).map(lw::getSlot);
    }

    private void createSamples() {
        if (samples==null) {
            Sample sample = EntityFactory.getSample();
            samples = new Sample[3];
            samples[0] = sample;
            for (int i = 1; i < samples.length; ++i) {
                samples[i] = new Sample(sample.getId()+i, 10+i, sample.getTissue(), sample.getBioState());
            }
        }
    }

    private void createLabware() {
        createSamples();
        if (labware==null) {
            LabwareType lt = EntityFactory.makeLabwareType(3,1);
            int[][] samplesInLabware = {{0,1,2},{0,1},{1},{2}};
            labware = Arrays.stream(samplesInLabware)
                    .map(arr -> Arrays.stream(arr).mapToObj(i -> samples[i]).toArray(Sample[]::new))
                    .map(contents -> EntityFactory.makeLabware(lt, contents))
                    .toArray(Labware[]::new);
        }
    }

    private User getUser() {
        return EntityFactory.getUser();
    }

    // Op 1: samples 0,1 from labware 0 to labware 1
    //       sample 1 from labware 0 to labware 2
    // Op 2: sample 2 from labware 0 to labware 3
    // We are not interested in sample 1, so we should get labware 0, 1 and 3 returned.
    private void createOps() {
        createLabware();
        if (ops==null) {
            final Address A1 = new Address(1,1);
            final Address B1 = new Address(2,1);
            final Address C1 = new Address(3,1);

            OperationType opType = EntityFactory.makeOperationType("Catapult", null);
            OperationType stainOpType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
            Operation stain = EntityFactory.makeOpForSlots(stainOpType, List.of(labware[0].getSlot(C1)), List.of(labware[3].getFirstSlot()), getUser());
            ops = List.of(
                    EntityFactory.makeOpForSlots(opType, streamSlots(labware[0], A1, B1, B1).collect(toList()),
                            Stream.concat(streamSlots(labware[1], A1, B1), streamSlots(labware[2], A1)).collect(toList()), getUser()),
                    stain
            );
        }
    }

    @Test
    public void testLoadLabwareIdsForOpsAndSampleIds() {
        createOps();
        // Op 1: samples 0,1 from labware 0 to labware 1
        //       sample 1 from labware 0 to labware 2
        // Op 2: sample 2 from labware 0 to labware 3

        Set<Integer> sampleIds = Set.of(samples[0].getId(), samples[2].getId());
        // We are not interested in sample 1, so we should get labware 0, 1 and 3 returned.

        assertEquals(Set.of(labware[0].getId(), labware[1].getId(), labware[3].getId()),
                service.loadLabwareIdsForOpsAndSampleIds(ops, sampleIds));
    }

    @Test
    public void testLoadOpComments() {
        Comment com = new Comment(1, "Purple", "Observation");

        List<OperationComment> opComs = List.of(
                new OperationComment(1, com, 1, 1, 1, 1),
                new OperationComment(2, com, 1, 2, 2, 2),
                new OperationComment(3, com, 2, 3, 3, 3)
        );
        when(mockOpCommentRepo.findAllByOperationIdIn(any())).then(invocation -> {
            Collection<Integer> opIds = invocation.getArgument(0);
            return opComs.stream().filter(oc -> opIds.contains(oc.getOperationId())).collect(toList());
        });

        assertEquals(Map.of(1, opComs.subList(0,2), 2, opComs.subList(2,3)), service.loadOpComments(Set.of(1,2,3)));
        assertEquals(Map.of(), service.loadOpComments(Set.of(3,4)));
    }

    @Test
    public void testLoadOpMeasurements() {
        Measurement[] measurements = {
                new Measurement(1, "Thickness", "10", 1, 10, 100),
                new Measurement(2, "Thickness", "20", 2, 20, 200),
                new Measurement(3, "Blueing", "300", 3, 20, 300),
        };
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).then(invocation -> {
            Collection<Integer> opIds = invocation.getArgument(0);
            return Arrays.stream(measurements).filter(m -> opIds.contains(m.getOperationId())).collect(toList());
        });
        assertEquals(Map.of(), service.loadOpMeasurements(Set.of(1,2)));
        assertEquals(Map.of(20, List.of(measurements[1], measurements[2])), service.loadOpMeasurements(Set.of(1,20)));
    }

    @Test
    public void testLoadOpLabwareNotes() {
        LabwareNote[] notes = {
                new LabwareNote(1, 100, 10, "Alpha", "Beta"),
                new LabwareNote(2, 200, 10, "Gamma", "Delta"),
                new LabwareNote(3, 200, 20, "Epsilon", "Zeta"),
        };
        when(mockLwNoteRepo.findAllByOperationIdIn(any())).then(invocation -> {
            Collection<Integer> opIds = invocation.getArgument(0);
            return Arrays.stream(notes).filter(n -> opIds.contains(n.getOperationId())).collect(toList());
        });
        assertEquals(Map.of(), service.loadOpLabwareNotes(Set.of(1,2)));
        assertEquals(Map.of(10, List.of(notes[0], notes[1])), service.loadOpLabwareNotes(Set.of(1,10)));
    }

    @Test
    public void testLoadOpProbes() {
        ProbePanel p1 = new ProbePanel(1, "probe1");
        ProbePanel p2 = new ProbePanel(2, "probe2");
        LabwareProbe[] lwps = {
                new LabwareProbe(1, p1, 10, 100, "LOT1", 1),
                new LabwareProbe(2, p2, 10, 200, "LOT2", 2),
                new LabwareProbe(3, p1, 20, 200, "LOT3", 3)
        };
        when(mockLwProbeRepo.findAllByOperationIdIn(any())).then(invocation -> {
            Collection<Integer> opIds = invocation.getArgument(0);
            return Arrays.stream(lwps).filter(p -> opIds.contains(p.getOperationId())).collect(toList());
        });
        OperationType probify = EntityFactory.makeOperationType("probify", null, OperationTypeFlag.PROBES, OperationTypeFlag.IN_PLACE);
        OperationType stir = EntityFactory.makeOperationType("stir", null, OperationTypeFlag.IN_PLACE);
        Operation[] ops = {new Operation(), new Operation(), new Operation()};
        ops[0].setId(1);
        ops[1].setId(10);
        ops[2].setId(20);
        ops[0].setOperationType(stir);
        ops[1].setOperationType(probify);
        ops[2].setOperationType(probify);

        assertEquals(Map.of(), service.loadOpProbes(List.of(ops[0])));
        verifyNoInteractions(mockLwProbeRepo);
        assertEquals(Map.of(10, List.of(lwps[0], lwps[1])), service.loadOpProbes(Arrays.asList(ops).subList(0,2)));
        verify(mockLwProbeRepo).findAllByOperationIdIn(List.of(10));
        assertEquals(Map.of(10, List.of(lwps[0], lwps[1]), 20, List.of(lwps[2])), service.loadOpProbes(Arrays.asList(ops)));
        verify(mockLwProbeRepo).findAllByOperationIdIn(List.of(10,20));
    }

    @Test
    public void testLoadOpRois() {
        List<Roi> rois = List.of(
                new Roi(1, 11, 21, "roi1"),
                new Roi(2, 12, 21, "roi2"),
                new Roi(3, 13, 22, "roi3")
        );
        List<Integer> opIds = List.of(21,22);
        when(mockRoiRepo.findAllByOperationIdIn(opIds)).thenReturn(rois);
        var map = service.loadOpRois(opIds);
        assertThat(map).hasSize(2);
        assertThat(map.get(21)).containsExactlyElementsOf(rois.subList(0,2));
        assertThat(map.get(22)).containsExactly(rois.get(2));
    }

    @Test
    public void testGetLabwareProbeDetails() {
        LabwareProbe lwp = new LabwareProbe(1, new ProbePanel(1, "probe1"), 5, 6, "LOT1", 21);
        assertThat(service.getLabwareProbeDetails(lwp)).containsExactly(
                "Probe panel: probe1",
                "Lot: LOT1",
                "Plex: 21"
        );
    }

    @Test
    public void testLoadOpResults() {
        OperationType resultOpType = EntityFactory.makeOperationType("Record result", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        OperationType otherOpType = EntityFactory.makeOperationType("Splunge", null, OperationTypeFlag.IN_PLACE);
        List<Operation> ops = List.of(
                new Operation(1, otherOpType, null, null, null),
                new Operation(2, resultOpType, null, null, null),
                new Operation(3, resultOpType, null, null, null)
        );
        assertThat(service.loadOpResults(ops.subList(0,1))).isEmpty();
        verifyNoInteractions(mockResultOpRepo);

        List<ResultOp> results = List.of(
                new ResultOp(10, PassFail.pass, 2, 20, 30, 40),
                new ResultOp(11, PassFail.fail, 2, 21, 31, 40),
                new ResultOp(12, PassFail.pass, 3, 22, 32, 50)
        );
        when(mockResultOpRepo.findAllByOperationIdIn(List.of(2,3))).thenReturn(results);

        var expected = Map.of(2, results.subList(0,2), 3, results.subList(2,3));
        assertEquals(expected, service.loadOpResults(ops));
    }

    @ParameterizedTest
    @CsvSource(value={
            "pass, A1, A1: pass",
            "fail, B3, B3: fail",
            "pass,,pass"
    })
    public void testResultDetail(PassFail res, Address address, String expected) {
        final int slotId = 7;
        Slot slot = (address==null ? null : new Slot(slotId, 2, address, null, null, null));
        Map<Integer, Slot> slotIdMap = (slot==null ? Map.of() : Map.of(slotId, slot));
        ResultOp result = new ResultOp();
        result.setResult(res);
        result.setSlotId(slotId);

        assertEquals(expected, service.resultDetail(result, slotIdMap));
    }

    @ParameterizedTest
    @CsvSource(value={
            "bananas, bananas",
            "51, 51\u00a0sec",
            "60, 1\u00a0min",
            "3333, 55\u00a0min 33\u00a0sec",
            "7200, 2\u00a0hour",
            "7320, 2\u00a0hour 2\u00a0min",
            "7205, 2\u00a0hour 0\u00a0min 5\u00a0sec",
            "9876, 2\u00a0hour 44\u00a0min 36\u00a0sec",
    })
    public void testDescribeSeconds(String value, String expected) {
        assertEquals(expected, service.describeSeconds(value));
    }

    @Test
    public void testRoiDetail() {
        final int slotId = 4;
        Slot slot = new Slot();
        slot.setAddress(new Address(2,3));
        Roi roi = new Roi(slotId, 6, 7, "roi1");
        String detail = service.roiDetail(roi, Map.of(slotId, slot));
        assertEquals("ROI (6, B3): roi1", detail);
    }

    @ParameterizedTest
    @CsvSource(value={
            "Thickness, 14,, Thickness: 14\u00a0μm",
            "Thickness, 11, B3, B3: Thickness: 11\u00a0μm",
            "Blueing, 902,, Blueing: 15\u00a0min 2\u00a0sec",
            "Blueing, 75, D9, D9: Blueing: 1\u00a0min 15\u00a0sec",
    })
    public void testMeasurementDetail(String name, String value, Address address, String expected) {
        Map<Integer, Slot> slotIdMap = null;
        Integer slotId = null;
        if (address != null) {
            slotId = 70;
            Slot slot = new Slot(slotId, 7, address, null, null, null);
            slotIdMap = Map.of(slotId, slot);
        }
        Measurement measurement = new Measurement(6, name, value, 4, 5, slotId);
        assertEquals(expected, service.measurementDetail(measurement, slotIdMap));
    }

    @Test
    public void testLabwareNoteDetail() {
        LabwareNote note = new LabwareNote(1, 100, 10, "Alpha", "Beta");
        assertEquals("Alpha: Beta", service.labwareNoteDetail(note));
    }

    @ParameterizedTest
    @MethodSource("doesCommentApplyData")
    public void testDoesCommentApply(OperationComment opCom, int sampleId, int labwareId, Slot slot, boolean expected) {
        Map<Integer, Slot> slotIdMap = (slot==null ? Map.of() : Map.of(slot.getId(), slot));
        assertEquals(expected, service.doesCommentApply(opCom, sampleId, labwareId, slotIdMap));
    }

    static Stream<Arguments> doesCommentApplyData() {
        Slot slot = new Slot(100, 10, new Address(1,1), null, null, null);
        Comment com = new Comment(1, "Alabama", "Bananas");
        return Stream.of(
                Arguments.of(new OperationComment(1, com, 1, null, null, null), 1, 10, null, true),
                Arguments.of(new OperationComment(1, com, 1, 1, 100, 10), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, 1, 100, null), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, null, 100, null), 1, 10, slot, true),
                Arguments.of(new OperationComment(1, com, 1, 1, null, null), 1, 10, null, true),

                Arguments.of(new OperationComment(1, com, 1, 2, null, null), 1, 10, null, false), // wrong sample id
                Arguments.of(new OperationComment(1, com, 1, null, 200, null), 1, 10, slot, false), // wrong slot id
                Arguments.of(new OperationComment(1, com, 1, null, 100, null), 1, 20, slot, false), // slot belongs to wrong labware
                Arguments.of(new OperationComment(1, com, 1, null, null, 20), 1, 10, null, false) // wrong labware id
        );
    }

    @ParameterizedTest
    @CsvSource({
            "11,30,30,1,true",
            "11,30,30,2,false",
            "11,30,40,1,false",
    })
    public void testDoesRoiApply(int roiSlotId, int roiSampleId, int sampleId, int labwareId, boolean expected) {
        Roi roi = new Roi(roiSlotId, roiSampleId, 100, "roi1");
        Slot slot = new Slot();
        slot.setId(roiSlotId);
        slot.setLabwareId(roiSlotId/10);
        Map<Integer, Slot> slotIdMap = Map.of(roiSlotId, slot);
        assertEquals(expected, service.doesRoiApply(roi, sampleId, labwareId, slotIdMap));
    }

    @ParameterizedTest
    @MethodSource("doesMeasurementApplyData")
    public void testDoesMeasurementApply(Measurement measurement, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap, boolean expected) {
        assertEquals(expected, service.doesMeasurementApply(measurement, sampleId, labwareId, slotIdMap));
    }

    static Stream<Arguments> doesMeasurementApplyData() {
        final int sampleId = 4;
        final int slotId = 10;
        final int lwId = 1;
        Map<Integer, Slot> slotIdMap = Map.of(slotId, new Slot(slotId, lwId, new Address(1,2), null, null, null));
        return Arrays.stream(new Object[][] {
                {new Measurement(1, "A", "1", null, 1, null), sampleId, lwId, slotIdMap, true},
                {new Measurement(1, "A", "1", sampleId, 1, null), sampleId, lwId, slotIdMap, true},
                {new Measurement(1, "A", "1", null, 1, slotId), sampleId, lwId, slotIdMap, true},
                {new Measurement(1, "A", "1", sampleId, 1, slotId), sampleId, lwId, slotIdMap, true},

                {new Measurement(1, "A", "1", 5, 1, slotId), sampleId, lwId, slotIdMap, false},
                {new Measurement(1, "A", "1", sampleId, 1, 800), sampleId, lwId, slotIdMap, false},
                {new Measurement(1, "A", "1", sampleId, 1, slotId), sampleId, 2, slotIdMap, false},
                {new Measurement(1, "A", "1", null, 1, 800), sampleId, lwId, slotIdMap, false},
                {new Measurement(1, "A", "1", 5, 1, null), sampleId, lwId, slotIdMap, false},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource(value={
            "pass, 10, 200, 20, 10, 20, true",
            "fail, 10, 200, 20, 10, 20, true",
            "pass,,,,10,20,true",
            "pass, 11, 200, 20, 10, 20, false",
            "pass, 10, 200, 21, 10, 20, false",
    })
    public void testDoesResultApply(PassFail res, Integer resultSampleId, Integer resultSlotId, Integer slotLabwareId, int sampleId, int labwareId, boolean expected) {
        ResultOp result = new ResultOp(50, res, 1, resultSampleId, resultSlotId, -50);
        Map<Integer, Slot> slotIdMap;
        if (slotLabwareId!=null) {
            Slot slot = new Slot();
            slot.setId(resultSlotId);
            slot.setLabwareId(slotLabwareId);
            slotIdMap = Map.of(resultSlotId, slot);
        } else {
            slotIdMap = Map.of();
        }
        assertEquals(expected, service.doesResultApply(result, sampleId, labwareId, slotIdMap));
    }

    @Test
    public void testCreateEntriesForOps() {
        Comment[] coms = {
                new Comment(1, "Alabama", "Bananas"),
                new Comment(2, "Alaska", "Bananas"),
                new Comment(3, "Arizona", "Bananas"),
                new Comment(3, "Arkansas", "Bananas"),
        };
        createOps();
        ops.get(0).setEquipment(new Equipment("Feeniks", "scanner"));
        int[] opIds = ops.stream().mapToInt(Operation::getId).toArray();
        final Set<Integer> opIdSet = Set.of(opIds[0], opIds[1]);
        LabwareProbe lwp = new LabwareProbe(1, new ProbePanel("probe1"), opIds[1], labware[3].getId(), "LOT1", 5);

        StainType st1 = new StainType(1, "Coffee");
        StainType st2 = new StainType(2, "Blood");
        Map<Integer, List<StainType>> opStainTypes = Map.of(
                opIds[1], List.of(st1, st2)
        );
        doReturn(opStainTypes).when(mockStainTypeRepo).loadOperationStainTypes(any());

        Map<Integer, List<ReagentActionDetail>> radMap = Map.of(opIds[0],
                List.of(new ReagentActionDetail("123", "type1", new Address(1,2), new Address(2,3), labware[1].getId(), null),
                        new ReagentActionDetail("456", "type2", new Address(3,4), new Address(5,6), labware[1].getId(), null)
                ));
        when(mockRadService.loadReagentTransfers(any())).thenReturn(radMap);
        Map<Integer, Set<String>> opWork = Map.of(
                opIds[0], Set.of("SGP5000"),
                opIds[1], Set.of()
        );

        Map<Integer, List<ResultOp>> resultMap = Map.of(
                opIds[0], List.of(new ResultOp(50, PassFail.pass, opIds[0], 90, labware[1].getFirstSlot().getId(), -50))
        );
        doReturn(resultMap).when(service).loadOpResults(any());

        OperationComment[] opComs = {
                new OperationComment(1, coms[0], opIds[0], null, null, null),
                new OperationComment(2, coms[1], opIds[0], null, labware[1].getFirstSlot().getId(), null),
                new OperationComment(3, coms[2], opIds[1], null, null, null),
                new OperationComment(4, coms[3], opIds[1], 400, null, null),
        };

        Map<Integer, List<Measurement>> opMeasurements = Map.of(
                opIds[0], List.of(new Measurement(1, "Thickness", "4", null, opIds[0], null))
        );

        doReturn(opMeasurements).when(service).loadOpMeasurements(any());

        Map<Integer, List<Roi>> opRois = Map.of(
                opIds[0], List.of(new Roi(labware[1].getFirstSlot().getId(), samples[0].getId(), opIds[0], "roi1"))
        );

        doReturn(opRois).when(service).loadOpRois(any());

        Map<Integer, List<OperationComment>> opComMap = Map.of(
                opIds[0], List.of(opComs[0], opComs[1]),
                opIds[1], List.of(opComs[2], opComs[3])
        );

        doReturn(opComMap).when(service).loadOpComments(opIdSet);

        LabwareNote[] lwNotes = {
                new LabwareNote(1, labware[1].getId(), opIds[0], "Alpha", "Beta"),
                new LabwareNote(2, labware[1].getId(), opIds[0], "Gamma", "Delta"),
                new LabwareNote(3, labware[3].getId(), opIds[1], "Epsilon", "Zeta"),
        };

        Map<Integer, List<LabwareNote>> opNotes = Map.of(
                opIds[0], List.of(lwNotes[0], lwNotes[1]),
                opIds[1], List.of(lwNotes[2])
        );
        doReturn(opNotes).when(service).loadOpLabwareNotes(opIdSet);

        Map<Integer, List<LabwareProbe>> opProbes = Map.of(opIds[1], List.of(lwp));
        doReturn(opProbes).when(service).loadOpProbes(ops);

        // Letting doesCommentApply actually run is easier than mocking it to return what it would return anyway

        List<Labware> labwareList = Arrays.asList(this.labware);

        Set<Integer> sampleIds = Set.of(samples[0].getId(), samples[2].getId());

        String opTypeName0 = ops.get(0).getOperationType().getName();
        String opTypeName1 = ops.get(1).getOperationType().getName();
        String username = getUser().getUsername();
        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(opIds[0], opTypeName0, ops.get(0).getPerformed(), labware[0].getId(),
                        labware[1].getId(), samples[0].getId(), username, "SGP5000",
                        List.of("123 : A2 -> B3", "456 : C4 -> E6", "Alpha: Beta", "Gamma: Delta", "Equipment: Feeniks",
                                "A1: pass", "Alabama", "A1: Alaska", "Thickness: 4\u00a0μm", "ROI (90, A1): roi1"), "A1", null),
                new HistoryEntry(opIds[1], opTypeName1, ops.get(1).getPerformed(), labware[0].getId(),
                        labware[3].getId(), samples[2].getId(), username, null,
                        List.of("Stain type: Coffee, Blood", "Epsilon: Zeta", "Probe panel: probe1", "Lot: LOT1", "Plex: 5", "Arizona"), "A1", null)
        );
        assertThat(service.createEntriesForOps(ops, sampleIds, labwareList, opWork, null)).containsExactlyElementsOf(expectedEntries);

        verify(service).loadOpMeasurements(opIdSet);
        verify(mockStainTypeRepo).loadOperationStainTypes(opIdSet);
        verify(mockRadService).loadReagentTransfers(opIdSet);
    }

    @ParameterizedTest
    @MethodSource("slotDestinationAddressAndRegion")
    public void testCreateEntriesForOps_forDestinationSlotAddressAndRegions(int sampleIndex1,  int sampleIndex2,
                                                                            Address address1,Address address2,
                                                                            String region1, String region2,
                                                                            String expectedAddress1, String  expectedAddress2,
                                                                            String expectedRegion1, String expectedRegion2) {
        createLabware();
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        Action action1 = new Action(1, 100, labware[0].getSlot(A1), labware[3].getSlot(address1), samples[sampleIndex1], samples[0]);
        Action action2 = new Action(2, 100, labware[0].getSlot(B1), labware[3].getSlot(address2), samples[sampleIndex2], samples[0]);
        Operation op1 = new Operation(100, EntityFactory.makeOperationType("Type 1", null), makeTime(0), List.of(action1, action2), getUser(), null);

        op1.setActions(List.of(action1, action2));
        doReturn(List.of(
                new SamplePositionResult(labware[3].getSlot(address1), samples[sampleIndex1].getId(), region1, op1.getId()),
                new SamplePositionResult(labware[3].getSlot(address2), samples[sampleIndex2].getId(), region2, op1.getId())))
                .when(mockSlotRegionService).loadSamplePositionResultsForLabware(List.of(labware));

        List<HistoryEntry> expectedEntries = new ArrayList<>(List.of(
                new HistoryEntry(op1.getId(), op1.getOperationType().getName(), op1.getPerformed(), labware[0].getId(),
                        labware[3].getId(), samples[sampleIndex1].getId(), getUser().getUsername(), null,
                        null, expectedAddress1, expectedRegion1)

        ));
        if(expectedAddress2 != null) {
            expectedEntries.add(new HistoryEntry(op1.getId(), op1.getOperationType().getName(), op1.getPerformed(), labware[0].getId(),
                    labware[3].getId(), samples[sampleIndex2].getId(), getUser().getUsername(), null,
                    null, expectedAddress2, expectedRegion2));

        }


        assertThat(service.createEntriesForOps(List.of(op1), Set.of(samples[0].getId(), samples[2].getId()), Arrays.asList(labware), Map.of(
                op1.getId(), Set.of()
        ), null)).containsExactlyElementsOf(expectedEntries);

    }

    static Stream<Arguments> slotDestinationAddressAndRegion() {
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);

        return Arrays.stream(new Object[][] {
                {0, 2, B1, A1, "Left", "Right", "B1", "A1", "Left", "Right"},
                {0, 2, B1, B1, "Top Right", "Top Bottom", "B1", "B1", "Top Right", "Top Bottom"},
                {2, 2, B1, A1, "", "", "B1, A1", null, "", ""},
                {2, 2, B1, A1, "Top Right", "", "B1", "A1", "Top Right", ""},

        }).map(Arguments::of);
    }

    @Test
    public void testCreateEntriesForOpsForWorkNumber() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw = EntityFactory.makeLabware(lt, sample, sample);
        doReturn(Map.of()).when(service).loadOpComments(any());
        doReturn(Map.of()).when(service).loadOpMeasurements(any());
        doReturn(Map.of()).when(service).loadOpLabwareNotes(any());
        doReturn(Map.of()).when(service).loadOpResults(any());
        OperationType opType = EntityFactory.makeOperationType("Boil", null);
        User user = EntityFactory.getUser();
        Operation op = EntityFactory.makeOpForSlots(opType, lw.getSlots(), lw.getSlots(), user);
        String workNumber = "SGP42";
        List<HistoryEntry> entries = service.createEntriesForOps(List.of(op), null, List.of(lw), null, workNumber);
        assertThat(entries).hasSize(1);
        HistoryEntry entry = entries.get(0);
        assertEquals(new HistoryEntry(op.getId(), opType.getName(), op.getPerformed(), lw.getId(), lw.getId(),
                sample.getId(), user.getUsername(), workNumber, null, "A1, A2", null), entry);
    }

    private static LocalDateTime makeTime(int n) {
        return LocalDateTime.of(2021,7,1+n, 2+n, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCreateEntriesForReleases(boolean withSingleWork) {
        createSamples();
        Map<Integer, String> releaseWorkMap;
        String singleWorkNumber;
        if (withSingleWork) {
            releaseWorkMap = null;
            singleWorkNumber = "SGP11";
        } else {
            releaseWorkMap = Map.of(1, "SGP12");
            singleWorkNumber = null;
        }
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples[0], samples[0], samples[1], samples[2]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[0], samples[1]);
        Snapshot snap = new Snapshot(1, lw1.getId(),
                lw1.getSlots().stream().flatMap(slot -> slot.getSamples().stream()
                            .map(sample -> new SnapshotElement(null, 1, slot.getId(), sample.getId()))
                ).collect(toList()));
        User user = getUser();
        Release rel1 = new Release(1, lw1, user, new ReleaseDestination(1, "Mercury"),
                new ReleaseRecipient(2, "jeff"), snap.getId(), makeTime(1));
        Release rel2 = new Release(2, lw2, user, new ReleaseDestination(3, "Venus"),
                new ReleaseRecipient(4, "dirk"), null, makeTime(2));
        String username = user.getUsername();

        when(mockSnapshotRepo.findAllByIdIn(Set.of(1))).thenReturn(List.of(snap));
        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(1, "Release", rel1.getReleased(), lw1.getId(), lw1.getId(), samples[0].getId(),
                        username, null, List.of("Destination: Mercury", "Recipient: jeff")),
                new HistoryEntry(1, "Release", rel1.getReleased(), lw1.getId(), lw1.getId(), samples[2].getId(),
                        username, null, List.of("Destination: Mercury", "Recipient: jeff")),
                new HistoryEntry(2, "Release", rel2.getReleased(), lw2.getId(), lw2.getId(), null,
                        username, null, List.of("Destination: Venus", "Recipient: dirk"))
        );
        if (withSingleWork) {
            expectedEntries.forEach(e -> e.setWorkNumber(singleWorkNumber));
        } else {
            expectedEntries.forEach(e -> e.setWorkNumber(releaseWorkMap.get(e.getEventId())));
        }
        assertThat(service.createEntriesForReleases(List.of(rel1, rel2), Set.of(samples[0].getId(), samples[2].getId()),
                releaseWorkMap, singleWorkNumber))
                .containsExactlyElementsOf(expectedEntries);
    }

    @Test
    public void testCreateEntriesForDestructions() {
        createSamples();
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples[0], samples[0], samples[1], samples[2]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[0], samples[1]);
        User user = getUser();
        Destruction d1 = new Destruction(1, lw1, user, makeTime(1),
                new DestructionReason(1, "Dropped."));
        Destruction d2 = new Destruction(2, lw2, user, makeTime(2),
                new DestructionReason(2, "Sat on."));
        String username = user.getUsername();

        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(1, "Destruction", d1.getDestroyed(), lw1.getId(), lw1.getId(), samples[0].getId(),
                        username, null, List.of("Reason: Dropped.")),
                new HistoryEntry(1, "Destruction", d1.getDestroyed(), lw1.getId(), lw1.getId(), samples[2].getId(),
                        username, null, List.of("Reason: Dropped.")),
                new HistoryEntry(2, "Destruction", d2.getDestroyed(), lw2.getId(), lw2.getId(), samples[0].getId(),
                        username, null, List.of("Reason: Sat on."))
        );

        assertThat(service.createEntriesForDestructions(List.of(d1, d2), Set.of(samples[0].getId(), samples[2].getId())))
                .containsExactlyElementsOf(expectedEntries);
    }


    @Test
    public void testAssembleEntries() {
        List<HistoryEntry> e1 = List.of(
                new HistoryEntry(1, "Register", makeTime(0), 1, 1, 1, "user1", null),
                new HistoryEntry(2, "Section", makeTime(2), 1, 2, 2, "user2", null),
                new HistoryEntry(3, "Section", makeTime(4), 1, 3, 3, "user3", null),
                new HistoryEntry(4, "Scoop", makeTime(6), 3, 4, 3, "user4", null)
        );
        List<HistoryEntry> e2 = List.of(
                new HistoryEntry(1, "Release", makeTime(3), 2, 2, 2, "user11", null),
                new HistoryEntry(2, "Release", makeTime(8), 3, 3, 3, "user12", null)
        );
        List<HistoryEntry> e3 = List.of(
                new HistoryEntry(1, "Destruction", makeTime(5), 1, 1, 1, "user21", null),
                new HistoryEntry(2, "Destruction", makeTime(9), 4, 4, 3, "user22", null)
        );

        List<HistoryEntry> expectedEntries = List.of(
                e1.get(0), e1.get(1), e2.get(0), e1.get(2), e3.get(0), e1.get(3), e2.get(1), e3.get(1)
        );

        assertEquals(expectedEntries, service.assembleEntries(List.of(e1, e2, e3)));
    }
}
