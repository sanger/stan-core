package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.Roi;
import uk.ac.sanger.sccp.stan.repo.RoiRepo;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest.SampleMetric;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;
import static uk.ac.sanger.sccp.stan.Matchers.mayAddProblem;

/** Test{@link RoiMetricValidationService} */
class TestRoiMetricValidationService {
    private RoiRepo mockRoiRepo;
    private RoiMetricValidationService service;

    @BeforeEach
    void setup() {
        mockRoiRepo = mock(RoiRepo.class);
        service = spy(new RoiMetricValidationService(mockRoiRepo));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true"})
    void testValidateMetrics_noMetrics(boolean lwIsNull, boolean metricsIsNull) {
        Labware lw = lwIsNull ? EntityFactory.getTube() : null;
        List<SampleMetric> metrics = metricsIsNull ? null : List.of();
        List<String> problems = new ArrayList<>(1);
        assertThat(service.validateMetrics(problems, lw, metrics)).isNullOrEmpty();
        assertProblem(problems, "No metrics supplied.");
        verify(service, never()).sanitise(any(), any());
        verify(service, never()).checkRois(any(), any(), any());
        verify(service, never()).checkDupes(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,false"})
    void testValidateMetrics(boolean anyLabware, boolean otherProblems) {
        List<SampleMetric> initialMetrics = List.of(new SampleMetric("R1", "N1", "V1"));
        List<SampleMetric> sanitisedMetrics = List.of(new SampleMetric("R2", "N2", "V2"));
        Labware lw = anyLabware ? EntityFactory.getTube() : null;
        mayAddProblem(otherProblems ? "Bad values" : null, sanitisedMetrics).when(service).sanitise(any(), any());
        mayAddProblem(otherProblems ? "Bad rois" : null).when(service).checkRois(any(), any(), any());
        mayAddProblem(otherProblems ? "Bad dupes" : null).when(service).checkDupes(any(), any());
        List<String> expectedProblems = otherProblems ? List.of("Bad values", "Bad rois", "Bad dupes") : List.of();
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        assertSame(sanitisedMetrics, service.validateMetrics(problems, lw, initialMetrics));
        verify(service).sanitise(problems, initialMetrics);
        if (lw!=null) {
            verify(service).checkRois(problems, lw, sanitisedMetrics);
        } else {
            verify(service, never()).checkRois(any(), any(), any());
        }
        verify(service).checkDupes(problems, sanitisedMetrics);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @MethodSource("sanitiseArgs")
    void testSanitise(List<SampleMetric> sms, List<String> expectedProblems, List<SampleMetric> expectedSms) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        assertThat(service.sanitise(problems, sms)).containsExactlyInAnyOrderElementsOf(expectedSms);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> sanitiseArgs() {
        final String missingRoi = "ROI missing from metric.", missingName = "Name missing from metric.";
        SampleMetric sane1 = new SampleMetric("R1", "N1", "V1");
        SampleMetric sane2 = new SampleMetric("R2", "N2", "V2");
        List<SampleMetric> saneMetrics = List.of(sane1, sane2);
        SampleMetric input1 = new SampleMetric("      ", "N1", "V1");
        SampleMetric input2 = new SampleMetric("R2", null, "V2");
        SampleMetric input3 = new SampleMetric("R3", "N3", "");
        SampleMetric input4 = new SampleMetric("R1  ", "   N1", "  V1  ");
        return Arrays.stream(new Object[][] {
                {saneMetrics, List.of(), saneMetrics},
                {List.of(input4, sane2), List.of(), saneMetrics},
                {List.of(input1, sane1, sane2), List.of(missingRoi), saneMetrics},
                {List.of(sane1, input2, sane2), List.of(missingName), saneMetrics},
                {List.of(sane1, sane2, input3), List.of(), saneMetrics},
                {List.of(input1, input2, input3), List.of(missingRoi, missingName), List.of()},
        }).map(Arguments::of);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({"A,A", "'  A ',A", "'   ',", ","})
    void testSanitiseField(String input, String expected) {
        Function<SampleMetric, String> getter = mock(Function.class);
        BiConsumer<SampleMetric, String> setter = mock(BiConsumer.class);
        SampleMetric sm = mock(SampleMetric.class);
        when(getter.apply(sm)).thenReturn(input);
        boolean present = service.sanitiseField(sm, getter, setter);
        verify(getter).apply(sm);
        if (input!=null) {
            verify(setter).accept(sm, expected);
        } else {
            verifyNoInteractions(setter);
        }
        verifyNoMoreInteractions(sm);
        assertEquals(expected!=null, present);
    }

    @Test
    void testCheckDupes_dupes() {
        List<SampleMetric> sms = List.of(
                new SampleMetric("r1", "n1", "v1"),
                new SampleMetric("R1", "N1", "v2"),
                new SampleMetric("ALPHa", "bETa", "V3"),
                new SampleMetric("Alpha", "Beta", "v4"),
                new SampleMetric("alpha", "beta", "v5"),
                new SampleMetric("R1", "Beta", "V6")
        );
        List<String> problems = new ArrayList<>(1);
        service.checkDupes(problems, sms);
        assertThat(problems).containsExactly("Duplicate metrics supplied for the same roi: " +
                "[(roi=\"R1\", name=\"N1\"), (roi=\"Alpha\", name=\"Beta\")]");
    }

    @Test
    void testCheckDupes_noDupes() {
        List<SampleMetric> sms = List.of(
                new SampleMetric("Alpha", "Beta", "v1"),
                new SampleMetric("Alpha", "Gamma", "v1"),
                new SampleMetric("Delta", "Beta", "v1")
        );
        List<String> problems = new ArrayList<>(0);
        service.checkDupes(problems, sms);
        assertThat(problems).isEmpty();
    }

    @Test
    void testCheckRois_noRois() {
        when(mockRoiRepo.findAllBySlotIdIn(any())).thenReturn(List.of());
        List<SampleMetric> sms = List.of(
                new SampleMetric("r1", "n1", "v1"),
                new SampleMetric("r2", "n2", "v2")
        );
        Labware lw = EntityFactory.getTube();
        List<String> problems = new ArrayList<>(1);
        service.checkRois(problems, lw, sms);
        verify(mockRoiRepo).findAllBySlotIdIn(List.of(lw.getFirstSlot().getId()));
        assertProblem(problems, "No ROIs have been recorded in labware "+lw.getBarcode()+".");
    }

    @Test
    void testCheckRois_mismatch() {
        when(mockRoiRepo.findAllBySlotIdIn(any())).thenReturn(List.of(
                new Roi(1, 11, 21, "Alpha"),
                new Roi(2, 12, 22, "Beta")
        ));
        List<SampleMetric> sms = List.of(
                new SampleMetric("ALPHA", "N1", "V1"),
                new SampleMetric("alpha", "N2", "V2"),
                new SampleMetric("BETA", "N3", "V3"),
                new SampleMetric("Delta", "N4", "V4"),
                new SampleMetric("Epsilon", "N5", "V5")
        );
        List<String> problems = new ArrayList<>(1);
        Labware lw = EntityFactory.getTube();
        service.checkRois(problems, lw, sms);
        verify(mockRoiRepo).findAllBySlotIdIn(List.of(lw.getFirstSlot().getId()));

        assertProblem(problems, "ROIs not present in labware "+lw.getBarcode()+": " +
                "[\"Delta\", \"Epsilon\"]");
    }

    @Test
    void testCheckRois_valid() {
        when(mockRoiRepo.findAllBySlotIdIn(any())).thenReturn(List.of(
                new Roi(1, 11, 21, "Alpha"),
                new Roi(2, 12, 22, "Beta")
        ));
        List<SampleMetric> sms = List.of(
                new SampleMetric("ALPHA", "N1", "V1"),
                new SampleMetric("alpha", "N2", "V2"),
                new SampleMetric("BETA", "N3", "V3")
        );
        List<String> problems = new ArrayList<>(0);
        Labware lw = EntityFactory.getTube();
        service.checkRois(problems, lw, sms);
        verify(mockRoiRepo).findAllBySlotIdIn(List.of(lw.getFirstSlot().getId()));
        assertThat(problems).isEmpty();
        assertThat(sms).containsExactly(
                new SampleMetric("Alpha", "N1", "V1"),
                new SampleMetric("Alpha", "N2", "V2"),
                new SampleMetric("Beta", "N3", "V3")
        );
    }
}