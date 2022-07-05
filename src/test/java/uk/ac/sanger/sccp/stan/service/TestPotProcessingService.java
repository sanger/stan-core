package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.PotProcessingRequest;
import uk.ac.sanger.sccp.stan.request.PotProcessingRequest.PotProcessingDestination;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/**
 * Test {@link PotProcessingServiceImp}
 */
public class TestPotProcessingService {
    private LabwareValidatorFactory mockLwValidatorFactory;
    private WorkService mockWorkService;
    private CommentValidationService mockCommentValidationService;
    private LabwareService mockLwService;
    private OperationService mockOpService;
    private StoreService mockStoreService;
    private Transactor mockTransactor;

    private LabwareRepo mockLwRepo;
    private BioStateRepo mockBsRepo;
    private FixativeRepo mockFixRepo;
    private LabwareTypeRepo mockLwTypeRepo;
    private TissueRepo mockTissueRepo;
    private SampleRepo mockSampleRepo;
    private SlotRepo mockSlotRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationCommentRepo mockOpComRepo;

    private PotProcessingServiceImp service;

    @BeforeEach
    void setup() {
        mockLwValidatorFactory = mock(LabwareValidatorFactory.class);
        mockWorkService = mock(WorkService.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockLwService = mock(LabwareService.class);
        mockOpService = mock(OperationService.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockBsRepo = mock(BioStateRepo.class);
        mockFixRepo = mock(FixativeRepo.class);
        mockLwTypeRepo = mock(LabwareTypeRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockStoreService = mock(StoreService.class);
        mockTransactor = mock(Transactor.class);

        service = spy(new PotProcessingServiceImp(mockLwValidatorFactory, mockWorkService, mockCommentValidationService,
                mockStoreService, mockTransactor, mockLwService, mockOpService, mockLwRepo, mockBsRepo, mockFixRepo, mockLwTypeRepo, mockTissueRepo,
                mockSampleRepo, mockSlotRepo, mockOpTypeRepo, mockOpComRepo));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true"})
    public void testPerform(boolean succeed, boolean discard) {
        User user = EntityFactory.getUser();
        PotProcessingRequest request = new PotProcessingRequest();
        request.setSourceBarcode("STAN-A1");
        request.setSourceDiscarded(discard);

        OperationResult opres;
        if (succeed) {
            opres = new OperationResult(List.of(), List.of());
            doReturn(opres).when(service).performInTransaction(any(), any());
        } else {
            opres = null;
            doThrow(ValidationException.class).when(service).performInTransaction(any(), any());
        }
        Matchers.mockTransactor(mockTransactor);
        if (succeed) {
            assertSame(opres, service.perform(user, request));
        } else {
            assertThrows(ValidationException.class, () -> service.perform(user, request));
        }
        verify(mockTransactor).transact(any(), any());
        verify(service).performInTransaction(user, request);
        if (succeed && discard) {
            verify(mockStoreService).discardStorage(user, List.of(request.getSourceBarcode()));
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @Test
    public void testPerformInTransaction_none() {
        User user = EntityFactory.getUser();
        Labware source = EntityFactory.getTube();

        PotProcessingRequest request = new PotProcessingRequest(source.getBarcode(), "SGP1", List.of());
        doReturn(source).when(service).loadSource(any(), any());
        when(mockWorkService.validateUsableWork(any(), any())).then(Matchers.addProblem("Bad work."));

        assertValidationException(() -> service.performInTransaction(user, request), "The request could not be validated.",
                "Bad work.", "No destinations specified.");

        verify(service).loadSource(any(), eq(request.getSourceBarcode()));
        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service, never()).loadFixatives(any(), any());
        verify(service, never()).loadLabwareTypes(any(), any());
        verify(service, never()).loadComments(any(), any());
        verify(service, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testPerformInTransaction_valid() {
        Work work = new Work(5, "SGP1", null, null, null, null, null);
        Labware source = EntityFactory.getTube();
        List<PotProcessingDestination> dests = List.of(
                new PotProcessingDestination("Pot", "fix1", 1),
                new PotProcessingDestination("Fetal waste labware", "None", null)
        );
        PotProcessingRequest request = new PotProcessingRequest(source.getBarcode(),
                work.getWorkNumber(), dests);
        User user = EntityFactory.getUser();
        doReturn(source).when(service).loadSource(any(), any());
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        UCMap<Fixative> fixatives = UCMap.from(Fixative::getName, new Fixative(10, "fix1"));
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, EntityFactory.makeLabwareType(1,1));
        Map<Integer, Comment> comments = Map.of(10, new Comment(10, "custard", "non-newtonian fluid"));

        OperationResult result = new OperationResult(List.of(), List.of());

        doReturn(fixatives).when(service).loadFixatives(any(), any());
        doReturn(lwTypes).when(service).loadLabwareTypes(any(), any());
        doReturn(comments).when(service).loadComments(any(), any());
        doNothing().when(service).checkFixatives(any(), any(), any());
        doReturn(result).when(service).record(any(), any(), any(), any(), any(), any(), any());

        assertSame(result, service.performInTransaction(user, request));

        verifyValidation(request, work, fixatives);

        verify(service).record(user, request, source, fixatives, lwTypes, comments, work);
    }

    @Test
    public void testPerformInTransaction_invalid() {
        List<PotProcessingDestination> dests = List.of(new PotProcessingDestination());
        PotProcessingRequest request = new PotProcessingRequest("STAN-1", "", dests);
        User user = EntityFactory.getUser();
        doAnswer(Matchers.addProblem("Bad source")).when(service).loadSource(any(), any());
        UCMap<Fixative> fixatives = UCMap.from(Fixative::getName, new Fixative(10, "fix1"));
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, EntityFactory.makeLabwareType(1,1));
        Map<Integer, Comment> comments = Map.of(10, new Comment(10, "custard", "non-newtonian fluid"));
        doAnswer(Matchers.addProblem("Bad fixative", fixatives)).when(service).loadFixatives(any(), any());
        doAnswer(Matchers.addProblem("Bad lw type", lwTypes)).when(service).loadLabwareTypes(any(), any());
        doAnswer(Matchers.addProblem("Bad comment id", comments)).when(service).loadComments(any(), any());
        doAnswer(Matchers.addProblem("Unexpected fixative")).when(service).checkFixatives(any(), any(), any());

        assertValidationException(() -> service.performInTransaction(user, request), "The request could not be validated.",
                "Bad source", "Bad fixative", "Bad lw type", "Bad comment id", "Unexpected fixative", "No work number was supplied.");
        verifyValidation(request, null, fixatives);
        verify(service, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private void verifyValidation(PotProcessingRequest request, Work work, UCMap<Fixative> fixatives) {
        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).loadFixatives(problemsCaptor.capture(), same(request));
        Collection<String> problems = problemsCaptor.getValue();
        if (request.getWorkNumber()!=null && !request.getWorkNumber().isEmpty()) {
            verify(mockWorkService).validateUsableWork(same(problems), eq(work.getWorkNumber()));
        } else {
            verifyNoInteractions(mockWorkService);
        }
        verify(service).loadFixatives(same(problems), same(request));
        verify(service).loadLabwareTypes(same(problems), same(request));
        verify(service).loadComments(same(problems), eq(request.getDestinations()));
        verify(service).checkFixatives(same(problems), same(request), same(fixatives));
    }

    @ParameterizedTest
    @CsvSource({",false,", "STAN-A1, false, No such thing", "STAN-A1, true, Bad labware", "STAN-A1, true,"})
    public void testLoadSource(String barcode, boolean exists, String validationProblem) {
        if (barcode==null || barcode.isEmpty()) {
            final List<String> problems = new ArrayList<>(1);
            assertNull(service.loadSource(problems, barcode));
            assertThat(problems).containsExactly("No source barcode was supplied.");
            verifyNoInteractions(mockLwValidatorFactory);
            return;
        }
        Labware lw = (exists ? EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()) : null);
        if (lw!=null) {
            lw.setBarcode(barcode);
        }
        BioState bs = EntityFactory.getBioState();
        when(mockBsRepo.getByName("Original sample")).thenReturn(bs);
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValidatorFactory.getValidator()).thenReturn(val);
        List<String> validationProblems = (validationProblem==null ? List.of() : List.of(validationProblem));
        when(val.getErrors()).thenReturn(validationProblems);
        final List<Labware> lwList = lw == null ? List.of() : List.of(lw);
        when(val.getLabware()).thenReturn(lwList);
        when(val.loadLabware(any(), any())).thenReturn(lwList);

        final List<String> problems = new ArrayList<>(validationProblems.size());
        assertSame(lw, service.loadSource(problems, barcode));

        verify(val).loadLabware(mockLwRepo, List.of(barcode));
        verify(val).setSingleSample(true);
        verify(val).validateSources();
        verify(val).validateBioState(bs);
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "false,false,false", "true,true,false", "false,true,false", "true,true,true",
            "false,true,true", "true,false,true"})
    public void testLoadLabwareTypes(boolean anyNull, boolean anyInvalid, boolean anyValid) {
        List<LabwareType> lwTypes;
        if (anyValid) {
            lwTypes = List.of(EntityFactory.makeLabwareType(1, 1), EntityFactory.makeLabwareType(1, 1));
            lwTypes.get(0).setName("Alpha");
            lwTypes.get(1).setName("Beta");
        } else {
            lwTypes = List.of();
        }
        List<String> strings = new ArrayList<>(lwTypes.size() + (anyNull ? 1 : 0) + (anyInvalid ? 1 : 0));
        List<String> expectedProblems = new ArrayList<>((anyNull ? 1 : 0) + (anyInvalid ? 1 : 0));
        for (LabwareType lt : lwTypes) {
            strings.add(lt.getName());
        }
        if (anyNull) {
            strings.add(null);
            expectedProblems.add("Labware type name missing.");
        }
        if (anyInvalid) {
            strings.add("Bananas");
            expectedProblems.add("Labware type name unknown: [\"Bananas\"]");
        }
        testLoadUCMap(mockLwTypeRepo, LabwareTypeRepo::findAllByNameIn, strings,
                PotProcessingDestination::setLabwareType, service::loadLabwareTypes, lwTypes, expectedProblems);
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "false,false,false", "true,true,false", "false,true,false", "true,true,true",
            "false,true,true", "true,false,true"})
    public void testLoadFixatives(boolean anyNull, boolean anyInvalid, boolean anyValid) {
        List<Fixative> fixatives;
        if (anyValid) {
            fixatives = List.of(new Fixative(1, "Alpha"), new Fixative(2, "Beta"));
        } else {
            fixatives = List.of();
        }
        List<String> strings = new ArrayList<>(fixatives.size() + (anyNull ? 1 : 0) + (anyInvalid ? 1 : 0));
        List<String> expectedProblems = new ArrayList<>((anyNull ? 1 : 0) + (anyInvalid ? 1 : 0));
        for (var fix : fixatives) {
            strings.add(fix.getName());
        }
        if (anyNull) {
            strings.add(null);
            expectedProblems.add("Fixative name missing.");
        }
        if (anyInvalid) {
            strings.add("Bananas");
            expectedProblems.add("Fixative name unknown: [\"Bananas\"]");
        }
        testLoadUCMap(mockFixRepo, FixativeRepo::findAllByNameIn, strings,
                PotProcessingDestination::setFixative, service::loadFixatives, fixatives, expectedProblems);
    }

    private <E, R> void testLoadUCMap(R repo, BiFunction<R, Collection<String>, List<E>> repoFn,
                                     Collection<String> strings, BiConsumer<PotProcessingDestination, String> reqSetter,
                                     BiFunction<Collection<String>, PotProcessingRequest, UCMap<E>> serviceFn,
                                     List<E> entities, Collection<String> expectedProblems) {

        PotProcessingRequest request = new PotProcessingRequest(null, null,
                strings.stream().map(string -> {
                    PotProcessingDestination dest = new PotProcessingDestination();
                    reqSetter.accept(dest, string);
                    return dest;
                }).collect(toList()));

        when(repoFn.apply(repo, any())).thenReturn(entities);

        List<String> problems = new ArrayList<>(expectedProblems.size());
        var result = serviceFn.apply(problems, request);

        Set<String> stringSet = strings.stream().filter(s -> s!=null && !s.isEmpty()).collect(toSet());
        if (stringSet.isEmpty()) {
            verifyNoInteractions(repo);
        } else {
            repoFn.apply(verify(repo), stringSet);
        }
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(entities);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @CsvSource({",,", "None, Tube,", "Fix1, Tube,", "None, Fetal waste container,",
               "Fix1, Fetal waste container, A fixative is not expected for fetal waste labware.",
               "Fix1:Fix1:None, Tube:Tube:Fetal waste container,"})
    public void testCheckFixatives(String fixativesJoined, String lwTypesJoined, String expectedProblem) {
        String[] fixNames = fixativesJoined==null ? new String[0] : fixativesJoined.split(";");
        String[] lwTypeNames = lwTypesJoined==null ? new String[0] : lwTypesJoined.split(";");
        List<PotProcessingDestination> dests = IntStream.range(0, fixNames.length)
                .mapToObj(i -> new PotProcessingDestination(lwTypeNames[i], fixNames[i], null))
                .collect(toList());
        List<String> expectedProblems = expectedProblem==null ? List.of() : List.of(expectedProblem);
        List<String> problems = new ArrayList<>(expectedProblems.size());
        final var idIter = IntStream.range(0, fixNames.length).iterator();
        var fixativeSet = Arrays.stream(fixNames)
                .filter(fn -> fn!=null && !fn.isEmpty() && fn.indexOf('!')<0)
                .map(fn -> new Fixative(idIter.next(), fn))
                .collect(toSet());
        UCMap<Fixative> fixatives = UCMap.from(fixativeSet, Fixative::getName);
        service.checkFixatives(problems, new PotProcessingRequest(null, null, dests), fixatives);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @Test
    public void testLoadComments() {
        List<Comment> comments = List.of(new Comment(1, "Alpha", "Beta"),
                new Comment(2, "Beta", "Gamma"));
        String problem = "No such comment id: 3";
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(Matchers.addProblem(problem, comments));
        List<String> problems = new ArrayList<>(1);
        Map<Integer, Comment> result = service.loadComments(problems,
                Stream.of(1, 2, 3, null)
                        .map(n -> new PotProcessingDestination(null, null, n))
                        .collect(toList()));
        //noinspection unchecked
        ArgumentCaptor<Stream<Integer>> commentIdCaptor = ArgumentCaptor.forClass(Stream.class);
        verify(mockCommentValidationService).validateCommentIds(any(), commentIdCaptor.capture());
        var commentIdStream = commentIdCaptor.getValue();
        assertThat(commentIdStream).containsExactlyInAnyOrder(1,2,3);
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(comments);
        comments.forEach(c -> assertSame(c, result.get(c.getId())));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testRecord(boolean discard) {
        Sample ogSample = EntityFactory.getSample();
        Labware source = EntityFactory.makeLabware(EntityFactory.getTubeType(), ogSample);
        Tissue ogTissue = ogSample.getTissue();
        UCMap<Tissue> fixTissues = new UCMap<>(1);
        final Work work = new Work(100, "SGP100", null, null, null, null, null);
        Fixative fix1 = new Fixative(1, "fix1");
        UCMap<Fixative> fixatives = UCMap.from(Fixative::getName, fix1);
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, EntityFactory.getTubeType());
        Map<Integer, Comment> commentMap = Map.of(200, new Comment(200, "Custard", "non-newtonion"));
        Tissue newTissue = new Tissue(100, null, null, ogTissue.getSpatialLocation(), ogTissue.getDonor(),
                ogTissue.getMedium(), fix1, ogTissue.getHmdmc(), ogTissue.getCollectionDate(),
                ogTissue.getId());
        final Sample newSample = new Sample(100, null, newTissue, ogSample.getBioState());
        List<Labware> destLabware = List.of(EntityFactory.makeLabware(EntityFactory.getTubeType(), newSample));
        List<PotProcessingDestination> ppds = List.of(
                new PotProcessingDestination("Tube", "Fix1", 1)
        );
        PotProcessingRequest request = new PotProcessingRequest(source.getBarcode(), work.getWorkNumber(), ppds, discard);
        fixTissues.put("fix1", newTissue);
        doReturn(fixTissues).when(service).fixativesToTissues(any(), any());
        List<Sample> samples = List.of(ogSample, newSample);
        doReturn(samples).when(service).createSamples(any(), any(), any());
        doReturn(destLabware).when(service).createDestinations(any(), any(), any());
        List<Operation> ops = List.of(new Operation());
        doReturn(ops).when(service).createOps(any(), any(), any(), any(), any());

        User user = EntityFactory.getUser();
        var opRes = service.record(user, request, source, fixatives, lwTypes, commentMap, work);

        verify(service).fixativesToTissues(ogTissue, fixatives.values());
        verify(service).createSamples(ppds, ogSample, fixTissues);
        verify(service).createDestinations(ppds, lwTypes, samples);
        verify(service).createOps(ppds, user, source, destLabware, commentMap);
        if (discard) {
            verify(mockLwRepo).save(source);
        }
        assertEquals(discard, source.isDiscarded());
        verify(mockWorkService).link(work, ops);

        assertEquals(opRes.getLabware(), destLabware);
        assertEquals(opRes.getOperations(), ops);
    }

    @Test
    public void testFixativesToTissues() {
        Tissue ogTissue = EntityFactory.getTissue();
        List<Fixative> fixatives = List.of(new Fixative(1, "Fix1"), new Fixative(2, "Fix2"), ogTissue.getFixative());
        doAnswer(invocation -> {
            Tissue tissue = invocation.getArgument(0);
            Fixative fix = invocation.getArgument(1);
            Tissue newTissue = new Tissue();
            newTissue.setId(tissue.getId() + fix.getId());
            newTissue.setFixative(fix);
            return newTissue;
        }).when(service).createTissue(any(), any());

        UCMap<Tissue> fixTissues = service.fixativesToTissues(ogTissue, fixatives);
        verify(service, times(2)).createTissue(any(), any());
        verify(service).createTissue(ogTissue, fixatives.get(0));
        verify(service).createTissue(ogTissue, fixatives.get(1));
        assertThat(fixTissues).hasSize(3);
        for (Fixative fix : fixatives) {
            assertEquals(fix, fixTissues.get(fix.getName()).getFixative());
        }
        assertSame(ogTissue, fixTissues.get(ogTissue.getFixative().getName()));
    }

    @Test
    public void testCreateTissue() {
        Fixative noFix = new Fixative(1, "None");
        Fixative fix1 = new Fixative(2, "fix1");
        Tissue ogTissue = new Tissue(100, null, null, EntityFactory.getSpatialLocation(), EntityFactory.getDonor(),
                EntityFactory.getMedium(), noFix, EntityFactory.getHmdmc(), LocalDate.of(2022,6,7),
                null);
        when(mockTissueRepo.save(any())).then(invocation -> {
            Tissue t = invocation.getArgument(0);
            assertNull(t.getId());
            t.setId(500);
            return t;
        });
        Tissue newTissue = service.createTissue(ogTissue, fix1);
        verify(mockTissueRepo).save(newTissue);
        assertEquals(new Tissue(500, ogTissue.getExternalName(), ogTissue.getReplicate(), ogTissue.getSpatialLocation(),
                ogTissue.getDonor(), ogTissue.getMedium(), fix1, ogTissue.getHmdmc(), ogTissue.getCollectionDate(),
                ogTissue.getId()), newTissue);
    }

    @ParameterizedTest
    @CsvSource({"Fetal waste container, true", "Tube, false", ", false"})
    public void testIsForFetalWaste(String lwTypeName, boolean expected) {
        PotProcessingDestination dest = new PotProcessingDestination();
        dest.setLabwareType(lwTypeName);
        assertEquals(expected, service.isForFetalWaste(dest));
    }

    @Test
    public void testCreateSample() {
        Tissue tissue = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sample = invocation.getArgument(0);
            assertNull(sample.getId());
            sample.setId(300);
            return sample;
        });
        Sample sample = service.createSample(tissue, bs);
        assertEquals(new Sample(300, null, tissue, bs), sample);
    }

    @Test
    public void testCreateSamples() {
        BioState bs1 = new BioState(1, "BS1");
        BioState fwBs = new BioState(10, "Fetal waste");
        when(mockBsRepo.getByName("Fetal waste")).thenReturn(fwBs);
        Donor donor = EntityFactory.getDonor();
        Fixative noFix = new Fixative(1, "None");
        Fixative fix1 = new Fixative(2, "fix1");

        Tissue ogTissue = new Tissue();
        ogTissue.setId(1);
        ogTissue.setDonor(donor);
        ogTissue.setFixative(noFix);
        Tissue tissue1 = new Tissue();
        tissue1.setId(2);
        tissue1.setDonor(donor);
        tissue1.setFixative(fix1);

        Sample ogSample = new Sample(100, null, ogTissue, bs1);
        UCMap<Tissue> fixTissues = UCMap.from(t -> t.getFixative().getName(), ogTissue, tissue1);

        List<PotProcessingDestination> ppds = List.of(
                new PotProcessingDestination("Tube", "fix1"),
                new PotProcessingDestination("Tube", "None"),
                new PotProcessingDestination("Tube", "fix1"),
                new PotProcessingDestination("Fetal waste container", "None"),
                new PotProcessingDestination("Fetal waste container", "None")
        );

        doAnswer(invocation -> {
            Tissue t = invocation.getArgument(0);
            BioState bs = invocation.getArgument(1);
            return new Sample(t.getId()+bs.getId(), null, t, bs);
        }).when(service).createSample(any(), any());

        List<Sample> samples = service.createSamples(ppds, ogSample, fixTissues);
        assertThat(samples).hasSize(5);
        assertSame(samples.get(0), samples.get(2));
        assertSame(ogSample, samples.get(1));
        assertSame(samples.get(3), samples.get(4));
        for (int i = 0; i < samples.size(); ++i) {
            Sample sample = samples.get(i);
            final PotProcessingDestination ppd = ppds.get(i);
            boolean isFw = ppd.getLabwareType().equalsIgnoreCase("Fetal waste container");
            assertSame(isFw ? fwBs : bs1, sample.getBioState());
            assertSame(fixTissues.get(ppd.getFixative()), sample.getTissue());
        }
        verify(service, times(2)).createSample(any(), any());
        verify(service).createSample(tissue1, bs1);
        verify(service).createSample(ogTissue, fwBs);
    }

    @Test
    public void testCreateDestinations() {
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, null, sample1.getTissue(), EntityFactory.getBioState());
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1,"lt1");
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1, "lt2");
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lt1, lt2);
        List<LabwareType> lts = List.of(lt1, lt1, lt2, lt2);
        List<PotProcessingDestination> ppds = lts.stream()
                .map(lt -> new PotProcessingDestination(lt.getName(), "fix1"))
                .collect(toList());
        List<Sample> samples = List.of(sample1, sample1, sample1, sample2);
        when(mockLwService.create(any(LabwareType.class))).thenAnswer(invocation -> {
            LabwareType lt = invocation.getArgument(0);
            return EntityFactory.makeEmptyLabware(lt);
        });

        List<Labware> labware = service.createDestinations(ppds, ltMap, samples);
        assertThat(labware).hasSameSizeAs(samples);
        for (int i = 0; i < labware.size(); ++i) {
            Labware lw = labware.get(i);
            Slot slot = lw.getFirstSlot();
            assertThat(slot.getSamples()).containsExactly(samples.get(i));
            verify(mockSlotRepo).save(slot);
            assertSame(lts.get(i), lw.getLabwareType());
        }

        verify(mockLwService, times(samples.size())).create(any(LabwareType.class));
    }

    @Test
    public void testCreateOps() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Pot processing", null);
        when(mockOpTypeRepo.getByName("Pot processing")).thenReturn(opType);
        Labware source = EntityFactory.getTube();
        Slot srcSlot = source.getFirstSlot();
        Sample srcSample = srcSlot.getSamples().get(0);

        Sample[] samples = IntStream.range(0,3)
                .mapToObj(i -> new Sample(10*srcSample.getId()+i, null, srcSample.getTissue(), srcSample.getBioState()))
                .toArray(Sample[]::new);
        List<Labware> destLabware = Arrays.stream(samples)
                .map(sam -> EntityFactory.makeLabware(source.getLabwareType(), sam))
                .collect(toList());

        Comment com1 = new Comment(1, "Alpha", "Beta");
        Comment com2 = new Comment(2, "Gamma", "Delta");
        Map<Integer, Comment> commentMap = Map.of(1, com1, 2, com2);
        List<PotProcessingDestination> ppds = List.of(
                new PotProcessingDestination("Tube", "fix1"),
                new PotProcessingDestination("Tube", "fix2", 1),
                new PotProcessingDestination("Tube", "fix2", 2)
        );
        var ops = IntStream.range(0,3)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100+i);
                    return op;
                })
                .collect(toList());
        doReturn(ops.get(0), ops.get(1), ops.get(2)).when(service).createOp(any(), any(), any(), any(), any(), any());

        assertEquals(ops, service.createOps(ppds, user, source, destLabware, commentMap));


        verify(service, times(ppds.size())).createOp(any(), any(), any(), any(), any(), any());
        for (int i = 0; i < ppds.size(); ++i) {
            verify(service).createOp(opType, user, srcSlot, destLabware.get(i).getFirstSlot(), srcSample, samples[i]);
        }

        List<OperationComment> expectedOpComs = IntStream.range(1,3).mapToObj(i ->
            new OperationComment(null, commentMap.get(i), ops.get(i).getId(), samples[i].getId(), destLabware.get(i).getFirstSlot().getId(), null)
        ).collect(toList());
        verify(mockOpComRepo).saveAll(expectedOpComs);
    }

    @Test
    public void testCreateOp() {
        OperationType opType = EntityFactory.makeOperationType("Pot processing", null);
        User user = EntityFactory.getUser();
        Sample srcSam = EntityFactory.getSample();
        Sample dstSam = new Sample(srcSam.getId()+1, null, srcSam.getTissue(), srcSam.getBioState());
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeLabware(lt, srcSam);
        Labware lw2 = EntityFactory.makeLabware(lt, dstSam);
        Slot src = lw1.getFirstSlot();
        Slot dst = lw2.getFirstSlot();
        Operation op = new Operation();
        op.setId(500);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOp(opType, user, src, dst, srcSam, dstSam));

        verify(mockOpService).createOperation(opType, user, List.of(new Action(null, null, src, dst, dstSam, srcSam)), null);
    }
}