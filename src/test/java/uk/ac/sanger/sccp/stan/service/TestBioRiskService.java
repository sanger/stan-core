package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.BioRiskRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
}