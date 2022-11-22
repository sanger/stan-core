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
import uk.ac.sanger.sccp.stan.repo.EquipmentRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.transaction.Transactional;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests a mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRecordInPlaceWithEquipmentMutation {

    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private EquipmentRepo equipmentRepo;

    @Transactional
    @Test
    public void testRecordInPlaceWithEquipment() throws Exception {
        WorkType wt = entityCreator.createWorkType("Rocks");
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("4");
        ReleaseRecipient wr = entityCreator.createReleaseRecipient("test1");
        Work work = entityCreator.createWork(wt, pr, null, cc, wr);
        User user = entityCreator.createUser("user1");
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 5);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1, 1);
        entityCreator.createLabware("STAN-50", lt, sam);

        Equipment equipment = equipmentRepo.save(new Equipment("Bananas", "scanner"));
        OperationType opType = entityCreator.createOpType("OpTypeName", null, OperationTypeFlag.IN_PLACE);

        String mutation = tester.readGraphQL("recordInPlace.graphql").replace("WORKNUMBER", work.getWorkNumber())
                .replace("666", String.valueOf(equipment.getId()));

        tester.setUser(user);
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordInPlace", "operations", 0, "id");
        assertNotNull(opId);

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        assertEquals(equipment, op.getEquipment());
    }

    @Transactional
    @Test
    public void testRecordInPlaceChangeBioState() throws Exception {
        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");
        final Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1");
        Sample[] samples = IntStream.range(5,8)
                .mapToObj(i -> entityCreator.createSample(tissue, i))
                .toArray(Sample[]::new);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1, 3);
        Labware lw = entityCreator.createLabware("STAN-50", lt, samples);
        BioState bs = entityCreator.createBioState("Fried");
        OperationType opType = entityCreator.createOpType("Fry", bs, OperationTypeFlag.IN_PLACE);
        String mutation = tester.readGraphQL("recordInPlace.graphql")
                .replace("WORKNUMBER", work.getWorkNumber())
                .replace("666", "null")
                .replace("OpTypeName", opType.getName());
        tester.setUser(user);
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordInPlace", "operations", 0, "id");
        List<Map<String, ?>> slotsData = chainGet(result, "data", "recordInPlace", "labware", 0, "slots");
        assertThat(slotsData).hasSize(3);
        for (var slotData : slotsData) {
            assertEquals(bs.getName(), chainGet(slotData, "samples", 0, "bioState", "name"));
        }
        assertNotNull(opId);
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        assertThat(op.getActions()).hasSize(3);
        for (int i = 0; i < 3; ++i) {
            Action action = op.getActions().get(i);
            assertEquals(action.getSource(), action.getDestination());
            assertEquals(action.getSource().getAddress(), new Address(1, i+1));
            assertEquals(action.getSource().getLabwareId(), lw.getId());
            final Sample oldSample = action.getSourceSample();
            Sample newSample = action.getSample();
            assertEquals(oldSample, samples[i]);
            assertEquals(newSample.getTissue(), oldSample.getTissue());
            assertEquals(newSample.getSection(), oldSample.getSection());
            assertEquals(bs, newSample.getBioState());
        }
    }

}
