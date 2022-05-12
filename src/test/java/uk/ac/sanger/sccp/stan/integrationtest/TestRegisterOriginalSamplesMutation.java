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
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.SolutionSampleRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

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
    private EntityManager entityManager;
    @Autowired
    private SolutionSampleRepo solutionSampleRepo;
    @Autowired
    private LabwareRepo lwRepo;

    @Test
    @Transactional
    public void testRegisterOriginalSamples() throws Exception {
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        SolutionSample solution = solutionSampleRepo.save(new SolutionSample(null, "Glue"));

        String mutation = tester.readGraphQL("registeroriginal.graphql");
        Map<String, ?> result = tester.post(mutation);
        assertThat((List<?>) result.get("errors")).isNullOrEmpty();
        assertThat((List<?>) result.get("clashes")).isNullOrEmpty();
        List<Map<String, ?>> labwareData = chainGet(result, "data", "registerOriginalSamples", "labware");
        assertThat(labwareData).hasSize(1);
        Map<String, ?> lwData = labwareData.get(0);

        String barcode = (String) lwData.get("barcode");
        assertNotNull(barcode);
        assertThat((List<?>) lwData.get("slots")).hasSize(1);
        assertThat(chainGetList(lwData, "slots", 0, "samples")).hasSize(1);
        Map<String, ?> tissueData = chainGet(lwData, "slots", 0, "samples", 0, "tissue");
        assertNull(tissueData.get("externalName"));
        assertEquals("Human", chainGet(tissueData, "donor", "species", "name"));
        assertEquals("2022-05-19", tissueData.get("collectionDate"));
        assertEquals("None", chainGet(tissueData, "medium", "name"));
        assertEquals("None", chainGet(tissueData, "fixative", "name"));
        assertEquals((Integer) 0, chainGet(tissueData, "spatialLocation", "code"));
        assertEquals("Bone", chainGet(tissueData, "spatialLocation", "tissueType", "name"));
        assertEquals(solution.getName(), chainGet(tissueData, "solutionSample", "name"));

        Labware lw = lwRepo.getByBarcode(barcode);
        Slot slot = lw.getFirstSlot();
        assertThat(slot.getSamples()).hasSize(1);
        Sample sample = slot.getSamples().get(0);
        assertNull(sample.getSection());
        assertEquals("Original sample", sample.getBioState().getName());
        Tissue tissue = sample.getTissue();
        assertEquals(solution, tissue.getSolutionSample());
        assertNull(tissue.getParentId());
        assertEquals("None", tissue.getMedium().getName());
        assertNull(tissue.getExternalName());
        assertNull(tissue.getReplicate());
        assertEquals(Labware.State.active, lw.getState());
    }
}
