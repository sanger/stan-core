package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareProbeRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests recordProbeOperation mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestProbeOperationMutation {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    OperationRepo opRepo;
    @Autowired
    LabwareProbeRepo lwProbeRepo;

    @Test
    @Transactional
    public void testRecordProbeOperation() throws Exception {
        User user = entityCreator.createUser("user1");
        String addProbeMutation = tester.readGraphQL("createprobepanels.graphql");
        tester.setUser(user);
        Object result = tester.post(addProbeMutation.replace("PROBENAME", "probe1"));
        assertEquals("probe1", chainGet(result, "data", "addProbePanel", "name"));
        result = tester.post(addProbeMutation.replace("PROBENAME", "probe2"));
        assertEquals("probe2", chainGet(result, "data", "addProbePanel", "name"));

        OperationType opType = entityCreator.createOpType("probify", null, OperationTypeFlag.PROBES, OperationTypeFlag.IN_PLACE);
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-1", entityCreator.getTubeType(), sample);
        Work work = entityCreator.createWork(null, null, null, null, null);

        String opMutation = tester.readGraphQL("recordprobeoperation.graphql").replace("SGP1", work.getWorkNumber());
        result = tester.post(opMutation);

        assertEquals("STAN-1", chainGet(result, "data", "recordProbeOperation", "labware", 0, "barcode"));
        Integer opId = chainGet(result, "data", "recordProbeOperation", "operations", 0, "id");
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        List<LabwareProbe> lwProbes = lwProbeRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(lwProbes).hasSize(2);
        if (lwProbes.get(0).getProbePanel().getName().equals("probe2")) {
            lwProbes = IntStream.of(lwProbes.size()-1,-1,-1).mapToObj(lwProbes::get).collect(toList());
        }
        for (LabwareProbe lwp : lwProbes) {
            assertEquals(opId, lwp.getOperationId());
            assertEquals(lw.getId(), lwp.getLabwareId());
        }
        LabwareProbe lwp = lwProbes.get(0);
        assertEquals("probe1", lwp.getProbePanel().getName());
        assertEquals(1, lwp.getPlex());
        assertEquals("LOT1", lwp.getLotNumber());
        lwp = lwProbes.get(1);
        assertEquals("probe2", lwp.getProbePanel().getName());
        assertEquals(2, lwp.getPlex());
        assertEquals("LOT2", lwp.getLotNumber());
    }
}
