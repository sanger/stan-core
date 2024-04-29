package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.SlotRegionService;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.stan.service.history.HistoryServiceImp.EventTypeFilter;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.genericMock;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Tests {@link HistoryServiceImp}
 * @author dr6
 */
public class TestHistoryService {
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private SampleRepo mockSampleRepo;
    @Mock
    private TissueRepo mockTissueRepo;
    @Mock
    private DonorRepo mockDonorRepo;
    @Mock
    private ReleaseRepo mockReleaseRepo;
    @Mock
    private DestructionRepo mockDestructionRepo;
    @Mock
    private OperationCommentRepo mockOpCommentRepo;
    @Mock
    private RoiRepo mockRoiRepo;
    @Mock
    private SnapshotRepo mockSnapshotRepo;
    @Mock
    private WorkRepo mockWorkRepo;
    @Mock
    private MeasurementRepo mockMeasurementRepo;
    @Mock
    private LabwareNoteRepo mockLwNoteRepo;
    @Mock
    private ResultOpRepo mockResultOpRepo;
    @Mock
    private StainTypeRepo mockStainTypeRepo;
    @Mock
    private LabwareProbeRepo mockLwProbeRepo;
    @Mock
    private LabwareFlagRepo mockFlagRepo;
    @Mock
    private OperationSolutionRepo mockOpSolRepo;
    @Mock
    private SolutionRepo mockSolutionRepo;
    @Mock
    private ReagentActionDetailService mockRadService;
    @Mock
    private SlotRegionService mockSlotRegionService;
    @Mock
    private FlagLookupService mockFlagLookupService;

    private HistoryServiceImp service;

    private AutoCloseable mocking;

    private Sample[] samples;
    private Labware[] labware;
    private List<Operation> ops;

    @BeforeEach
    public void setup() {
        mocking = MockitoAnnotations.openMocks(this);

        service = spy(new HistoryServiceImp(mockOpRepo, mockOpTypeRepo, mockLwRepo, mockSampleRepo, mockTissueRepo, mockDonorRepo,
                mockReleaseRepo, mockDestructionRepo, mockOpCommentRepo, mockRoiRepo, mockSnapshotRepo, mockWorkRepo,
                mockMeasurementRepo, mockLwNoteRepo, mockResultOpRepo, mockStainTypeRepo, mockLwProbeRepo,
                mockFlagRepo, mockOpSolRepo, mockSolutionRepo,
                mockRadService, mockSlotRegionService, mockFlagLookupService));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
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
        final Set<Integer> opIds = Set.of(20, 21);
        work.setOperationIds(opIds);
        final Set<Integer> releaseIds = Set.of(30,31);
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
        List<String> flaggedBarcodes = List.of("alpha", "beta");

        List<Sample> samples = List.of(sam1,sam2);
        List<Labware> allLabware = BasicUtils.concat(lws, List.of(rlw1, rlw2));
        doReturn(samples).when(service).referencedSamples(sameElements(entries, true), sameElements(allLabware, true));
        doReturn(flaggedBarcodes).when(service).loadFlaggedBarcodes(allLabware);

        History history = service.getHistoryForWorkNumber(workNumber);
        assertEquals(entries, history.getEntries());
        assertEquals(samples, history.getSamples());
        assertEquals(allLabware, history.getLabware());
        assertEquals(flaggedBarcodes, history.getFlaggedBarcodes());
    }

