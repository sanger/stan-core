package uk.ac.sanger.sccp.stan.integrationtest;

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

import javax.transaction.Transactional;
import java.util.List;

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
public class TestRecordOpWithSlotMeasurementsMutation {

    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private MeasurementRepo measurementRepo;

    @Transactional
    @ParameterizedTest
    @ValueSource(strings={"Amplification", "Visium concentration"})
    public void testRecordOpWithSlotMeasurements(String opName) throws Exception {
        OperationType opType = entityCreator.createOpType(opName, null, OperationTypeFlag.IN_PLACE);
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 1);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1,1);
        Labware lw = entityCreator.createLabware("STAN-A", lt, sam);
        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");
        String[] measNames, sanMeasNames, measValues, sanMeasValues;
        if (opName.equalsIgnoreCase("Visium concentration")) {
            measNames = new String[] { "CDNA CONCENTRATION", "library CONCENTRATION" };
            sanMeasNames = new String[] { "cDNA concentration", "Library concentration"};
            measValues = new String[] { "0123.5", "4" };
            sanMeasValues = new String[] { "123.50", "4.00"};
        } else {
            measNames = new String[] { "CQ VALUE", "CYCLES"};
            sanMeasNames = new String[] { "Cq value", "Cycles"};
            measValues = new String[] { "05.5", "023" };
            sanMeasValues = new String[] { "5.50", "23"};
        }
        String mutation = tester.readGraphQL("opwithslotmeasurements.graphql")
                .replace("OP-TYPE", opType.getName())
                .replace("WORK-NUM", work.getWorkNumber());
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
        if (measurements.get(0).getName().equalsIgnoreCase(sanMeasNames[1])) {
            measurements = List.of(measurements.get(1), measurements.get(0));
        }
        for (int i = 0; i < sanMeasNames.length; ++i) {
            Measurement measurement = measurements.get(i);
            assertEquals(sanMeasNames[i], measurement.getName());
            assertEquals(sanMeasValues[i], measurement.getValue());
            assertEquals(lw.getFirstSlot().getId(), measurement.getSlotId());
        }
    }
}
