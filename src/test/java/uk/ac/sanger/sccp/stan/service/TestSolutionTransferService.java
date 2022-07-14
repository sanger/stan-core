package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SolutionTransferRequest;
import uk.ac.sanger.sccp.stan.request.SolutionTransferRequest.SolutionTransferLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SolutionTransferServiceImp}
 */
public class TestSolutionTransferService {
    private SolutionRepo mockSolutionRepo;
    private OperationSolutionRepo mockOpSolRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private LabwareValidatorFactory mockLwValFactory;

    private SolutionTransferServiceImp service;

    @BeforeEach
    void setup() {
        mockSolutionRepo = mock(SolutionRepo.class);
        mockOpSolRepo = mock(OperationSolutionRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);

        service = spy(new SolutionTransferServiceImp(mockSolutionRepo, mockOpSolRepo, mockOpTypeRepo, mockLwRepo,
                mockWorkService, mockOpService, mockLwValFactory));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform(boolean valid) {
        User user = EntityFactory.getUser();
        Work work = new Work(10, "SGP10", null, null, null, null);
        Labware lw = EntityFactory.getTube();
        Solution solution = new Solution(1, "Sol1");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Solution> solutionMap = UCMap.from(Solution::getName, solution);
        SolutionTransferRequest request = new SolutionTransferRequest(work.getWorkNumber(),
                List.of(new SolutionTransferLabware(lw.getBarcode(), solution.getName())));
        OperationResult opRes;
        if (valid) {
            when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
            doReturn(lwMap).when(service).loadLabware(any(), any());
            doReturn(solutionMap).when(service).loadSolutions(any(), any());
            opRes = new OperationResult(List.of(new Operation()), List.of(lw));
            doReturn(opRes).when(service).record(any(), any(), any(), any(), any());
        } else {
            when(mockWorkService.validateUsableWork(any(), any())).then(Matchers.addProblem("Bad work.", work));
            doAnswer(Matchers.addProblem("Bad labware.", lwMap)).when(service).loadLabware(any(), any());
            doAnswer(Matchers.addProblem("Bad solution.", solutionMap)).when(service).loadSolutions(any(), any());
            opRes = null;
        }

        if (valid) {
            assertSame(opRes, service.perform(user, request));
        } else {
            Matchers.assertValidationException(() -> service.perform(user, request),
                    "The request could not be validated.",
                    "Bad work.", "Bad labware.", "Bad solution.");
        }

        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).loadLabware(anyCollection(), eq(request.getLabware()));
        verify(service).loadSolutions(anyCollection(), eq(request.getLabware()));

        if (valid) {
            verify(service).record(user, work, request.getLabware(), lwMap, solutionMap);
        } else {
            verify(service, never()).record(any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware_none(boolean anyNull) {
        final List<String> problems = new ArrayList<>(1);
        assertThat(service.loadLabware(problems, anyNull ? List.of(new SolutionTransferLabware()) : List.of())).isEmpty();
        assertThat(problems).containsExactly(anyNull ? "Labware barcode missing." : "No labware specified.");
        verifyNoInteractions(mockLwValFactory);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware_invalid(boolean anyNull) {
        Labware lw = EntityFactory.getTube();

        final SolutionTransferLabware stl = new SolutionTransferLabware(lw.getBarcode(), null);
        final List<SolutionTransferLabware> stls = (anyNull ? List.of(new SolutionTransferLabware(), stl) : List.of(stl));
        final String badLwMsg = "Bad labware.";

        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.getLabware()).thenReturn(List.of(lw));
        when(val.getErrors()).thenReturn(List.of(badLwMsg));

        final List<String> problems = new ArrayList<>(anyNull ? 2 : 1);
        var lwMap = service.loadLabware(problems, stls);
        assertThat(lwMap).hasSize(1);
        assertSame(lw, lwMap.get(lw.getBarcode()));
        if (anyNull) {
            assertThat(problems).containsExactly("Labware barcode missing.", badLwMsg);
        } else {
            assertThat(problems).containsExactly(badLwMsg);
        }
        verify(val).loadLabware(mockLwRepo, List.of(lw.getBarcode()));
        verify(val).validateSources();
    }

    @Test
    public void testLoadLabware() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        final List<SolutionTransferLabware> stls = List.of(
                new SolutionTransferLabware(lw1.getBarcode(), null),
                new SolutionTransferLabware(lw2.getBarcode(), null)
        );

        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        when(val.getLabware()).thenReturn(List.of(lw1, lw2));
        when(val.getErrors()).thenReturn(List.of());

        final List<String> problems = new ArrayList<>(0);
        var lwMap = service.loadLabware(problems, stls);
        assertThat(lwMap).hasSize(2);
        assertSame(lw1, lwMap.get(lw1.getBarcode()));
        assertSame(lw2, lwMap.get(lw2.getBarcode()));
        assertThat(problems).isEmpty();
        verify(val).loadLabware(mockLwRepo, List.of(lw1.getBarcode(), lw2.getBarcode()));
        verify(val).validateSources();
    }

    @ParameterizedTest
    @MethodSource("loadSolutionsArgs")
    public void testLoadSolutions(List<String> solutionBarcodes, List<Solution> solutions, List<String> expectedProblems) {
        when(mockSolutionRepo.findAllByNameIn(any())).then(invocation -> {
            Collection<String> strings = invocation.getArgument(0);
            return solutions.stream()
                    .filter(sol -> strings.stream().anyMatch(s -> s.equalsIgnoreCase(sol.getName())))
                    .collect(toList());
        });
        List<SolutionTransferLabware> stls = solutionBarcodes.stream()
                .map(string -> new SolutionTransferLabware(null, string))
                .collect(toList());
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        var map = service.loadSolutions(problems, stls);
        assertThat(map.values()).containsExactlyInAnyOrderElementsOf(solutions);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> loadSolutionsArgs() {
        Solution sol1 = new Solution(1, "Sol1");
        Solution sol2 = new Solution(2, "Sol2");

        return Arrays.stream(new Object[][] {
                { List.of(), List.of(), List.of() },
                { List.of("Sol1", "Sol2", "Sol2"), List.of(sol1, sol2), List.of() },
                { List.of("Sol1", ""), List.of(sol1), List.of("Solution name missing.") },
                { List.of(""), List.of(), List.of("Solution name missing.") },
                { List.of("Sol1", "Sol404"), List.of(sol1), List.of("Unknown solution: [\"Sol404\"]") },
        }).map(Arguments::of);
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        Work work = new Work(10, "SGP10", null, null, null, null);
        final Labware lw = EntityFactory.getTube();
        var lwMap = UCMap.from(Labware::getBarcode, lw);
        var solMap = UCMap.from(Solution::getName, new Solution(1, "Sol1"));
        final Operation op = new Operation();
        op.setId(30);
        List<Operation> ops = List.of(op);
        doReturn(ops).when(service).recordOps(any(), any(), any(), any());
        List<SolutionTransferLabware> stls = List.of(new SolutionTransferLabware(lw.getBarcode(), "Sol1"));

        OperationResult opRes = service.record(user, work, stls, lwMap, solMap);
        verify(service).recordOps(user, stls, lwMap, solMap);
        assertThat(opRes.getLabware()).containsExactly(lw);
        assertThat(opRes.getOperations()).containsExactly(op);
        verify(mockWorkService).link(work, ops);
    }

    @Test
    public void testRecordOps() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Solution transfer", null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        final Labware lw1 = EntityFactory.getTube();
        final Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        final Solution sol1 = new Solution(1, "Sol1");
        final Solution sol2 = new Solution(2, "Sol2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        UCMap<Solution> solMap = UCMap.from(Solution::getName, sol1, sol2);
        Operation op1 = new Operation(101, opType, null, null, null);
        Operation op2 = new Operation(102, opType, null, null, null);
        doReturn(op1, op2).when(service).recordOp(any(), any(), any(), any());

        assertThat(service.recordOps(user, List.of(new SolutionTransferLabware(lw1.getBarcode(), "Sol1"),
                new SolutionTransferLabware(lw2.getBarcode(), "Sol2")), lwMap, solMap))
                .containsExactly(op1, op2);
        verify(service).recordOp(opType, user, lw1, sol1);
        verify(service).recordOp(opType, user, lw2, sol2);
    }

    @Test
    public void testRecordOp() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Solution transfer", null, OperationTypeFlag.IN_PLACE);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        Solution sol = new Solution(10, "Sol10");
        List<Labware> lwList = List.of(lw);
        Operation op = EntityFactory.makeOpForLabware(opType, lwList, lwList, user);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);

        assertSame(op, service.recordOp(opType, user, lw, sol));
        verify(mockOpSolRepo).saveAll(List.of(new OperationSolution(op.getId(), sol.getId(), lw.getId(), sample.getId())));
    }
}