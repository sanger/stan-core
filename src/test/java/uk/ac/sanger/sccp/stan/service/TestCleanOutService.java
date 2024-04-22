package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;
import uk.ac.sanger.sccp.stan.request.CleanOutRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToList;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/** Test {@link CleanOutServiceImp} */
class TestCleanOutService {
    @Mock
    private ValidationHelperFactory mockValFactory;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private SlotRepo mockSlotRepo;

    @InjectMocks
    private CleanOutServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    ValidationHelper setupVal() {
        ValidationHelper val = mock(ValidationHelper.class);
        Set<String> problems = new HashSet<>();
        when(val.getProblems()).thenReturn(problems);
        return val;
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    void testPerform_noRequest(boolean hasUser) {
        User user = hasUser ? EntityFactory.getUser() : null;
        ValidationHelper val = setupVal();
        when(mockValFactory.getHelper()).thenReturn(val);
        List<String> expectedProblems = (hasUser ? List.of("No request supplied.") : List.of("No user supplied.", "No request supplied."));
        assertValidationException(() -> service.perform(user, null), expectedProblems);
        verify(val, never()).checkOpType(any(), any(OperationTypeFlag.class));
        verify(service, never()).loadLabware(any(), any());
        verify(service, never()).checkAddresses(any(), any(), any());
        verify(service, never()).checkSlots(any(), any(), any());
        verify(service, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void testPerform_invalid() {
        User user = EntityFactory.getUser();
        final ValidationHelper val = setupVal();
        when(mockValFactory.getHelper()).thenReturn(val);
        Labware lw = EntityFactory.getTube();
        when(val.checkOpType(any(), any(OperationTypeFlag.class))).then(invocation -> {
            val.getProblems().add("Bad op type.");
            return null;
        });
        doAnswer(invocation -> {
            ValidationHelper valArg = invocation.getArgument(0);
            valArg.getProblems().add("Bad lw.");
            return lw;
        }).when(service).loadLabware(any(), any());
        mayAddProblem("Bad work.", null).when(mockWorkService).validateUsableWork(any(), any());
        mayAddProblem("Bad addresses.").when(service).checkAddresses(any(), any(), any());
        Address A2 = new Address(1,2);
        Address A3 = new Address(1,3);
        List<Address> addresses = List.of(A2, A3);
        CleanOutRequest request = new CleanOutRequest("STAN-1", addresses, "SGP1");

        assertValidationException(() -> service.perform(user, request), List.of(
                "Bad op type.", "Bad lw.", "Bad addresses.", "Bad work."
        ));
        verify(val).checkOpType("Clean out", OperationTypeFlag.IN_PLACE);
        verify(mockWorkService).validateUsableWork(any(), eq("SGP1"));
        verify(service).loadLabware(val, request.getBarcode());
        verify(service).checkAddresses(any(), same(lw), eq(addresses));
        verify(service, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void testPerform() {
        User user = EntityFactory.getUser();
        final ValidationHelper val = setupVal();
        when(mockValFactory.getHelper()).thenReturn(val);
        Labware lw = EntityFactory.getTube();
        Address A2 = new Address(1,2), A3 = new Address(1,3);
        List<Address> addresses = List.of(A2, A3);
        Work work = EntityFactory.makeWork("SGP1");
        CleanOutRequest request = new CleanOutRequest("STAN-1", addresses, work.getWorkNumber());
        OperationType opType = EntityFactory.makeOperationType("Clean out", null, OperationTypeFlag.IN_PLACE);
        doReturn(opType).when(val).checkOpType(any(), any(OperationTypeFlag.class));
        doReturn(lw).when(service).loadLabware(any(), any());
        doReturn(work).when(mockWorkService).validateUsableWork(any(), any());
        doNothing().when(service).checkAddresses(any(), any(), any());
        OperationResult opres = new OperationResult(List.of(), List.of(lw));
        doReturn(opres).when(service).record(any(), any(), any(), any(), any());

        assertSame(opres, service.perform(user, request));
        verify(val).checkOpType("Clean out", OperationTypeFlag.IN_PLACE);
        verify(service).loadLabware(val, "STAN-1");
        verify(service).checkAddresses(any(), same(lw), eq(addresses));
        verify(service).record(user, opType, work, lw, addresses);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void testLoadLabware(boolean ok) {
        ValidationHelper val = setupVal();
        Labware lw = ok ? EntityFactory.getTube() : null;
        String problem = ok ? null : "Bad lw.";
        if (ok) {
            when(val.checkLabware(any())).thenReturn(UCMap.from(Labware::getBarcode, lw));
        } else {
            when(val.checkLabware(any())).then(invocation -> {
                val.getProblems().add(problem);
                return new UCMap<>(0);
            });
        }
        String bc = lw==null ? "STAN-1" : lw.getBarcode();
        assertSame(lw, service.loadLabware(val, bc));
        verify(val).checkLabware(List.of(bc));
        assertProblem(val.getProblems(), problem);
    }

    @ParameterizedTest
    @MethodSource("checkAddressesArgs")
    void testCheckAddresses(Labware lw, List<Address> addresses, Set<Address> expectedSlotChecks, List<String> expectedProblems) {
        doNothing().when(service).checkSlots(any(), any(), any());
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkAddresses(problems, lw, addresses);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (nullOrEmpty(expectedSlotChecks)) {
            verify(service, never()).checkSlots(any(), any(), any());
        } else {
            verify(service).checkSlots(same(problems), same(lw), eq(expectedSlotChecks));
        }
    }

    static Stream<Arguments> checkAddressesArgs() {
        Labware lw = EntityFactory.getTube();
        Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        return Arrays.stream(new Object[][] {
                {lw, List.of(A1, A3), Set.of(A1, A3), null},
                {null, List.of(A1, A3), null, null},
                {null, null, null, "No slot addresses supplied."},
                {null, List.of(A1, A3, A1, A2, A3), null, "Repeated slot address: [A1, A3]"},
                {null, Arrays.asList(A1, A2, null), null, "Null supplied as slot address."},
                {lw, List.of(A1, A3, A3, A2), Set.of(A1, A2, A3), "Repeated slot address: [A3]"},
                {lw, Arrays.asList(A1, null, A1), Set.of(A1), List.of("Null supplied as slot address.", "Repeated slot address: [A1]")},
        }).map(arr -> {
            arr[3] = objToList(arr[3]);
            return Arguments.of(arr);
        });
    }

    @ParameterizedTest
    @MethodSource("checkSlotsArgs")
    void testCheckSlots(Labware lw, Set<Address> addresses, List<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkSlots(problems, lw, addresses);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkSlotsArgs() {
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, null, sample, null);
        lw.setBarcode("STAN-A1");
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3),
                B1 = new Address(2,1), B2 = new Address(2,2), B3 = new Address(2,3);
        return Arrays.stream(new Object[][] {
                {A1, B1},
                {A1, A2, B2, "Slot in labware STAN-A1 is empty: [A2, B2]"},
                {A1, A3, B3, "No slot found in labware STAN-A1 at address: [A3, B3]"},
                {A1, A2, A3, "Slot in labware STAN-A1 is empty: [A2]", "No slot found in labware STAN-A1 at address: [A3]"},
        }).map(arr -> {
            Set<Address> addresses = new LinkedHashSet<>();
            List<String> problems = new ArrayList<>();
            for (Object x : arr) {
                if (x instanceof String) {
                    problems.add((String) x);
                } else {
                    addresses.add((Address) x);
                }
            }
            return Arguments.of(lw, addresses, problems);
        });
    }

    @Test
    void testRecord() {
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Tissue tissue = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        List<Sample> samples = IntStream.range(0,3)
                .mapToObj(i -> new Sample(10+i, 20+i, tissue, bs))
                .toList();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final Address A2 = new Address(1,2), A3 = new Address(1,3);
        lw.getFirstSlot().setSamples(samples.subList(0,2));
        lw.getSlot(A2).setSamples(samples.subList(1,3));
        lw.getSlot(A3).setSamples(samples.subList(2,3));
        Work work = EntityFactory.makeWork("SGP1");
        Slot slot2 = lw.getSlot(A2);
        Slot slot3 = lw.getSlot(A3);
        List<Slot> slots = List.of(slot2, slot3);
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Clean out", null, OperationTypeFlag.IN_PLACE);
        Operation op = EntityFactory.makeOpForSlots(opType, List.of(slot2), slots, user);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        OperationResult opres = service.record(user, opType, work, lw, List.of(A2, A3));
        assertThat(opres.getOperations()).containsExactly(op);
        assertThat(opres.getLabware()).containsExactly(lw);
        List<Action> expectedActions = List.of(
                new Action(null, null, slot2, slot2, samples.get(1), samples.get(1)),
                new Action(null, null, slot2, slot2, samples.get(2), samples.get(2)),
                new Action(null, null, slot3, slot3, samples.get(2), samples.get(2))
        );
        verify(mockOpService).createOperation(opType, user, expectedActions, null);
        assertThat(lw.getFirstSlot().getSamples()).containsExactlyElementsOf(samples.subList(0,2));
        assertThat(slot2.getSamples()).isEmpty();
        assertThat(slot3.getSamples()).isEmpty();
        verify(mockSlotRepo).saveAll(slots);
        verify(mockWorkService).link(work, opres.getOperations());
        verifyNoMoreInteractions(mockOpService);
        verifyNoMoreInteractions(mockSlotRepo);
    }
}