package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.BioRiskRepo;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link BioRiskService} */
class TestBioRiskService extends AdminServiceTestUtils<BioRisk, BioRiskRepo, BioRiskService> {
    public TestBioRiskService() {
        super("BioRisk", BioRisk::new,
                BioRiskRepo::findByCode, "Code not supplied.");
    }

    @BeforeEach
    void setUp() {
        mockRepo = mock(BioRiskRepo.class);
        service = spy(new BioRiskService(mockRepo, simpleValidator(), mockTransactor, mockNotifyService));
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(BioRiskService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(BioRiskService::setEnabled,
                string, newValue, oldValue, expectedException);
    }

    @Test
    void testLoadBioRiskMap() {
        List<BioRisk> risks = List.of(new BioRisk(1, "alpha", true),
                new BioRisk(2, "beta", false));
        List<String> codes = List.of("alpha", "beta", "gamma");
        when(mockRepo.findAllByCodeIn(any())).thenReturn(risks);
        UCMap<BioRisk> map = service.loadBioRiskMap(codes);
        verify(mockRepo).findAllByCodeIn(codes);
        assertThat(map).hasSize(2);
        assertSame(risks.get(0), map.get("alpha"));
        assertSame(risks.get(1), map.get("beta"));
    }

    @Test
    void testRecordSampleBioRisks() {
        List<BioRisk> risks = List.of(new BioRisk(1, "alpha", true),
                new BioRisk(2, "beta", false));
        int[] sampleIds = {10,11};
        int opId = 999;
        service.recordSampleBioRisks(Map.of(sampleIds[0], risks.get(0), sampleIds[1], risks.get(1)), opId);
        verify(mockRepo, times(sampleIds.length)).recordBioRisk(anyInt(), anyInt(), anyInt());
        for (int i = 0; i < sampleIds.length; ++i) {
            verify(mockRepo).recordBioRisk(sampleIds[i], risks.get(i).getId(), opId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"map", "actions", "op", "ops"})
    void testCopySampleBioRisks(String mode) {
        Map<Integer, Integer> sourceBrIds = Map.of(
                10, 100,
                11, 101
        );
        Map<Integer, Integer> sampleDerivations = Map.of(
                20, 10,
                21, 10,
                22, 11,
                23, 12
        );
        when(mockRepo.loadBioRiskIdsForSampleIds(any())).thenReturn(sourceBrIds);

        if (mode.equalsIgnoreCase("map")) {
            service.copySampleBioRisks(sampleDerivations);
        } else {
            Map<Integer, Sample> samples = Stream.concat(sampleDerivations.keySet().stream(), sampleDerivations.values().stream())
                    .distinct()
                    .map(id -> new Sample(id, null, null, null))
                    .collect(inMap(Sample::getId));
            List<Action> actions = sampleDerivations.entrySet().stream()
                    .map(e -> new Action(null, null, null, null, samples.get(e.getKey()), samples.get(e.getValue())))
                    .toList();
            if (mode.equalsIgnoreCase("actions")) {
                service.copyActionSampleBioRisks(actions.stream());
            } else if (mode.equalsIgnoreCase("op")) {
                Operation op = new Operation();
                op.setActions(actions);
                service.copyOpSampleBioRisks(op);
            } else if (mode.equalsIgnoreCase("ops")) {
                List<Operation> ops = List.of(new Operation(), new Operation());
                ops.get(0).setActions(actions.subList(0, 3));
                ops.get(1).setActions(actions.subList(3, actions.size()));
                service.copyOpSampleBioRisks(ops);
            }
        }

        verify(mockRepo).loadBioRiskIdsForSampleIds(sameElements(sampleDerivations.values(), true));

        verify(mockRepo, times(3)).recordBioRisk(anyInt(), anyInt(), isNull());
        verify(mockRepo).recordBioRisk(20, 100, null);
        verify(mockRepo).recordBioRisk(21, 100, null);
        verify(mockRepo).recordBioRisk(22, 101, null);
    }

    @ParameterizedTest
    @MethodSource("loadAndValidateBioRisksArgs")
    public void testLoadAndValidateBioRisks(List<String> codes, List<String> sanitisedCodes,
                                            List<String> expectedProblems, List<BioRisk> expectedBioRisks,
                                            Function<String[], String> getter,
                                            BiConsumer<String[], String> setter) {
        List<String[]> datas = codes.stream()
                .map(s -> new String[] {s})
                .toList();
        List<String> problems = new ArrayList<>(expectedProblems.size());
        UCMap<BioRisk> riskMap = expectedBioRisks.stream().filter(Objects::nonNull).distinct().collect(UCMap.toUCMap(BioRisk::getCode));
        doReturn(riskMap).when(service).loadBioRiskMap(any());
        UCMap<BioRisk> map = service.loadAndValidateBioRisks(problems, datas.stream(), getter, setter);
        assertThat(problems).containsExactlyElementsOf(expectedProblems);
        assertEquals(riskMap, map);
        Zip.of(datas.stream(), sanitisedCodes.stream()).forEach((data, code) -> assertEquals(code, data[0]));
        if (sanitisedCodes.stream().anyMatch(Objects::nonNull)) {
            verify(service).loadBioRiskMap(sanitisedCodes.stream().filter(Objects::nonNull).collect(toSet()));
        }
    }

    static Stream<Arguments> loadAndValidateBioRisksArgs() {
        final String MISSING = "Biological risk number missing.";
        BioRisk risk1 = new BioRisk(1, "risk1");
        BioRisk risk2 = new BioRisk(2, "risk2");
        Function<String[], String> getter = arr -> arr[0];
        BiConsumer<String[], String> setter = (arr, s) -> arr[0] = s;
        return Arrays.stream(new Object[][] {
                {singletonList(null), singletonList(null), List.of(MISSING), singletonList(null)},
                {singletonList("   "), singletonList(null), List.of(MISSING), singletonList(null)},
                {List.of("risk1", "RISK1", " risk2  "), List.of("risk1", "RISK1", "risk2"), List.of(), List.of(risk1, risk1, risk2)},
                {List.of("x1", "  x2"), List.of("x1", "x2"), List.of("Unknown biological risk number: [\"x1\", \"x2\"]"), Arrays.asList(null, null)},
                {List.of("   ", "x1  ", "risk1", "RISK2  "), Arrays.asList(null, "x1", "risk1", "RISK2"),
                    List.of(MISSING, "Unknown biological risk number: [\"x1\"]"), Arrays.asList(null, null, risk1, risk2)},
        }).map(arr -> {
            arr = Arrays.copyOf(arr, arr.length + 2);
            arr[arr.length - 2] = getter;
            arr[arr.length - 1] = setter;
            return Arguments.of(arr);
        });
    }
}