    @ParameterizedTest
    @CsvSource({"true,false,false,",
            "false,true,false,",
            "false,false,true,",
            "false,false,true,Baking",
    })
    public void testGetHistoryForWorkNumber_withEventTypeFilter(boolean includeReleases, boolean includeDestroys,
                                                                boolean includeOps, String opTypeName) {
        OperationType requiredOpType = (opTypeName==null ? null : EntityFactory.makeOperationType(opTypeName, null));
        EventTypeFilter etFilter = new EventTypeFilter(includeReleases, includeDestroys, includeOps, requiredOpType);
        String workNumber = "SGP1";
        Work work = EntityFactory.makeWork(workNumber);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        work.setOperationIds(includeOps ? Set.of(2, 3, 4) : Set.of());
        Set<Integer> opLwIds = includeOps ? Set.of(10,11) : Set.of();
        doReturn(opLwIds).when(service).labwareIdsFromOps(any());
        LabwareType lt = EntityFactory.getTubeType();
        List<Operation> workOps;
        if (requiredOpType!=null) {
            OperationType otherOpType = EntityFactory.makeOperationType("other", null);
            Operation op1 = new Operation(1, otherOpType, null, null, null);
            Operation op2 = new Operation(2, requiredOpType, null, null, null);
            workOps = List.of(op1, op2);
            when(mockOpRepo.findAllById(work.getOperationIds())).thenReturn(workOps);
        } else if (includeOps) {
            OperationType opType = EntityFactory.makeOperationType("opname", null);
            workOps = Stream.of(1,2).map(i -> new Operation(i, opType, null, null, null))
                    .collect(toList());
            when(mockOpRepo.findAllById(work.getOperationIds())).thenReturn(workOps);
        } else {
            workOps = List.of();
        }

        List<Labware> opLw = (includeOps ? List.of(new Labware(2, "STAN-2", lt, null)) : List.of());
        doReturn(opLw).when(mockLwRepo).findAllByIdIn(opLwIds);

        work.setReleaseIds(includeReleases ? Set.of(5,6,7) : Set.of());
        List<Release> releases;
        List<Labware> releaseLw;
        if (includeReleases) {
            Labware lw1 = EntityFactory.getTube();
            Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
            releaseLw = List.of(lw1, lw2);
            releases = List.of(new Release(), new Release());
            for (int i = 0; i < releaseLw.size(); ++i) {
                releases.get(i).setLabware(releaseLw.get(i));
            }
        } else {
            releaseLw = List.of();
            releases = List.of();
        }

        doReturn(releases).when(mockReleaseRepo).findAllByIdIn(work.getReleaseIds());

        List<Sample> samples = includeOps || includeReleases ? List.of(EntityFactory.getSample()) : List.of();
        doReturn(samples).when(service).referencedSamples(any(), any());

        List<HistoryEntry> opEntries = includeOps ? new ArrayList<>(List.of(entryAtTime(1))) : new ArrayList<>();
        List<HistoryEntry> releaseEntries = includeReleases ? new ArrayList<>(List.of(entryAtTime(2))) : new ArrayList<>();

        doReturn(opEntries).when(service).createEntriesForOps(any(), any(), any(), any(), any());
        doReturn(releaseEntries).when(service).createEntriesForReleases(any(), any(), any(), any());

        List<HistoryEntry> allEntries = new ArrayList<>(opEntries.size() + releaseEntries.size());
        allEntries.addAll(opEntries);
        allEntries.addAll(releaseEntries);

        List<Labware> allLabware = BasicUtils.concat(opLw, releaseLw);
        assertEquals(new History(allEntries, samples, allLabware), service.getHistoryForWorkNumber(workNumber, etFilter));

        if (includeOps) {
            List<Operation> relevantOps = requiredOpType==null ? workOps : workOps.subList(1,2);
            verify(service).labwareIdsFromOps(relevantOps);
            verify(service).createEntriesForOps(relevantOps, null, opLw, null, work.getWorkNumber());
        }
        if (includeReleases) {
            verify(service).createEntriesForReleases(releases, null, null, work.getWorkNumber());
        }
        if (!samples.isEmpty()) {
            verify(service).referencedSamples(allEntries, allLabware);
        }
    }

