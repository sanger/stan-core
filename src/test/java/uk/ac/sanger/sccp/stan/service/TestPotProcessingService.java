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
import uk.ac.sanger.sccp.stan.service.PotProcessingServiceImp.TissueFixKey;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Test {@link PotProcessingServiceImp}
 */
public class TestPotProcessingService {
    private LabwareValidatorFactory mockLwValidatorFactory;
    private WorkService mockWorkService;
    private CommentValidationService mockCommentValidationService;
    private LabwareService mockLwService;
    private BioRiskService mockBioRiskService;
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
        mockBioRiskService = mock(BioRiskService.class);
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
                mockStoreService, mockTransactor, mockLwService, mockBioRiskService, mockOpService, mockLwRepo, mockBsRepo, mockFixRepo, mockLwTypeRepo, mockTissueRepo,
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
        Work work = new Work(5, "SGP1", null, null, null, null, null, null);
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
    @CsvSource({",false,false,", "STAN-A1, false, false, No such thing", "STAN-A1, true, false, Bad labware", "STAN-A1, true,false,",
            "STAN-A1, true, true,"})
    public void testLoadSource(String barcode, boolean exists, boolean wrongSlot, String validationProblem) {
        if (nullOrEmpty(barcode)) {
            final List<String> problems = new ArrayList<>(1);
            assertNull(service.loadSource(problems, barcode));
            assertThat(problems).containsExactly("No source barcode was supplied.");
            verifyNoInteractions(mockLwValidatorFactory);
            return;
        }
        Labware lw;
        if (!exists) {
            lw = null;
        } else if (wrongSlot) {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
            lw.getSlots().getLast().addSample(EntityFactory.getSample());
        } else {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        }
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

        final List<String> problems = new ArrayList<>();
        assertSame(lw, service.loadSource(problems, barcode));

        verify(val).loadLabware(mockLwRepo, List.of(barcode));
        verify(val, never()).setSingleSample(true);
        verify(val).validateSources();
        verify(val).validateBioState(bs);
        assertProblem(problems, wrongSlot ? "Source labware should not have samples in slots other than the first." : validationProblem);
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
    void testRecord(boolean discard) {
        User user = EntityFactory.getUser();
        PotProcessingRequest request = new PotProcessingRequest();
        request.setSourceDiscarded(discard);
        request.setDestinations(List.of(new PotProcessingDestination()));
        Sample[] samples = EntityFactory.makeSamples(6);
        List<Sample> sourceSamples = List.of(samples[0], samples[1]);
        LabwareType lt = EntityFactory.getTubeType();
        Labware source = EntityFactory.makeEmptyLabware(lt);
        source.getFirstSlot().getSamples().addAll(sourceSamples);
        Map<TissueFixKey, Tissue> destTissueMap = Map.of(new TissueFixKey(samples[0].getTissue(), samples[0].getTissue().getFixative().getName()), samples[0].getTissue());
        List<List<Sample>> destSamples = List.of(List.of(samples[2], samples[3]), List.of(samples[4], samples[5]));
        List<Labware> destLabwares = List.of(EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt));
        List<Operation> ops = IntStream.range(1, 3).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toList();
        Work work = EntityFactory.makeWork("SGP1");
        UCMap<Fixative> fixMap = UCMap.from(Fixative::getName, new Fixative(1, "fix1"));
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lt);
        Map<Integer, Comment> commentMap = Map.of(1, new Comment(1, "Alpha", "Beta"));

        doReturn(destTissueMap).when(service).tissueFixToTissue(any(), any());
        doReturn(destSamples).when(service).createSamplesPerDestination(any(), any(), any());
        doReturn(destLabwares).when(service).createDestinations(any(), any(), any());
        doReturn(ops).when(service).createOps(any(), any(), any(), any(), any(), any(), any());

        OperationResult opres = service.record(user, request, source, fixMap, ltMap, commentMap, work);

