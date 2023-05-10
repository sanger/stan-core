package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentSlot;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests the reagentTransfer mutation.
 * @author dr6
 */
@Sql("/testdata/reagent_transfer_mutation_test.sql")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestReagentTransferMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private BioStateRepo bsRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ReagentPlateRepo reagentPlateRepo;

    @SuppressWarnings("unchecked")
    @Test
    @Transactional
    public void testReagentTransfer() throws Exception {
        final String rpBarcode = String.format("%024d", 123);
        final String plateBarcode = "STAN-A1";
        final LabwareType lt = entityCreator.createLabwareType("lt", 8, 12);
        String rpQuery = tester.readGraphQL("reagentplate.graphql")
                        .replace("[RP_BC]", rpBarcode);
        Object result = tester.post(rpQuery);
        assertNull(chainGet(result, "data", "reagentPlate"));

        Labware lw = recordTransfer(rpBarcode, plateBarcode, lt);

        entityManager.flush();
        final ReagentPlate rp = reagentPlateRepo.findByBarcode(rpBarcode).orElseThrow();
        // It would be nice if this wasn't necessary
        for (ReagentSlot rslot : rp.getSlots()) {
            entityManager.refresh(rslot);
        }

        assertEquals(2, rp.getTagLayoutId());

        checkHistory(rpBarcode, lw.getBarcode());

        result = tester.post(rpQuery);
        Map<String, ?> rpData = chainGet(result, "data", "reagentPlate");
        assertEquals(ReagentPlate.TYPE_FRESH_FROZEN, rpData.get("plateType"));
        final int NUM_SLOTS = 96;
        Set<String> addressStrings = new HashSet<>(NUM_SLOTS);
        assertEquals(rpBarcode, rpData.get("barcode"));
        List<Map<String, ?>> slotsData = (List<Map<String, ?>>) rpData.get("slots");
        assertThat(slotsData).hasSize(NUM_SLOTS);
        for (var slotData : slotsData) {
            final String addressString = (String) slotData.get("address");
            assertThat(addressString).matches("^[A-H][1-9][0-9]?$");
            addressStrings.add(addressString);
            if (addressString.matches("^[AB]1$")) {
                assertTrue((boolean) slotData.get("used"));
            } else {
                assertFalse((boolean) slotData.get("used"));
            }
        }
        assertThat(addressStrings).hasSize(NUM_SLOTS);
        assertNotNull(rpData);
    }

    @SuppressWarnings("unchecked")
    private Labware recordTransfer(String rpBarcode, String plateBarcode, LabwareType lt) throws Exception {
        Object result;
        Tissue tissue = entityCreator.createTissue(null, "EXT1");
        BioState bs0 = bsRepo.save(new BioState(null, "Regular"));
        BioState bs1 = bsRepo.save(new BioState(null, "Decaf"));
        Sample[] samples = IntStream.range(10,13)
                .mapToObj(i -> entityCreator.createSample(tissue, i, bs0))
                .toArray(Sample[]::new);

        Labware lw = entityCreator.createLabware(plateBarcode, lt, samples);

        OperationType opType = entityCreator.createOpType("Decaffeinate", bs1,
                OperationTypeFlag.IN_PLACE, OperationTypeFlag.REAGENT_TRANSFER);

        Work work = entityCreator.createWork(null, null, null, null, null);

        String mutation = tester.readGraphQL("reagenttransfer.graphql")
                .replace("[RP_BC]", rpBarcode)
                .replace("[OPTYPE]", opType.getName())
                .replace("[DEST_BC]", lw.getBarcode())
                .replace("[WORKNUM]", work.getWorkNumber())
                .replace("[PLATETYPE]", ReagentPlate.TYPE_FRESH_FROZEN);

        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        result = tester.post(mutation);
        Object rtData = chainGet(result, "data", "reagentTransfer");
        assertThat(chainGetList(rtData, "labware")).hasSize(1);
        assertThat(chainGetList(rtData, "operations")).hasSize(1);
        Object lwData = chainGet(rtData, "labware", 0);
        Object opData = chainGet(rtData, "operations", 0);

        boolean gotA1 = false;
        boolean gotA2 = false;
        boolean gotA3 = false;
        List<Map<String,?>> slotDataList = chainGet(lwData, "slots");
        for (Map<String,?> slotData : slotDataList) {
            String ad = (String) slotData.get("address");
            List<Map<String,?>> samplesData = (List<Map<String, ?>>) slotData.get("samples");
            if (ad.matches("A[1-3]")) {
                switch (ad) {
                    case "A1": gotA1 = true; break;
                    case "A2": gotA2 = true; break;
                    case "A3": gotA3 = true; break;
                }
                assertThat(samplesData).hasSize(1);
                assertEquals(bs1.getName(), chainGet(samplesData, 0, "bioState", "name"));
            } else {
                assertThat(samplesData).isEmpty();
            }
        }
        assertTrue(gotA1);
        assertTrue(gotA2);
        assertTrue(gotA3);

        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));
        Integer opId = chainGet(opData, "id");
        assertNotNull(opId);

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        assertThat(op.getActions()).hasSize(samples.length);

        for (int i = 0; i < samples.length; ++i) {
            Action action = op.getActions().get(i);
            Sample sam0 = action.getSourceSample();
            Sample sam1 = action.getSample();
            assertEquals(samples[i], sam0);
            assertNotEquals(sam1, sam0);
            assertEquals(sam1.getTissue(), sam0.getTissue());
            assertEquals(sam1.getSection(), sam0.getSection());
            assertEquals(sam1.getBioState(), bs1);
        }

        entityManager.refresh(lw);
        int sampleCount = 0;
        for (Slot slot : lw.getSlots()) {
            for (Sample sample : slot.getSamples()) {
                ++sampleCount;
                assertEquals(bs1, sample.getBioState());
            }
        }
        assertEquals(3, sampleCount);
        return lw;
    }

    @SuppressWarnings("unchecked")
    private void checkHistory(String reagentPlateBarcode, String lwBarcode) throws Exception {
        // historyForLabwareBarcode
        String history = tester.readGraphQL("history.graphql")
                .replace("historyForSampleId(sampleId: 1)",
                        "historyForLabwareBarcode(barcode: \""+lwBarcode+"\")");
        Object result = tester.post(history);
        List<Map<String,?>> entries = chainGet(result, "data", "historyForLabwareBarcode", "entries");
        Map<String, ?> entry = entries.stream()
                .filter(e -> e.get("type").equals("Decaffeinate"))
                .findAny()
                .orElseThrow();
        List<String> details = (List<String>) entry.get("details");
        assertThat(details).containsExactlyInAnyOrder(reagentPlateBarcode+" : A1 -> A1",
                reagentPlateBarcode+" : B1 -> A2");
    }
}
