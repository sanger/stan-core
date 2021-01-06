package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwarePrintRepo;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;
import uk.ac.sanger.sccp.stan.service.label.print.PrintClient;

import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Non-exhaustive integration tests.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class IntegrationTests {

    @Autowired
    private GraphQLTester tester;

    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private LabwarePrintRepo labwarePrintRepo;

    @Test
    @Transactional
    public void testRegister() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        String mutation = tester.readResource("graphql/register.graphql");
        Map<String, Map<String, Map<String, List<Map<String, String>>>>> result = tester.post(mutation);
        assertNotNull(result.get("data").get("register").get("labware").get(0).get("barcode"));
        assertEquals("TISSUE1", result.get("data").get("register").get("tissue").get(0).get("externalName"));
    }

    @Test
    @Transactional
    public void testPlanAndRecordOperation() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1"), null);
        Labware sourceBlock = entityCreator.createBlock("STAN-B70C", sample);
        String mutation = tester.readResource("graphql/plan.graphql");
        mutation = mutation.replace("$sampleId", String.valueOf(sample.getId()));
        Map<String, ?> result = tester.post(mutation);
        assertNull(result.get("errors"));
        Object resultPlan = chainGet(result, "data", "plan");
        List<?> planResultLabware = chainGet(resultPlan, "labware");
        assertEquals(1, planResultLabware.size());
        String barcode = chainGet(planResultLabware, 0, "barcode");
        assertNotNull(barcode);
        assertEquals("Tube", chainGet(planResultLabware, 0, "labwareType", "name"));
        List<?> resultOps = chainGet(resultPlan, "operations");
        assertEquals(resultOps.size(), 1);
        assertEquals("Section", chainGet(resultOps, 0, "operationType", "name"));
        List<?> resultActions = chainGet(resultOps, 0, "planActions");
        assertEquals(1, resultActions.size());
        Map<String, ?> resultAction = chainGet(resultActions, 0);
        assertEquals("A1", chainGet(resultAction, "source", "address"));
        assertEquals(sourceBlock.getId(), chainGet(resultAction, "source", "labwareId"));
        assertEquals("A1", chainGet(resultAction, "destination", "address"));
        assertNotNull(chainGet(resultAction, "destination", "labwareId"));
        assertEquals(sample.getId(), chainGet(resultAction, "sample", "id"));
        assertNotNull(chainGet(resultAction, "newSection"));

        String recordMutation = tester.readResource("graphql/confirm.graphql");
        recordMutation = recordMutation.replace("$BARCODE", barcode);
        result = tester.post(recordMutation);
        assertNull(result.get("errors"));

        Object resultConfirm = chainGet(result, "data", "confirmOperation");
        List<?> resultLabware = chainGet(resultConfirm, "labware");
        assertEquals(1, resultLabware.size());
        assertEquals(barcode, chainGet(resultLabware, 0, "barcode"));
        List<?> slots = chainGet(resultLabware, 0, "slots");
        assertEquals(1, slots.size());
        assertEquals((Integer) 1, chainGet(slots, 0, "samples", 0, "section"));
        assertNotNull(chainGet(slots, 0, "samples", 0, "id"));

        resultOps = chainGet(resultConfirm, "operations");
        assertEquals(resultOps.size(), 1);
        Object resultOp = resultOps.get(0);
        assertNotNull(chainGet(resultOp, "performed"));
        assertEquals("Section", chainGet(resultOp, "operationType", "name"));
        List<?> actions = chainGet(resultOp, "actions");
        assertEquals(1, actions.size());
        Object action = actions.get(0);
        assertEquals((Integer) 1, chainGet(action, "sample", "section"));
        assertEquals("A1", chainGet(action, "destination", "address").toString());
    }

    @Test
    @Transactional
    public void testPrintLabware() throws Exception {
        //noinspection unchecked
        PrintClient<LabelPrintRequest> mockPrintClient = mock(PrintClient.class);
        when(tester.mockPrintClientFactory.getClient(any())).thenReturn(mockPrintClient);
        tester.setUser(entityCreator.createUser("dr6"));
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1");
        Labware lw = entityCreator.createLabware("STAN-SLIDE", entityCreator.createLabwareType("slide6", 3, 2),
                entityCreator.createSample(tissue, 1), entityCreator.createSample(tissue, 2),
                entityCreator.createSample(tissue, 3), entityCreator.createSample(tissue, 4));
        Printer printer = entityCreator.createPrinter("stub");
        String mutation = "mutation { printLabware(barcodes: [\"STAN-SLIDE\"], printer: \"stub\") }";
        assertThat(tester.<Map<?,?>>post(mutation)).isEqualTo(Map.of("data", Map.of("printLabware", "OK")));
        String donorName = tissue.getDonor().getDonorName();
        String tissueDesc = getTissueDesc(tissue);
        Integer replicate = tissue.getReplicate();
        verify(mockPrintClient).print("stub", new LabelPrintRequest(
                lw.getLabwareType().getLabelType(),
                List.of(new LabwareLabelData(lw.getBarcode(), tissue.getMedium().getName(),
                        List.of(
                                new LabelContent(donorName, tissueDesc, replicate, 1),
                                new LabelContent(donorName, tissueDesc, replicate, 2),
                                new LabelContent(donorName, tissueDesc, replicate, 3),
                                new LabelContent(donorName, tissueDesc, replicate, 4)
                        ))
                ))
        );

        Iterator<LabwarePrint> recordIter = labwarePrintRepo.findAll().iterator();
        assertTrue(recordIter.hasNext());
        LabwarePrint record = recordIter.next();
        assertFalse(recordIter.hasNext());

        assertNotNull(record.getPrinted());
        assertNotNull(record.getId());
        assertEquals(record.getLabware().getId(), lw.getId());
        assertEquals(record.getPrinter().getId(), printer.getId());
        assertEquals(record.getUser().getUsername(), "dr6");
    }

    private static String getTissueDesc(Tissue tissue) {
        String prefix;
        switch (tissue.getDonor().getLifeStage()) {
            case paediatric:
                prefix = "P";
                break;
            case fetal:
                prefix = "F";
                break;
            default:
                prefix = "";
        }
        return String.format("%s%s-%s", prefix, tissue.getTissueType().getCode(), tissue.getSpatialLocation().getCode());
    }

    @SuppressWarnings("unchecked")
    private static <T> T chainGet(Object container, Object... accessors) {
        for (Object accessor : accessors) {
            if (accessor instanceof Integer) {
                container = ((List<?>) container).get((int) accessor);
            } else {
                container = ((Map<?,?>) container).get(accessor);
            }
        }
        return (T) container;
    }
}