        verify(service).tissueFixToTissue(sourceSamples, fixMap.values());
        verify(service).createSamplesPerDestination(request.getDestinations(), sourceSamples, destTissueMap);
        verify(service).createDestinations(request.getDestinations(), ltMap, destSamples);
        verify(service).createOps(request.getDestinations(), destSamples, user, source, sourceSamples, destLabwares, commentMap);
        assertEquals(discard, source.isDiscarded());
        if (discard) {
            verify(mockLwRepo).save(source);
        } else {
            verifyNoInteractions(mockLwRepo);
        }
        verify(mockBioRiskService).copyOpSampleBioRisks(ops);
        verify(mockWorkService).link(work, ops);
        assertThat(opres.getOperations()).containsExactlyElementsOf(ops);
        assertThat(opres.getLabware()).containsExactlyElementsOf(destLabwares);
    }

    @Test
    void testTissueFixToTissue() {
        Tissue[] srcTissues = {EntityFactory.makeTissue(null, null), EntityFactory.makeTissue(null, null)};
        Fixative[] fixes = IntStream.rangeClosed(1, 3).mapToObj(i -> new Fixative(i, "fix" + i)).toArray(Fixative[]::new);
        srcTissues[0].setFixative(fixes[0]);
        srcTissues[1].setFixative(fixes[0]);
        Sample[] samples = EntityFactory.makeSamples(3);
        samples[0].setTissue(srcTissues[0]);
        samples[1].setTissue(srcTissues[1]);
        samples[2].setTissue(srcTissues[1]);

        doAnswer(invocation -> {
            Tissue tis = invocation.getArgument(0);
            Fixative fix = invocation.getArgument(1);
            Tissue newTis = EntityFactory.makeTissue(null, null);
            newTis.setFixative(fix);
            newTis.setParentId(tis.getId());
            return newTis;
        }).when(service).createTissue(any(), any());

        Map<TissueFixKey, Tissue> result = service.tissueFixToTissue(Arrays.asList(samples), Arrays.asList(fixes));

        Set<TissueFixKey> expectedKeys = Arrays.stream(srcTissues)
                .flatMap(t -> Arrays.stream(fixes).map(f -> new TissueFixKey(t, f.getName())))
                .collect(toSet());
        assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(expectedKeys);

        for (Fixative fix : fixes) {
            for (Tissue srcTis : srcTissues) {
                Tissue destTis = result.get(new TissueFixKey(srcTis, fix.getName()));
                if (srcTis.getFixative()==fix) {
                    assertSame(srcTis, destTis);
                } else {
                    assertSame(fix, destTis.getFixative());
                    assertEquals(srcTis.getId(), destTis.getParentId());
                    verify(service).createTissue(srcTis, fix);
                }
            }
        }
    }

    @Test
    public void testCreateTissue() {
        Fixative noFix = new Fixative(1, "None");
        Fixative fix1 = new Fixative(2, "fix1");
        Tissue ogTissue = new Tissue(100, null, null, EntityFactory.getSpatialLocation(), EntityFactory.getDonor(),
                EntityFactory.getMedium(), noFix, EntityFactory.getCellClass(), EntityFactory.getHmdmc(), LocalDate.of(2022,6,7),
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
                ogTissue.getDonor(), ogTissue.getMedium(), fix1, EntityFactory.getCellClass(), ogTissue.getHmdmc(),
                ogTissue.getCollectionDate(), ogTissue.getId()), newTissue);
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
    void testCreateSamplesPerDestination() {
        BioState bs = EntityFactory.getBioState();
        BioState fwBs = new BioState(100, "Fetal waste");
        when(mockBsRepo.getByName(fwBs.getName())).thenReturn(fwBs);
        Fixative[] fixatives = {new Fixative(1, "fix1"), new Fixative(2, "fix2"), new Fixative(3, "fix3")};
        Tissue[] ogTissues = {new Tissue(), new Tissue()};
        for (int i = 0; i < ogTissues.length; ++i) {
            ogTissues[i].setId(10 + i);
            ogTissues[i].setFixative(fixatives[0]);
        }
        List<Sample> ogSamples = List.of(
                new Sample(200, null, ogTissues[0], bs),
                new Sample(201, null, ogTissues[1], bs)
        );
        Tissue[] newTissues = {new Tissue(), new Tissue(), new Tissue(), new Tissue()};
        for (int i = 0; i < newTissues.length; ++i) {
            newTissues[i].setId(20 + i);
            newTissues[i].setFixative(fixatives[1+i/2]);
        }
        Map<TissueFixKey, Tissue> destTissueMap = new HashMap<>(6);
        destTissueMap.put(new TissueFixKey(10, "fix1"), ogTissues[0]);
        destTissueMap.put(new TissueFixKey(11, "fix1"), ogTissues[1]);
        destTissueMap.put(new TissueFixKey(10, "fix2"), newTissues[0]);
        destTissueMap.put(new TissueFixKey(11, "fix2"), newTissues[1]);
        destTissueMap.put(new TissueFixKey(10, "fix3"), newTissues[2]);
        destTissueMap.put(new TissueFixKey(11, "fix3"), newTissues[3]);
        List<PotProcessingDestination> destinations = List.of(
                new PotProcessingDestination("lt1", "fix1"),
                new PotProcessingDestination("lt1", "fix2"),
                new PotProcessingDestination("lt1", "fix2"),
                new PotProcessingDestination(LabwareType.FETAL_WASTE_NAME, "fix3")
        );

        Sample[] createdSamples = {
                new Sample(202, null, newTissues[0], bs),
                new Sample(203, null, newTissues[1], bs),
                new Sample(204, null, newTissues[2], fwBs),
                new Sample(205, null, newTissues[3], fwBs),
        };

        doAnswer(invocation -> {
            Tissue tis = invocation.getArgument(0);
            BioState b = invocation.getArgument(1);
            return Arrays.stream(createdSamples)
                    .filter(s -> s.getTissue()==tis && s.getBioState()==b)
                    .findAny().orElseThrow();
        }).when(service).createSample(any(), any());

        List<List<Sample>> samplesPerDest = service.createSamplesPerDestination(destinations, ogSamples, destTissueMap);

        assertThat(samplesPerDest).hasSize(4);
        assertThat(samplesPerDest.get(0)).containsExactly(ogSamples.get(0), ogSamples.get(1));
        assertThat(samplesPerDest.get(1)).containsExactly(createdSamples[0], createdSamples[1]);
        assertThat(samplesPerDest.get(2)).containsExactly(createdSamples[0], createdSamples[1]);
        assertThat(samplesPerDest.get(3)).containsExactly(createdSamples[2], createdSamples[3]);
        verify(service, times(4)).createSample(any(), any());
    }

    @Test
    void testCreateDestinations() {
        Sample[] samples = EntityFactory.makeSamples(4);
        LabwareType[] lts = new LabwareType[]{
                EntityFactory.makeLabwareType(1,1,"lt1"),
                EntityFactory.makeLabwareType(1,1, "lt2")
        };
        List<List<Sample>> sampleLists = List.of(
                List.of(samples[0], samples[1]),
                List.of(samples[2], samples[3])
        );
        List<PotProcessingDestination> destinations = Arrays.stream(lts)
                .map(lt -> new PotProcessingDestination(lt.getName(), "fix1"))
                .toList();
        Labware[] newLabware = Arrays.stream(lts).map(EntityFactory::makeEmptyLabware).toArray(Labware[]::new);
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lts);

        when(mockLwService.create(any(LabwareType.class))).thenAnswer(invocation -> {
            LabwareType lt = invocation.getArgument(0);
            return Arrays.stream(newLabware).filter(lw -> lw.getLabwareType()==lt).findAny().orElseThrow();
        });

        List<Labware> dests = service.createDestinations(destinations, ltMap, sampleLists);
        Zip.of(dests.stream(), sampleLists.stream()).forEach((lw, sampleList) ->
                assertThat(lw.getFirstSlot().getSamples()).containsExactlyElementsOf(sampleList)
        );
        dests.forEach(lw -> verify(mockSlotRepo).save(lw.getFirstSlot()));
        verify(mockLwService, times(2)).create(any(LabwareType.class));
    }

    @Test
    public void testCreateOps() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Pot processing", null);
        when(mockOpTypeRepo.getByName("Pot processing")).thenReturn(opType);
        LabwareType lt = EntityFactory.getTubeType();
        Labware source = EntityFactory.makeEmptyLabware(lt);
        Sample[] samples = EntityFactory.makeSamples(4);
        List<List<Sample>> sampleLists = Arrays.stream(new int[][] { {0,1}, {2,3}, {2,3}})
                .map(arr -> Arrays.stream(arr).mapToObj(i -> samples[i]).toList())
                .toList();
        List<Sample> sourceSamples = List.of(samples[0], samples[1]);
        final Slot srcSlot = source.getFirstSlot();
        srcSlot.getSamples().addAll(sourceSamples);

        List<Labware> destLabware = sampleLists.stream()
                .map(sams -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.getFirstSlot().getSamples().addAll(sams);
                    return lw;
                })
                .toList();

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
                .toList();
        doReturn(ops.get(0), ops.get(1), ops.get(2)).when(service).createOp(any(), any(), any(), any(), any(), any());

        assertEquals(ops, service.createOps(ppds, sampleLists, user, source, sourceSamples, destLabware, commentMap));

        verify(service, times(ppds.size())).createOp(any(), any(), any(), any(), any(), any());
        for (int i = 0; i < ppds.size(); ++i) {
            verify(service).createOp(opType, user, srcSlot, destLabware.get(i).getFirstSlot(), sourceSamples, sampleLists.get(i));
        }

        List<OperationComment> expectedOpComs = IntStream.range(1,3).mapToObj(i ->
            new OperationComment(null, commentMap.get(i), ops.get(i).getId(), null, null, destLabware.get(i).getId())
        ).collect(toList());
        verify(mockOpComRepo).saveAll(expectedOpComs);
    }

    @Test
    public void testCreateOp() {
        OperationType opType = EntityFactory.makeOperationType("Pot processing", null);
        User user = EntityFactory.getUser();
        Sample[] samples = EntityFactory.makeSamples(4);
        List<Sample> sourceSamples = List.of(samples[0], samples[1]);
        List<Sample> destSamples = List.of(samples[2], samples[3]);
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw0 = EntityFactory.makeEmptyLabware(lt);
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw0.getFirstSlot().getSamples().addAll(sourceSamples);
        lw1.getFirstSlot().getSamples().addAll(destSamples);
        Slot src = lw0.getFirstSlot();
        Slot dst = lw1.getFirstSlot();
        Operation op = new Operation();
        op.setId(500);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.createOp(opType, user, src, dst, sourceSamples, destSamples));
        List<Action> expectedActions = Zip.of(sourceSamples.stream(), destSamples.stream())
                .map((srcSam, dstSam) -> new Action(null, null, src, dst, dstSam, srcSam))
                .toList();

        verify(mockOpService).createOperation(opType, user, expectedActions, null);
    }
}