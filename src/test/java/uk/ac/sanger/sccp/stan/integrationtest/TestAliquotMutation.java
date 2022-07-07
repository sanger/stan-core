package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

/**
 * Tests the aliquot mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestAliquotMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private OperationRepo opRepo;

    @MockBean
    private StorelightClient mockStorelight;

    @Test
    @Transactional
    public void testAliquot() throws Exception {
        stubStorelightUnstore(mockStorelight);
        OperationType opType = entityCreator.createOpType("Aliquot", null, OperationTypeFlag.DISCARD_SOURCE);
        LabwareType tubeType = entityCreator.getTubeType();
        Tissue tissue = entityCreator.createTissue(null, "EXT1");
        Sample sample = entityCreator.createSample(tissue, null);
        Labware sourceLw = entityCreator.createLabware("STAN-1A", tubeType, sample);
        Work work = entityCreator.createWork(null, null, null);
        String mutation = tester.readGraphQL("aliquot.graphql")
                .replace("SOURCE", sourceLw.getBarcode())
                .replace("WORK", work.getWorkNumber())
                .replace("LWTYPE", tubeType.getName());

        User user = entityCreator.createUser("user1");
        tester.setUser(user);

        Object response = tester.post(mutation);

        entityManager.flush();

        Map<String, List<Map<String, ?>>> aliquotData = chainGet(response, "data", "aliquot");

        List<Map<String, ?>> lwsData = aliquotData.get("labware");
        List<Map<String, ?>> opsData = aliquotData.get("operations");

        assertThat(lwsData).hasSize(2);
        assertThat(opsData).hasSize(2);

        Set<String> barcodes = lwsData.stream()
                .map(ld -> (String) ld.get("barcode"))
                .collect(toSet());
        assertThat(barcodes).hasSize(2);
        for (String bc : barcodes) {
            assertThat(bc).matches("^STAN-[A-F0-9]+$");
        }

        Set<Integer> opIds = new HashSet<>(2);
        for (var od : opsData) {
            assertEquals("Aliquot", chainGet(od, "operationType", "name"));
            Integer opId = (Integer) od.get("id");
            assertNotNull(opId);
            assertTrue(opIds.add(opId));
            List<Map<String, ?>> actionsData = chainGet(od, "actions");
            assertThat(actionsData).hasSize(1);
            Map<String, ?> destData = chainGet(actionsData, 0, "destination");
            assertEquals("A1", destData.get("address"));
            List<Map<String, ?>> samplesData = chainGet(destData, "samples");
            assertThat(samplesData).hasSize(1);
            assertEquals(sample.getId(), chainGet(samplesData, 0, "id"));
        }

        entityManager.refresh(sourceLw);
        assertTrue(sourceLw.isDiscarded());

        List<Labware> destLws = lwRepo.findByBarcodeIn(barcodes);
        Set<Slot> destSlots = new HashSet<>(2);
        assertThat(destLws).hasSize(2);
        for (Labware lw : destLws) {
            assertEquals(Labware.State.active, lw.getState());
            Slot slot = lw.getFirstSlot();
            assertThat(slot.getSamples()).hasSize(1).contains(sample);
            destSlots.add(slot);
        }

        Iterable<Operation> opsIterable = opRepo.findAllById(opIds);
        Collection<Operation> ops;
        if (opsIterable instanceof Collection) {
            ops = (Collection<Operation>) opsIterable;
        } else {
            ops = new ArrayList<>(2);
            for (Operation op : opsIterable) {
                ops.add(op);
            }
        }
        assertThat(ops).hasSize(2);
        Set<Slot> dests = new HashSet<>(2);
        for (Operation op : ops) {
            assertEquals(opType, op.getOperationType());
            assertThat(op.getActions()).hasSize(1);
            Action action = op.getActions().get(0);
            assertEquals(sample, action.getSample());
            assertEquals(sample, action.getSourceSample());
            assertEquals(sourceLw.getFirstSlot(), action.getSource());
            dests.add(action.getDestination());
        }
        assertEquals(destSlots, dests);

        verifyStorelightQuery(mockStorelight, List.of(sourceLw.getBarcode()), user.getUsername());
    }
}
