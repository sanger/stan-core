package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

/**
 * Tests the mutation to register new original samples
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRegisterOriginalSamplesMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private SolutionRepo solutionRepo;
    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private OperationTypeRepo opTypeRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private OperationSolutionRepo opSolRepo;
    @Autowired
    private BioRiskRepo bioRiskRepo;
    @MockBean
    private StorelightClient mockStorelight;

    @ParameterizedTest
    @Transactional
    @ValueSource(booleans={false,true})
    public void testRegisterOriginalSamples(boolean hasExternalName) throws Exception {
        stubStorelightUnstore(mockStorelight);

        User user = entityCreator.createUser("user1");
        BioRisk risk1 = entityCreator.createBioRisk("risk1");
        tester.setUser(user);
        Solution solution = solutionRepo.save(new Solution(null, "Glue"));
        String externalName = (hasExternalName ? "EXT1" : null);
        String mutation = tester.readGraphQL("registeroriginal.graphql");
        if (!hasExternalName) {
            mutation = mutation.replace("externalIdentifier: \"EXT1\"", "");
        }
        Map<String, ?> result = tester.post(mutation);
        assertThat((List<?>) result.get("errors")).isNullOrEmpty();
        assertThat((List<?>) result.get("clashes")).isNullOrEmpty();
        Map<String, ?> regData = chainGet(result, "data", "registerOriginalSamples");
        assertThat((List<?>) regData.get("clashes")).isNullOrEmpty();
        List<Map<String, ?>> labwareData = chainGet(regData, "labware");
        List<Map<String, String>> lwSolData = chainGet(regData, "labwareSolutions");
        assertThat(labwareData).hasSize(1);
        Map<String, ?> lwData = labwareData.get(0);

        String barcode = (String) lwData.get("barcode");
        assertNotNull(barcode);
        assertThat((List<?>) lwData.get("slots")).hasSize(1);
        assertThat(chainGetList(lwData, "slots", 0, "samples")).hasSize(1);
        Map<String, ?> tissueData = chainGet(lwData, "slots", 0, "samples", 0, "tissue");
        assertEquals(externalName, tissueData.get("externalName"));
        assertEquals(Species.HUMAN_NAME, chainGet(tissueData, "donor", "species", "name"));
        assertEquals("2022-05-19", tissueData.get("collectionDate"));
        assertEquals("None", chainGet(tissueData, "medium", "name"));
        assertEquals("None", chainGet(tissueData, "fixative", "name"));
        assertEquals("Tissue", chainGet(tissueData, "cellClass", "name"));
        assertEquals((Integer) 0, chainGet(tissueData, "spatialLocation", "code"));
        assertEquals("Bone", chainGet(tissueData, "spatialLocation", "tissueType", "name"));

        Labware lw = lwRepo.getByBarcode(barcode);
        Slot slot = lw.getFirstSlot();
        assertThat(slot.getSamples()).hasSize(1);
        Sample sample = slot.getSamples().get(0);
        assertNull(sample.getSection());
        assertEquals("Original sample", sample.getBioState().getName());
        Tissue tissue = sample.getTissue();
        Operation op = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opTypeRepo.getByName("Register"), List.of(slot.getId())).get(0);
        var opSols = opSolRepo.findAllByOperationId(op.getId());
        assertThat(opSols).containsExactly(new OperationSolution(op.getId(), solution.getId(), lw.getId(), sample.getId()));
        assertNull(tissue.getParentId());
        assertEquals("None", tissue.getMedium().getName());
        assertEquals(externalName, tissue.getExternalName());
        assertNull(tissue.getReplicate());
        assertEquals(Labware.State.active, lw.getState());

        assertThat(lwSolData).hasSize(1);
        Map<String, String> lwSol = lwSolData.get(0);
        assertEquals(lw.getBarcode(), lwSol.get("barcode"));
        assertEquals(solution.getName(), lwSol.get("solutionName"));
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sample.getId())).contains(risk1);

        Work work = entityCreator.createWork(null, null, null, null, null);
        Labware block = testBlockProcessing(barcode, work);
        testSectioningBlock(block, work);
        lw.setDiscarded(false);
        lwRepo.save(lw);
        testPotProcessing(barcode, work);
        verifyStorelightQueries(mockStorelight, user.getUsername(), List.of(barcode), List.of(barcode));

    }

    private Labware testBlockProcessing(String sourceBarcode, Work work) throws Exception {
        OperationType opType = entityCreator.createOpType("Block processing", null);
        String mutation = tester.readGraphQL("tissueblock.graphql")
                .replace("WORKNUMBER", work.getWorkNumber())
                .replace("BARCODE", sourceBarcode);
        Object result = tester.post(mutation);
        String destBarcode = chainGet(result, "data", "performTissueBlock", "labware", 0, "barcode");
        Integer opId = chainGet(result, "data", "performTissueBlock", "operations", 0, "id");
        Labware dest = lwRepo.getByBarcode(destBarcode);
        assertEquals("5c", dest.getFirstSlot().getSamples().get(0).getTissue().getReplicate());
        Labware src = lwRepo.getByBarcode(sourceBarcode);
        assertTrue(src.isDiscarded());
        assertTrue(dest.getFirstSlot().isBlock());

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        return dest;
    }

    private void testSectioningBlock(Labware block, Work work) throws Exception {
        final Integer blockSampleId = block.getFirstSlot().getBlockSampleId();
        String planMutation = tester.readGraphQL("plan_simple.graphql")
                .replace("BARCODE0", block.getBarcode())
                .replace("55555", String.valueOf(blockSampleId));
        Object result = tester.post(planMutation);
        String slideBarcode = chainGet(result, "data", "plan", "labware", 0, "barcode");
        String confirmMutation = tester.readGraphQL("confirmsection_simple.graphql")
                .replace("BARCODE0", slideBarcode)
                .replace("55555", String.valueOf(blockSampleId))
                .replace("SGP1", work.getWorkNumber());
        result = tester.post(confirmMutation);
        assertNotNull(chainGet(result, "data", "confirmSection", "labware", 0, "barcode"));
    }

    private void testPotProcessing(String sourceBarcode, Work work) throws Exception {
        OperationType opType = entityCreator.createOpType("Pot processing", null);
        LabwareType potLt = entityCreator.createLabwareType("Pot", 1, 1);
        LabwareType fwLt = entityCreator.createLabwareType("Fetal waste container", 1, 1);
        BioState fwBs = entityCreator.createBioState("Fetal waste");
        String mutation = tester.readGraphQL("potprocessing.graphql")
                .replace("WORKNUMBER", work.getWorkNumber())
                .replace("BARCODE", sourceBarcode);
        Object result = tester.post(mutation);
        String destBarcode = chainGet(result, "data", "performPotProcessing", "labware", 0, "barcode");
        String fwBarcode = chainGet(result, "data", "performPotProcessing", "labware", 1, "barcode");
        Integer opId = chainGet(result, "data", "performPotProcessing", "operations", 0, "id");
        Labware dest = lwRepo.getByBarcode(destBarcode);
        Sample sample = dest.getFirstSlot().getSamples().get(0);
        assertEquals(potLt, dest.getLabwareType());
        assertEquals("Formalin", sample.getTissue().getFixative().getName());
        Labware src = lwRepo.getByBarcode(sourceBarcode);
        assertTrue(src.isDiscarded());

        Labware fw = lwRepo.getByBarcode(fwBarcode);
        Sample fwSample = fw.getFirstSlot().getSamples().get(0);
        assertEquals(fwLt, fw.getLabwareType());
        assertEquals(fwBs, fwSample.getBioState());

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
    }
}
