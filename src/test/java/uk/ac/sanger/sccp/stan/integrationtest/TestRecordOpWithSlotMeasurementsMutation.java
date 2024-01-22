package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests a mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRecordOpWithSlotMeasurementsMutation {

    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private OperationRepo opRepo;

    @Transactional
    @ParameterizedTest
    @ValueSource(strings={"Amplification", "Visium concentration", "qPCR results"})
    public void testRecordOpWithSlotMeasurements(String opName) throws Exception {
        OperationType opType = entityCreator.createOpType(opName, null, OperationTypeFlag.IN_PLACE);
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 1);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1,1);
        Labware lw = entityCreator.createLabware("STAN-A", lt, sam);
        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");
        String[] measNames, sanMeasNames, measValues, sanMeasValues;
        if (opName.equalsIgnoreCase("Visium concentration")) {
            measNames = new String[]{"CDNA CONCENTRATION", "library CONCENTRATION"};
            sanMeasNames = new String[]{"cDNA concentration", "Library concentration"};
            measValues = new String[]{"0123.5", "4"};
            sanMeasValues = new String[]{"123.50", "4.00"};
        } else if (opName.equalsIgnoreCase("qPCR results")) {
            measNames = new String[] {"CQ Value"};
            sanMeasNames = new String[] {"Cq value"};
            measValues = new String[] {"05.5"};
            sanMeasValues = new String[] {"5.50"};
        } else {
            measNames = new String[] { "CQ VALUE", "CYCLES"};
            sanMeasNames = new String[] { "Cq value", "Cycles"};
            measValues = new String[] { "05.5", "023" };
            sanMeasValues = new String[] { "5.50", "23"};
        }
        String mutation = tester.readGraphQL("opwithslotmeasurements.graphql")
                .replace("OP-TYPE", opType.getName())
                .replace("WORK-NUM", work.getWorkNumber());
        if (measNames.length < 2) {
            // If the test is only recording one measurement, delete the second measurement from the mutation
            mutation = mutation.replaceFirst("\\{[^}]+\"MEAS-NAME-1\"[^}]+}", "");
        }
        for (int i = 0; i < measNames.length; ++i) {
            mutation = mutation.replace("MEAS-NAME-"+i, measNames[i])
                    .replace("MEAS-VALUE-"+i, measValues[i]);
        }

        tester.setUser(user);
        Object result = tester.post(mutation);
        List<?> opsData = chainGet(result, "data", "recordOpWithSlotMeasurements", "operations");
        assertThat(opsData).hasSize(1);
        Integer opId = chainGet(opsData, 0, "id");
        assertNotNull(opId);
        assertEquals(opName, chainGet(opsData, 0, "operationType", "name"));

        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(measurements).hasSize(sanMeasNames.length);
        if (sanMeasNames.length > 1 && measurements.get(0).getName().equalsIgnoreCase(sanMeasNames[1])) {
            measurements = List.of(measurements.get(1), measurements.get(0));
        }
        for (int i = 0; i < sanMeasNames.length; ++i) {
            Measurement measurement = measurements.get(i);
            assertEquals(sanMeasNames[i], measurement.getName());
            assertEquals(sanMeasValues[i], measurement.getValue());
            assertEquals(lw.getFirstSlot().getId(), measurement.getSlotId());
        }
    }

    @Test
    @Transactional
    public void testAmplificationWithParentCqValues() throws Exception {
        OperationType qpcr = entityCreator.createOpType("qPCR results", null, OperationTypeFlag.IN_PLACE);
        OperationType amp = entityCreator.createOpType("Amplification", null, OperationTypeFlag.IN_PLACE);
        OperationType simpleTransfer = entityCreator.createOpType("Simple transfer", null);
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 1);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1,1);
        Labware lw1 = entityCreator.createLabware("STAN-A", lt, sam);
        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");
        String baseMutation = tester.readGraphQL("opwithslotmeasurements.graphql")
                .replace("WORK-NUM", work.getWorkNumber())
                .replaceFirst("\\{[^}]+\"MEAS-NAME-1\"[^}]+}", "");
        tester.setUser(user);
        // record a qpcr mutation to add cq measurement to the parent labware
        String qpcrMutation = baseMutation
                .replace("OP-TYPE", qpcr.getName())
                .replace("MEAS-NAME-0", "CQ VALUE")
                .replace("MEAS-VALUE-0", "10");
        Map<String, ?> qpcrResponse = tester.post(qpcrMutation);

        assertNull(qpcrResponse.get("errors"));

        Labware lw2 = entityCreator.createLabware("STAN-B", lt, sam);
        entityCreator.simpleOp(simpleTransfer, user, lw1, lw2);
        String ampMutation = baseMutation
                .replace("OP-TYPE", amp.getName())
                .replace("STAN-A", "STAN-B")
                .replace("MEAS-NAME-0", "Cycles")
                .replace("MEAS-VALUE-0", "20");
        Map<String, ?> ampResponse = tester.post(ampMutation);
        assertNull(ampResponse.get("errors"));
        Integer opId = chainGet(ampResponse, "data", "recordOpWithSlotMeasurements", "operations", 0, "id");
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(amp, op.getOperationType());
        assertEquals(lw2.getSlots().getFirst(), op.getActions().getFirst().getDestination());
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(measurements).hasSize(1);
        Measurement measurement = measurements.getFirst();
        assertEquals(lw2.getSlots().getFirst().getId(), measurement.getSlotId());
        assertEquals("Cycles", measurement.getName());
        assertEquals("20", measurement.getValue());
    }
}
