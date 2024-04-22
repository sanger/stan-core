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
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests the clean out mutation.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestCleanOutMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OperationRepo opRepo;

    @Test
    @Transactional
    public void testCleanOut() throws Exception {
        LabwareType lt = entityCreator.createLabwareType("lt", 1,2);
        Tissue tissue = entityCreator.createTissue(null, "EXT1");
        Sample[] samples = IntStream.rangeClosed(1,3)
                .mapToObj(i -> entityCreator.createSample(tissue, i))
                .toArray(Sample[]::new);
        Labware lw = entityCreator.createLabware("STAN-A1", lt, new Sample[][] {
                { samples[0], samples[1] },
                { samples[1], samples[2] },
        });
        int slotId = lw.getSlot(new Address(1,2)).getId();
        OperationType opType = entityCreator.createOpType("Clean out", null, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        tester.setUser(user);

        String mutation = tester.readGraphQL("cleanout.graphql").replace("[WORK]", work.getWorkNumber());

        Object response = tester.post(mutation);

        final int opId = checkResponse(response, samples, slotId);
        entityManager.flush();
        entityManager.refresh(lw);
        checkOperation(opId, opType, slotId, samples);

        assertThat(lw.getFirstSlot().getSamples()).containsExactlyInAnyOrder(samples[0], samples[1]);
        assertThat(lw.getSlot(new Address(1,2)).getSamples()).isEmpty();
        assertThat(work.getOperationIds()).containsExactly(opId);
        assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrder(new Work.SampleSlotId(samples[1].getId(), slotId),
                new Work.SampleSlotId(samples[2].getId(), slotId));
    }

    private static int checkResponse(Object response, Sample[] samples, int slotId) {
        Object data = chainGet(response, "data", "cleanOut");
        assertThat(chainGetList(data, "labware")).hasSize(1);
        assertThat(chainGetList(data, "operations")).hasSize(1);
        List<?> actionsData = chainGet(data, "operations", 0, "actions");
        assertThat(actionsData).hasSize(2);
        assertThat(actionsData.stream().map(ac -> chainGet(ac, "sample", "id"))).containsExactlyInAnyOrder(
                samples[1].getId(), samples[2].getId()
        );
        assertThat(actionsData.stream().map(ac -> chainGet(ac, "destination", "id"))).containsExactly(
                slotId, slotId
        );
        List<Map<String,?>> slotsData = chainGet(data, "labware", 0, "slots");
        assertThat(slotsData).hasSize(2);
        assertThat(slotsData.stream().map(sd -> chainGet(sd, "address"))).containsExactly("A1", "A2");
        assertThat(chainGetList(slotsData.get(0), "samples").stream().map(sam -> chainGet(sam, "id")))
                .containsExactlyInAnyOrder(samples[0].getId(), samples[1].getId());
        assertThat(chainGetList(slotsData.get(1), "samples")).isEmpty();

        Integer opId = chainGet(data, "operations", 0, "id");
        assertNotNull(opId);
        return opId;
    }

    private void checkOperation(Integer opId, OperationType opType, Integer slotId, Sample[] samples) {
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        assertThat(op.getActions()).hasSize(2);
        for (Action action : op.getActions()) {
            assertEquals(action.getSource(), action.getDestination());
            assertEquals(slotId, action.getDestination().getId());
            assertEquals(action.getSample(), action.getSourceSample());
        }
        assertThat(op.getActions().stream().map(Action::getSample)).containsExactlyInAnyOrder(samples[1], samples[2]);
    }
}
