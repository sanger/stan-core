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
import uk.ac.sanger.sccp.stan.repo.BioRiskRepo;

import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the labwareBioRiskCodes graphql query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLabwareBioRiskCodesQuery {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    BioRiskRepo bioRiskRepo;

    @Transactional
    @Test
    public void testLabwareBioRiskCodes() throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        BioRisk risk = entityCreator.createBioRisk("risk1");
        bioRiskRepo.recordBioRisk(sample.getId(), risk.getId(), null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-1", lt, sample);
        Object response = tester.post(String.format("query { labwareBioRiskCodes(barcode: \"%s\") }", lw.getBarcode()));
        assertThat(IntegrationTestUtils.chainGetList(response, "data", "labwareBioRiskCodes")).containsExactly(risk.getCode());
    }
}
