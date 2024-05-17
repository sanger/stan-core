package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.RoiMetricRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest.SampleMetric;
import uk.ac.sanger.sccp.stan.service.RoiMetricServiceImp.MetricValidation;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.addProblem;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/** Test {@link RoiMetricServiceImp} */
class TestRoiMetricService {
    @Mock
    private Clock mockClock;
    @Mock
    private ValidationHelperFactory mockValFactory;
    @Mock
    private RoiMetricValidationService mockValService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private RoiMetricRepo mockRoiMetricRepo;

    @InjectMocks
    private RoiMetricServiceImp service;

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

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    void testPerform(boolean valid) {
        Set<String> problems = valid ? Set.of() : Set.of("Bad stuff");
        MetricValidation val = new MetricValidation(problems);
        User user = EntityFactory.getUser();
        SampleMetricsRequest request = new SampleMetricsRequest("op", "STAN-1", "SGP1", List.of());
        doReturn(val).when(service).validate(user, request);

        if (valid) {
            OperationResult opres = new OperationResult();
            doReturn(opres).when(service).record(any(), any());
            assertSame(opres, service.perform(user, request));
        } else {
            assertValidationException(() -> service.perform(user, request), problems);
        }
        verify(service).validate(user, request);
        if (valid) {
            verify(service).record(user, val);
        } else {
            verify(service, never()).record(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testValidate_noRequest(boolean userSupplied) {
        ValidationHelper helper = mock(ValidationHelper.class);
        final Set<String> problems = new LinkedHashSet<>();
        when(helper.getProblems()).thenReturn(problems);
        when(mockValFactory.getHelper()).thenReturn(helper);

        User user = userSupplied ? EntityFactory.getUser() : null;
        MetricValidation val = service.validate(user, null);
        verify(helper).getProblems();
        verifyNoMoreInteractions(helper);
        verifyNoInteractions(mockValService);
        assertSame(problems, val.problems);
        if (userSupplied) {
            assertThat(problems).containsExactly("No request supplied.");
        } else {
            assertThat(problems).containsExactlyInAnyOrder("No request supplied.", "No user supplied.");
        }
    }

    @Test
    void testValidate_problems() {
        ValidationHelper helper = mock(ValidationHelper.class);
        final Set<String> problems = new LinkedHashSet<>();
        when(helper.getProblems()).thenReturn(problems);
        when(mockValFactory.getHelper()).thenReturn(helper);
        List<SampleMetric> initialMetrics = List.of(new SampleMetric("R1", "N1", "V1"));

        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        Work work = EntityFactory.makeWork("SGP1");
        OperationType opType = EntityFactory.makeOperationType("opname", null, OperationTypeFlag.IN_PLACE);

        SampleMetricsRequest request = new SampleMetricsRequest("opname", lw.getBarcode(), "SGP1", initialMetrics);

        List<SampleMetric> sanitisedMetrics = List.of(new SampleMetric("R2", "N2", "V2"));

        when(helper.checkLabware(any())).then(addProblemAndReturn(helper, "Bad lw", UCMap.from(Labware::getBarcode, lw)));
        when(helper.checkWork(anyString())).then(addProblemAndReturn(helper, "Bad work", work));
        when(helper.checkOpType(any(), any(OperationTypeFlag.class))).then(addProblemAndReturn(helper, "Bad op type", opType));
        when(mockValService.validateMetrics(any(), any(), any())).then(addProblem("Bad metrics", sanitisedMetrics));

        MetricValidation val = service.validate(user, request);

        verify(helper, atLeastOnce()).getProblems();
        verify(helper).checkLabware(List.of(lw.getBarcode()));
        verify(helper).checkWork("SGP1");
        verify(helper).checkOpType("opname", OperationTypeFlag.IN_PLACE);
        verifyNoMoreInteractions(helper);
        verify(mockValService).validateMetrics(problems, lw, initialMetrics);

        assertThat(problems).containsExactlyInAnyOrder("Bad lw", "Bad work", "Bad op type", "Bad metrics");
        assertSame(problems, val.problems);
        assertSame(lw, val.labware);
        assertSame(work, val.work);
        assertSame(opType, val.opType);
        assertSame(sanitisedMetrics, val.metrics);
    }

    <R> Answer<R> addProblemAndReturn(ValidationHelper helper, String problem, R returnValue) {
        return invocation -> {
            if (problem!=null) {
                helper.getProblems().add(problem);
            }
            return returnValue;
        };
    }

    @Test
    void testValidate_valid() {
        ValidationHelper helper = mock(ValidationHelper.class);
        final Set<String> problems = new LinkedHashSet<>();
        when(helper.getProblems()).thenReturn(problems);
        when(mockValFactory.getHelper()).thenReturn(helper);
        List<SampleMetric> initialMetrics = List.of(new SampleMetric("R1", "N1", "V1"));

        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        Work work = EntityFactory.makeWork("SGP1");
        OperationType opType = EntityFactory.makeOperationType("opname", null, OperationTypeFlag.IN_PLACE);

        SampleMetricsRequest request = new SampleMetricsRequest("opname", lw.getBarcode(), "SGP1", initialMetrics);

        List<SampleMetric> sanitisedMetrics = List.of(new SampleMetric("R2", "N2", "V2"));

        when(helper.checkLabware(any())).thenReturn(UCMap.from(Labware::getBarcode, lw));
        when(helper.checkWork(anyString())).thenReturn(work);
        when(helper.checkOpType(any(), any(OperationTypeFlag.class))).thenReturn(opType);
        when(mockValService.validateMetrics(any(), any(), any())).thenReturn(sanitisedMetrics);

        MetricValidation val = service.validate(user, request);

        verify(helper, atLeastOnce()).getProblems();
        verify(helper).checkLabware(List.of(lw.getBarcode()));
        verify(helper).checkWork("SGP1");
        verify(helper).checkOpType("opname", OperationTypeFlag.IN_PLACE);
        verifyNoMoreInteractions(helper);
        verify(mockValService).validateMetrics(problems, lw, initialMetrics);

        assertThat(problems).isEmpty();
        assertSame(problems, val.problems);
        assertSame(lw, val.labware);
        assertSame(work, val.work);
        assertSame(opType, val.opType);
        assertSame(sanitisedMetrics, val.metrics);
    }


    @Test
    void testRecord() {
        User user = EntityFactory.getUser();
        MetricValidation val = new MetricValidation(Set.of());
        val.labware = EntityFactory.getTube();
        val.opType = EntityFactory.makeOperationType("opname", null, OperationTypeFlag.IN_PLACE);
        val.work = EntityFactory.makeWork("SGP1");
        val.metrics = List.of(new SampleMetric("R1", "N1", "V1"));
        Operation op = new Operation();
        op.setId(500);

        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        doNothing().when(service).deprecateOldMetrics(any(), any(), any());
        doNothing().when(service).recordMetrics(any(), any(), any());

        OperationResult opres = service.record(user, val);

        verify(mockOpService).createOperationInPlace(val.opType, user, val.labware, null, null);
        verify(service).deprecateOldMetrics(mockClock, val.labware.getId(), val.metrics);
        verify(service).recordMetrics(val.labware.getId(), op.getId(), val.metrics);
        verify(mockWorkService).link(val.work, List.of(op));

        assertThat(opres.getLabware()).containsExactly(val.labware);
        assertThat(opres.getOperations()).containsExactly(op);
    }

    @Test
    void testDeprecateOldMetrics() {
        LocalDateTime time = LocalDateTime.of(2024,1,17,9,0);
        Clock fixedClock = Clock.fixed(time.toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
        Integer lwId = 45;
        List<SampleMetric> newMetrics = List.of(
                new SampleMetric("R1", "N1", "V1"),
                new SampleMetric("R2", "N2", "V2"),
                new SampleMetric("R1", "N1B", "V1B")
        );
        Set<String> rois = Set.of("R1", "R2");

        service.deprecateOldMetrics(fixedClock, lwId, newMetrics);
        verify(mockRoiMetricRepo).deprecateMetrics(lwId, rois, time);
    }

    @Test
    void testRecordMetrics() {
        List<SampleMetric> newMetrics = List.of(
                new SampleMetric("R1", "N1", "V1"),
                new SampleMetric("R2", "N2", "V2")
        );
        Integer lwId = 45;
        Integer opId = 75;
        service.recordMetrics(lwId, opId, newMetrics);
        verify(mockRoiMetricRepo).saveAll(List.of(
                new RoiMetric(lwId, opId, "R1", "N1", "V1"),
                new RoiMetric(lwId, opId, "R2", "N2", "V2")
        ));
    }
}