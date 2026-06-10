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

import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.assertNoErrors;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;


@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestAddExternalIDMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    public void testAddExternalID() throws Exception {
        tester.setUser(entityCreator.createUser("user1"));

        entityCreator.createOpType("Add external ID", null,OperationTypeFlag.IN_PLACE);
        Donor donor = entityCreator.createDonor("Donor1");
        Tissue tissue = entityCreator.createTissue(donor, "", "1");
        Sample sample = entityCreator.createSample(tissue, 1, null);
        LabwareType lt = entityCreator.createLabwareType("LT",1, 1);
        entityCreator.createLabware("Barcode1", lt, sample);

        String mutation = tester.readGraphQL("addexternalid.graphql");
        Object result = tester.post(mutation);
        assertNoErrors(result);
        Integer opId = chainGet(result, "data", "addExternalID", "operations", 0, "id");
        assertNotNull(opId);
        assertEquals("ExternalName", tissue.getExternalName());
    }


    @Test
    @Transactional
    public void testAddExternalIDs() throws Exception {
        tester.setUser(entityCreator.createUser("user1"));

        entityCreator.createOpType("Add external ID", null,OperationTypeFlag.IN_PLACE);
        Donor donor = entityCreator.createDonor("Donor1");
        Tissue tissue = entityCreator.createTissue(donor, "", "1");
        Tissue tissue2 = entityCreator.createTissue(donor, "", "2");
        Sample sample = entityCreator.createSample(tissue, 1, null);
        Sample sample2 = entityCreator.createSample(tissue2, 1, null);
        LabwareType lt = entityCreator.createLabwareType("LT",1, 2);
        entityCreator.createLabware("BC1", lt, sample, sample2);

        String mutation = tester.readGraphQL("addexternalids.graphql");
        Object result = tester.post(mutation);
        assertNoErrors(result);

        Integer opId = chainGet(result, "data", "addExternalIds", "operations", 0, "id");
        assertNotNull(opId);
        assertEquals("NAME1", tissue.getExternalName());
        assertEquals("NAME2", tissue2.getExternalName());
    }
}