    @Test
    public void testGetHistoryForWorkNumber_noOps() {
        Work work = new Work(10, "SGP10", null, null, null, null, null, Work.Status.active);
        work.setOperationIds(Set.of());
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
            "by event type,,,,,myevent",
            "by work number,SGP5,,,,",
            "by barcode,SGP5,STAN-1,EXT1,DONOR1,",
            "by tissues,SGP5,,EXT1,DONOR1,",
    })
    public void testGetHistory(String mode, String workNumber, String barcode, String externalName, String donorName,
                               String eventType) {
        List<Sample> samples = List.of(EntityFactory.getSample());
        History history = new History(null, samples, null);
        EventTypeFilter etFilter = mock(EventTypeFilter.class);
        doReturn(etFilter).when(service).eventTypeFilter(eventType);
        List<String> externalNames = (externalName==null ? null : List.of(externalName));
        List<String> donorNames = (donorName==null ? null : List.of(donorName));
        if (mode.equalsIgnoreCase("by work number")) {
            doReturn(history).when(service).getHistoryForWorkNumber(workNumber, etFilter);
        } else if (mode.equalsIgnoreCase("by event type")) {
            doReturn(history).when(service).getHistoryForEventType(eventType);
        } else {
            if (mode.equalsIgnoreCase("by barcode")) {
                doReturn(samples).when(service).samplesForBarcode(barcode, externalNames, donorNames);
            } else {
                doReturn(samples).when(service).samplesForTissues(externalNames, donorNames);
            }
            doReturn(history).when(service).getHistoryForSamples(samples, workNumber, etFilter);
        }

        assertSame(history, service.getHistory(workNumber, barcode, externalNames, donorNames, eventType));
    }

    @ParameterizedTest
    @ValueSource(strings={"release", "destruction", "baking", "unicorn"})
    public void testGetHistoryForEventType(String eventTypeName) {
        History history = new History(null, List.of(EntityFactory.getSample()), null);
        boolean expectException = false;
        if (eventTypeName.equalsIgnoreCase("release")) {
            doReturn(history).when(service).getHistoryOfReleases();
        } else if (eventTypeName.equalsIgnoreCase("destruction")) {
            doReturn(history).when(service).getHistoryOfDestructions();
        } else if (eventTypeName.equalsIgnoreCase("unicorn")) {
            doThrow(EntityNotFoundException.class).when(mockOpTypeRepo).getByName(eventTypeName);
            expectException = true;
        } else {
            OperationType opType = EntityFactory.makeOperationType("Baking", null);
            doReturn(opType).when(mockOpTypeRepo).getByName(eventTypeName);
            doReturn(history).when(service).getHistoryForOpType(opType);
        }

        List<String> flaggedBarcodes = List.of("alpha", "beta");
        doReturn(flaggedBarcodes).when(service).loadFlaggedBarcodes(history.getLabware());

        if (expectException) {
            assertThrows(EntityNotFoundException.class, () -> service.getHistoryForEventType(eventTypeName));
        } else {
            assertSame(history, service.getHistoryForEventType(eventTypeName));
            assertEquals(flaggedBarcodes, history.getFlaggedBarcodes());
        }
    }


    private static HistoryEntry entryAtTime(int day) {
        LocalDateTime time = LocalDateTime.of(2023,1,day,12,0);
        HistoryEntry entry = new HistoryEntry();
        entry.setTime(time);
        return entry;
    }

    @Test
    public void testGetHistoryOfReleases() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        List<Labware> labware = List.of(lw1, lw2);
        List<Release> releases = labware.stream()
                .map(lw -> {
                    Release rel = new Release();
                    rel.setLabware(lw);
                    return rel;
                }).collect(toList());
        when(mockReleaseRepo.findAll()).thenReturn(releases);

        List<HistoryEntry> entries = new ArrayList<>(Arrays.asList(entryAtTime(1), entryAtTime(2)));
        doReturn(entries).when(service).createEntriesForReleases(releases, null, null, null);
        List<Sample> samples = List.of(EntityFactory.getSample());
        doReturn(samples).when(service).referencedSamples(entries, labware);
        assertEquals(new History(entries, samples, labware), service.getHistoryOfReleases());
    }

    @Test
    public void testGetHistoryOfReleases_none() {
        when(mockReleaseRepo.findAll()).thenReturn(List.of());
        assertEquals(new History(), service.getHistoryOfReleases());
    }

    @Test
    public void testGetHistoryOfDestructions() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        List<Labware> labware = List.of(lw1, lw2);
        List<Destruction> destructions = labware.stream()
                .map(lw -> {
                    Destruction d = new Destruction();
                    d.setLabware(lw);
                    return d;
                }).collect(toList());
        when(mockDestructionRepo.findAll()).thenReturn(destructions);

        List<HistoryEntry> entries = new ArrayList<>(Arrays.asList(entryAtTime(1), entryAtTime(2)));
        doReturn(entries).when(service).createEntriesForDestructions(destructions, null);
        List<Sample> samples = List.of(EntityFactory.getSample());
        doReturn(samples).when(service).referencedSamples(entries, labware);
        assertEquals(new History(entries, samples, labware), service.getHistoryOfDestructions());
    }

    @Test
    public void testGetHistoryForOpType() {
        OperationType opType = EntityFactory.makeOperationType("Baking", null);
        List<Operation> ops = List.of(new Operation(), new Operation());
        when(mockOpRepo.findAllByOperationType(opType)).thenReturn(ops);
        Set<Integer> lwIds = Set.of(4,5);
        doReturn(lwIds).when(service).labwareIdsFromOps(ops);
        List<Labware> labware = List.of(EntityFactory.getTube());
        when(mockLwRepo.findAllByIdIn(lwIds)).thenReturn(labware);
        List<HistoryEntry> entries = new ArrayList<>(Arrays.asList(entryAtTime(1), entryAtTime(2)));
        doReturn(entries).when(service).createEntriesForOps(ops, null, labware, null, null);
        List<Sample> samples = List.of(EntityFactory.getSample());
        doReturn(samples).when(service).referencedSamples(entries, labware);

        assertEquals(new History(entries, samples, labware), service.getHistoryForOpType(opType));
    }

    @Test
    public void testGetHistoryForOpType_none() {
        OperationType opType = EntityFactory.makeOperationType("Baking", null);
        when(mockOpRepo.findAllByOperationType(opType)).thenReturn(List.of());
        assertEquals(new History(), service.getHistoryForOpType(opType));
    }

    @Test
    public void testGetHistoryOfDestructions_none() {
        when(mockDestructionRepo.findAll()).thenReturn(List.of());
        assertEquals(new History(), service.getHistoryOfDestructions());
    }

    @Test
    public void testSamplesForBarcode() {
        String barcode = "STAN-1";
        List<String> externalNames = List.of("EXT1");
        List<String> donorNames = List.of("donor1");
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
        doReturn(filter).when(service).tissuePredicate(donorNames, externalNames);
        List<Sample> returnSamples = List.of(lwSamples[0], EntityFactory.getSample());
        when(mockSampleRepo.findAllByTissueIdIn(any())).thenReturn(returnSamples);

        assertSame(returnSamples, service.samplesForBarcode(barcode, externalNames, donorNames));
        verify(mockSampleRepo).findAllByTissueIdIn(Set.of(lwSamples[0].getTissue().getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "'',",
            ",''",
            "'', a b",
            "a b, ''",
    })
    public void testSamplesForTissues_empty(String xnJoined, String dnJoined) {
        List<String> externalNames = stringToList(xnJoined);
        List<String> donorNames = stringToList(dnJoined);
        assertThat(service.samplesForTissues(externalNames, donorNames)).isEmpty();
        verifyNoInteractions(mockTissueRepo);
        verifyNoInteractions(mockDonorRepo);
        verifyNoInteractions(mockSampleRepo);
    }

    @ParameterizedTest
    @CsvSource({
            "alpha beta, Alabama Alaska",
            "alpha beta,",
            ", Alabama Alaska",
            "alpha*,",
            "alpha*, Alabama",
            "alpha* beta, Alabama",
    })
    public void testSamplesForTissues(String xnJoined, String dnJoined) {
        List<String> externalNames = stringToList(xnJoined);
        List<String> donorNames = stringToList(dnJoined);
        Predicate<Tissue> donorTissuePredicate = null;
        List<Tissue> tissues;
        if (externalNames!=null) {
            tissues = IntStream.range(0, externalNames.size())
                    .mapToObj(i -> {
                        Tissue t = new Tissue();
                        t.setId(10+i);
                        return t;
                    })
                    .collect(toList());
            for (int i = 0; i < externalNames.size(); ++i) {
                String xn = externalNames.get(i);
                if (xn.indexOf('*') >= 0) {
                    when(mockTissueRepo.findAllByExternalNameLike(wildcardToLikeSql(xn))).thenReturn(List.of(tissues.get(i)));
                } else {
                    when(mockTissueRepo.getAllByExternalName(xn)).thenReturn(List.of(tissues.get(i)));
                }
            }
            if (donorNames!=null) {
                donorTissuePredicate = t -> t.getId()!=10;
                doReturn(donorTissuePredicate).when(service).donorNameTissuePredicate(donorNames);
            }
        } else {
            tissues = List.of(EntityFactory.getTissue());
            List<Donor> donors = List.of(new Donor(21, null, null, null), new Donor(22, null, null, null));
            when(mockDonorRepo.getAllByDonorNameIn(donorNames)).thenReturn(donors);
            when(mockTissueRepo.findAllByDonorIdIn(List.of(21,22))).thenReturn(tissues);
        }
        List<Integer> tissueIds;
        if (donorTissuePredicate!=null) {
            tissueIds = tissues.subList(1, tissues.size()).stream().map(Tissue::getId).collect(toList());
        } else {
            tissueIds = tissues.stream().map(Tissue::getId).collect(toList());
        }
        if (tissueIds.isEmpty()) {
            assertThat(service.samplesForTissues(externalNames, donorNames)).isEmpty();
            verifyNoInteractions(mockSampleRepo);
        } else {
            List<Sample> samples = List.of(EntityFactory.getSample());
            when(mockSampleRepo.findAllByTissueIdIn(any())).thenReturn(samples);
            assertSame(samples, service.samplesForTissues(externalNames, donorNames));
            verify(mockSampleRepo).findAllByTissueIdIn(tissueIds);
        }
    }

    /**
     * Splits a string by whitespace into a list.
     * Returns null if the input is null. Returns an empty list if the input is empty.
     * @param joinedString a string
     * @return list of substrings
     */
    private static List<String> stringToList(String joinedString) {
        if (joinedString==null) {
            return null;
        }
        if (joinedString.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(joinedString.split("\\s+"));
    }

    @ParameterizedTest
    @MethodSource("tissuePredicateAllFalseArgs")
    public void testTissuePredicate_allfalse(List<String> donorNames, List<String> externalNames) {
        doReturn(null).when(service).externalNameTissuePredicate(any());
        doReturn(null).when(service).donorNameTissuePredicate(any());
        Predicate<Tissue> predicate = service.tissuePredicate(donorNames, externalNames);
        assertNotNull(predicate);
        assertFalse(predicate.test(null));
        verify(service, never()).externalNameTissuePredicate(any());
        verify(service, never()).donorNameTissuePredicate(any());
    }

    static Stream<Arguments> tissuePredicateAllFalseArgs() {
        return Arrays.stream(new Object[][] {
                {List.of("Alpha"), List.of()},
                {List.of(), List.of("Beta")},
                {List.of(), null},
                {null, List.of()},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({"true,true",
            "true,false",
            "false,true",
            "false,false"
    })
    public void testTissuePredicate(boolean hasXnFilter, boolean hasDnFilter) {
        List<String> externalNames = List.of("xn");
        List<String> donorNames = List.of("dn");
        Predicate<Tissue> xnFilter, dnFilter, expectedFilter;
        xnFilter = hasXnFilter ? genericMock(Predicate.class) : null;
        dnFilter = hasDnFilter ? genericMock(Predicate.class) : null;
        if (hasXnFilter && hasDnFilter) {
            Predicate<Tissue> combinedFilter = genericMock(Predicate.class);
            when(xnFilter.and(dnFilter)).thenReturn(combinedFilter);
            expectedFilter = combinedFilter;
        } else {
            expectedFilter = coalesce(xnFilter, dnFilter);
        }
        doReturn(xnFilter).when(service).externalNameTissuePredicate(externalNames);
        doReturn(dnFilter).when(service).donorNameTissuePredicate(donorNames);
        assertSame(expectedFilter, service.tissuePredicate(donorNames, externalNames));
    }

    @ParameterizedTest
    @MethodSource("donorNameTissuePredicateArgs")
    public void testDonorNameTissuePredicate(List<String> donorNames, List<String> unmatching) {
        Predicate<Tissue> predicate = service.donorNameTissuePredicate(donorNames);
        if (donorNames==null) {
            assertNull(predicate);
            return;
        }
        Donor donor = new Donor();
        Tissue tissue = new Tissue();
        tissue.setDonor(donor);
        for (String donorName: donorNames) {
            donor.setDonorName(donorName);
            assertTrue(predicate.test(tissue));
            donor.setDonorName(donorName.toUpperCase());
            assertTrue(predicate.test(tissue));
        }
        for (String donorName: unmatching) {
            donor.setDonorName(donorName);
            assertFalse(predicate.test(tissue));
        }
    }

    static Stream<Arguments> donorNameTissuePredicateArgs() {
        return Arrays.stream(new Object[][] {
                {null, null},
                {List.of(), List.of("anything")},
                {List.of("alpha"), List.of("alpha1", "alph", "")},
                {List.of("alpha", "beta"), List.of("gamma")},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({
            ",,",
            "'',,'' a alpha",
            "alp*,alp alpha ALPHA, al bapl",
            "beta gamma,BETA Gamma,beta|gam gam bet gammarays",
            "alpha *eta, Alpha Beta Zeta Eta Theta, gamma"
    })
    public void testExternalNameTissuePredicate(String inputs, String goods, String bads) {
        List<String> externalNames;
        if (inputs==null)  {
            externalNames = null;
        } else if (inputs.isEmpty()) {
            externalNames = List.of();
        } else {
            externalNames = Arrays.asList(inputs.split("\\s+"));
        }
        Predicate<Tissue> predicate = service.externalNameTissuePredicate(externalNames);
        if (externalNames==null) {
            assertNull(predicate);
            return;
        }
        Tissue tissue = new Tissue();
        if (goods != null) {
            for (String good : goods.split("\\s+")) {
                tissue.setExternalName(good);
                assertTrue(predicate.test(tissue));
            }
        }
        if (bads != null) {
            for (String bad : bads.split("\\s+")) {
                tissue.setExternalName(bad);
                assertFalse(predicate.test(tissue));
            }
        }
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

        List<HistoryEntry> entries = List.of(opEntries.getFirst(), releaseEntries.getFirst(), destructionEntries.getFirst());

        when(mockOpRepo.findAllBySampleIdIn(sampleIds)).thenReturn(ops);
        when(mockDestructionRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(destructions);
        when(mockReleaseRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(releases);
        when(mockLwRepo.findAllByIdIn(labwareIds)).thenReturn(labware);
        List<String> flaggedBarcodes = List.of("Alpha", "Beta");
        doReturn(flaggedBarcodes).when(service).loadFlaggedBarcodes(labware);

        doReturn(labwareIds).when(service).loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);

        doReturn(opEntries).when(service).createEntriesForOps(ops, sampleIds, labware, opWork, null);
        doReturn(releaseEntries).when(service).createEntriesForReleases(releases, sampleIds, releaseWork, null);
        doReturn(destructionEntries).when(service).createEntriesForDestructions(destructions, sampleIds);

        doReturn(entries).when(service).assembleEntries(List.of(opEntries, releaseEntries, destructionEntries));

        History history = service.getHistoryForSamples(samples);
        assertEquals(entries, history.getEntries());
        assertEquals(samples, history.getSamples());
        assertEquals(labware, history.getLabware());
        assertEquals(flaggedBarcodes, history.getFlaggedBarcodes());
    }

    @ParameterizedTest
    @CsvSource({
            "true,false,false,",
            "false,true,false,",
            "false,false,true,Baking",
            "true,true,true,",
    })
    public void testGetHistoryForSamples_filter(boolean includeReleases, boolean includeDestructions,
                                                boolean includeOps, String requiredOpName) {
        final String requiredWorkNumber = null;
        OperationType requiredOpType = (requiredOpName==null ? null : EntityFactory.makeOperationType(requiredOpName, null));
        List<Operation> ops = includeOps ? List.of(new Operation(), new Operation()) : List.of();
        IntStream.range(0, ops.size()).forEach(i -> ops.get(i).setId(20+i));
        Sample s1 = EntityFactory.getSample();
        List<Sample> samples = List.of(s1, new Sample(s1.getId()+1, null, s1.getTissue(), s1.getBioState()));
        Set<Integer> sampleIds = Set.of(s1.getId(), samples.get(1).getId());
        if (includeOps) {
            when(requiredOpType==null ? mockOpRepo.findAllBySampleIdIn(sampleIds) : mockOpRepo.findAllByOperationTypeAndSampleIdIn(requiredOpType, sampleIds))
                    .thenReturn(ops);
        }
        Set<Integer> labwareIds = Set.of(4,5);
        if (ops.isEmpty()) {
            when(mockLwRepo.findAllLabwareIdsContainingSampleIds(sampleIds)).thenReturn(labwareIds);
        } else {
            doReturn(labwareIds).when(service).loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);
        }
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = List.of(EntityFactory.getTube(), EntityFactory.makeEmptyLabware(lt));
        when(mockLwRepo.findAllByIdIn(labwareIds)).thenReturn(labware);
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        Map<Integer, Set<String>> opWork = Map.of(20, Set.of("SGP12"));
        when(mockWorkRepo.findWorkNumbersForOpIds(opIds)).thenReturn(opWork);
        List<Release> releases = includeReleases ? List.of(new Release(), new Release()) : List.of();
        IntStream.range(0, releases.size()).forEach(i -> releases.get(i).setId(30+i));
        List<Integer> releaseIds = releases.stream().map(Release::getId).collect(toList());
        Map<Integer, String> releaseWork = Map.of(30, "SGP13");
        when(mockWorkRepo.findWorkNumbersForReleaseIds(releaseIds)).thenReturn(releaseWork);
        List<Destruction> destructions = includeDestructions ? List.of(new Destruction(), new Destruction()) : List.of();
        IntStream.range(0, destructions.size()).forEach(i -> destructions.get(i).setId(40+i));
        if (includeReleases) {
            when(mockReleaseRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(releases);
        }
        if (includeDestructions) {
            when(mockDestructionRepo.findAllByLabwareIdIn(labwareIds)).thenReturn(destructions);
        }

        List<HistoryEntry> opEntries = includeOps ? List.of(entryAtTime(1), entryAtTime(2)) : List.of();
        List<HistoryEntry> relEntries = includeReleases ? List.of(entryAtTime(3)) : List.of();
        List<HistoryEntry> desEntries = includeDestructions ? List.of(entryAtTime(4)) : List.of();

        doReturn(opEntries).when(service).createEntriesForOps(ops, sampleIds, labware, opWork, requiredWorkNumber);
        doReturn(relEntries).when(service).createEntriesForReleases(releases, sampleIds, releaseWork, requiredWorkNumber);
        doReturn(desEntries).when(service).createEntriesForDestructions(destructions, sampleIds);

        List<HistoryEntry> expectedEntries = Stream.of(opEntries, relEntries, desEntries)
                .flatMap(Collection::stream)
                .collect(toList());

        assertEquals(new History(expectedEntries, samples, labware),
                service.getHistoryForSamples(samples, requiredWorkNumber,
                        new EventTypeFilter(includeReleases, includeDestructions, includeOps, requiredOpType)));
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
    public void testLoadOpSolutions_noTransfers() {
        List<Operation> ops = IntStream.range(0,2).mapToObj(i -> new Operation()).toList();
        for (int i = 0; i < ops.size(); ++i) {
            ops.get(i).setId(100+i);
            ops.get(i).setOperationType(EntityFactory.makeOperationType("optype"+i, null));
        }
        assertThat(service.loadOpSolutions(ops)).isEmpty();
        verifyNoInteractions(mockOpSolRepo);
        verifyNoInteractions(mockSolutionRepo);
    }

    @Test
    public void testLoadOpSolutions_noOpSols() {
        List<Operation> ops = IntStream.range(0,3).mapToObj(i -> new Operation()).toList();
        OperationType solTransferOpType = EntityFactory.makeOperationType("Solution transfer", null, OperationTypeFlag.IN_PLACE);
        OperationType otherOpType = EntityFactory.makeOperationType("Other", null);
        for (int i = 0; i < ops.size(); ++i) {
            ops.get(i).setId(100+i);
            ops.get(i).setOperationType(i==0 ? otherOpType : solTransferOpType);
        }
        when(mockOpSolRepo.findAllByOperationIdIn(any())).thenReturn(List.of());

        assertThat(service.loadOpSolutions(ops)).isEmpty();

        verify(mockOpSolRepo).findAllByOperationIdIn(List.of(101,102));
        verifyNoInteractions(mockSolutionRepo);
    }

    @Test
    public void testLoadOpSolutions() {
        List<Operation> ops = IntStream.range(0,3).mapToObj(i -> new Operation()).toList();
        OperationType solTransferOpType = EntityFactory.makeOperationType("Solution transfer", null, OperationTypeFlag.IN_PLACE);
        OperationType otherOpType = EntityFactory.makeOperationType("Other", null);
        for (int i = 0; i < ops.size(); ++i) {
            ops.get(i).setId(100+i);
            ops.get(i).setOperationType(i==0 ? otherOpType : solTransferOpType);
        }
        List<OperationSolution> opsols = List.of(
                new OperationSolution(101, 201, 301, 401),
                new OperationSolution(102, 202, 302, 402)
        );
        when(mockOpSolRepo.findAllByOperationIdIn(any())).thenReturn(opsols);
        List<Solution> solutions = List.of(
                new Solution(201, "Solution 1"),
                new Solution(202, "Solution 2")
        );
        Map<Integer, Solution> solutionMap = solutions.stream().collect(inMap(Solution::getId));
        when(mockSolutionRepo.getMapByIdIn(any())).thenReturn(solutionMap);

        Map<Integer, Set<Solution>> opSolutions = service.loadOpSolutions(ops);

        verify(mockOpSolRepo).findAllByOperationIdIn(List.of(101,102));
        verify(mockSolutionRepo).getMapByIdIn(Set.of(201,202));
        assertThat(opSolutions).hasSize(2);
        assertThat(opSolutions.get(101)).containsExactly(solutions.get(0));
        assertThat(opSolutions.get(102)).containsExactly(solutions.get(1));
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
        LabwareProbe lwp = new LabwareProbe(1, new ProbePanel(1, "probe1"), 5, 6, "LOT1", 21, SlideCosting.SGP);
        assertThat(service.getLabwareProbeDetails(lwp)).containsExactly(
                "Probe panel: probe1",
                "Lot: LOT1",
                "Plex: 21",
                "Costing: SGP"
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

    @Test
    public void testLoadLabwareFlags() {
        OperationType flagOpType = EntityFactory.makeOperationType("Flag labware", null, OperationTypeFlag.IN_PLACE);
        OperationType otherOpType = EntityFactory.makeOperationType("Splunge", null, OperationTypeFlag.IN_PLACE);
        List<Operation> ops = List.of(
                new Operation(1, otherOpType, null, null, null),
                new Operation(2, flagOpType, null, null, null),
                new Operation(3, flagOpType, null, null, null)
        );
        assertThat(service.loadLabwareFlags(ops.subList(0,1))).isEmpty();
        verifyNoInteractions(mockFlagRepo);
        Labware lw = EntityFactory.getTube();
        List<LabwareFlag> flags = List.of(
                new LabwareFlag(10, lw, "Alpha", null, 2),
                new LabwareFlag(11, lw, "Beta", null, 3)
        );
        when(mockFlagRepo.findAllByOperationIdIn(List.of(2,3))).thenReturn(flags);

        var expected = Map.of(2, flags.subList(0,1), 3, flags.subList(1,2));
        assertEquals(expected, service.loadLabwareFlags(ops));
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
        ops.getFirst().setEquipment(new Equipment("Feeniks", "scanner"));
        int[] opIds = ops.stream().mapToInt(Operation::getId).toArray();
        final Set<Integer> opIdSet = Set.of(opIds[0], opIds[1]);
        LabwareProbe lwp = new LabwareProbe(1, new ProbePanel("probe1"), opIds[1], labware[3].getId(), "LOT1", 5, SlideCosting.SGP);

        StainType st1 = new StainType(1, "Coffee");
        StainType st2 = new StainType(2, "Blood");
        Map<Integer, List<StainType>> opStainTypes = Map.of(
                opIds[1], List.of(st1, st2)
        );
        doReturn(opStainTypes).when(mockStainTypeRepo).loadOperationStainTypes(any());

        Map<Integer, List<LabwareFlag>> opFlags = Map.of(opIds[0], List.of(new LabwareFlag(100, labware[0], "Alpha", null, opIds[0])));
        doReturn(opFlags).when(service).loadLabwareFlags(any());

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

        Solution sol = new Solution(100, "Solution 100");
        Map<Integer, Set<Solution>> opSolutions = Map.of(opIds[1], Set.of(sol));
        doReturn(opSolutions).when(service).loadOpSolutions(ops);

        // Letting doesCommentApply actually run is easier than mocking it to return what it would return anyway

        List<Labware> labwareList = Arrays.asList(this.labware);

        Set<Integer> sampleIds = Set.of(samples[0].getId(), samples[2].getId());

        String opTypeName0 = ops.get(0).getOperationType().getName();
        String opTypeName1 = ops.get(1).getOperationType().getName();
        String username = getUser().getUsername();
        List<HistoryEntry> expectedEntries = List.of(
                new HistoryEntry(opIds[0], opTypeName0, ops.get(0).getPerformed(), labware[0].getId(),
                        labware[1].getId(), samples[0].getId(), username, "SGP5000",
                        List.of("123 : A2 -> B3", "456 : C4 -> E6", "Alpha: Beta", "Gamma: Delta", "Flag: Alpha", "Equipment: Feeniks",
                                "A1: pass", "Alabama", "A1: Alaska", "Thickness: 4\u00a0μm", "ROI (90, A1): roi1"), "A1", null),
                new HistoryEntry(opIds[1], opTypeName1, ops.get(1).getPerformed(), labware[0].getId(),
                        labware[3].getId(), samples[2].getId(), username, null,
                        List.of("Stain type: Coffee, Blood", "Epsilon: Zeta", "Probe panel: probe1", "Lot: LOT1",
                                "Plex: 5", "Costing: SGP", "Solution: Solution 100", "Arizona"),
                        "A1", null)
        );
        final List<HistoryEntry> actualEntries = service.createEntriesForOps(ops, sampleIds, labwareList, opWork, null);
        for (HistoryEntry entry : actualEntries) {
            assertNotNull(entry.getOperation());
            entry.setOperation(null);
        }
        assertThat(actualEntries).containsExactlyElementsOf(expectedEntries);

        verify(service).loadOpMeasurements(opIdSet);
        verify(service).loadLabwareFlags(ops);
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
        if (expectedAddress2 != null) {
            expectedEntries.add(new HistoryEntry(op1.getId(), op1.getOperationType().getName(), op1.getPerformed(), labware[0].getId(),
                    labware[3].getId(), samples[sampleIndex2].getId(), getUser().getUsername(), null,
                    null, expectedAddress2, expectedRegion2));

        }

        expectedEntries.forEach(e -> e.setOperation(op1));

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
        HistoryEntry entry = entries.getFirst();
        assertSame(op, entry.getOperation());
        entry.setOperation(null);
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

    @ParameterizedTest
    @ValueSource(strings={"Release", "Destruction", "null", "Unicorn", "Baking"})
    public void testEventTypeFilter(String string) {
        if (string==null || string.equals("null")) {
            assertEquals(EventTypeFilter.NO_FILTER, service.eventTypeFilter(null));
            return;
        }
        if (string.equalsIgnoreCase("release")) {
            assertEquals(new EventTypeFilter(true,false,false,null), service.eventTypeFilter(string));
            return;
        }
        if (string.equalsIgnoreCase("destruction")) {
            assertEquals(new EventTypeFilter(false, true, false, null), service.eventTypeFilter(string));
            return;
        }
        OperationType opType = string.equalsIgnoreCase("unicorn") ? null : EntityFactory.makeOperationType(string, null);
        when(mockOpTypeRepo.findByName(string)).thenReturn(Optional.ofNullable(opType));

        if (opType!=null) {
            assertEquals(new EventTypeFilter(false, false, true, opType), service.eventTypeFilter(string));
            return;
        }

        assertThat(assertThrows(IllegalArgumentException.class, () -> service.eventTypeFilter(string)))
                .hasMessage("Unknown event type: "+repr(string));
    }

    @Test
    public void testGetEventTypes() {
        List<OperationType> opTypes = Stream.of("Alpha", "Beta").map(s -> EntityFactory.makeOperationType(s, null)).collect(toList());
        when(mockOpTypeRepo.findAll()).thenReturn(opTypes);
        assertThat(service.getEventTypes()).containsExactly("Alpha", "Beta", "Release", "Destruction");
    }

    @Test
    public void testLoadFlaggedBarcodes() {
        createLabware();
        List<Labware> labwares = Arrays.asList(this.labware);
        List<LabwareFlagged> lfs = IntStream.range(0, labwares.size())
                .mapToObj(i -> new LabwareFlagged(labwares.get(i), i==1 || i==2))
                .toList();
        when(mockFlagLookupService.getLabwareFlagged(labwares)).thenReturn(lfs);
        assertThat(service.loadFlaggedBarcodes(labwares)).containsExactlyInAnyOrder(labwares.get(1).getBarcode(), labwares.get(2).getBarcode());
    }
}
