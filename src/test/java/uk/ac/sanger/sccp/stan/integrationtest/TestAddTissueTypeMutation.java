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
import uk.ac.sanger.sccp.stan.repo.TissueTypeRepo;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the add tissue type mutation.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestAddTissueTypeMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private TissueTypeRepo ttRepo;

    @Test
    @Transactional
    public void testAddTissueType() throws Exception {
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        String mutation = tester.readGraphQL("addtissuetype.graphql");
        Object response = tester.post(mutation);
        Map<String, ?> ttData = chainGet(response, "data", "addTissueType");
        assertEquals("Bananas", ttData.get("name"));
        List<Map<String, ?>> slsData = chainGet(ttData, "spatialLocations");
        assertThat(slsData).hasSize(2);
        assertThat(slsData).containsExactly(Map.of("code", 0, "name", "No spatial information"),
                Map.of("code", 1, "name", "SL1"));
        TissueType tt = ttRepo.findByName("Bananas").orElseThrow();
        assertNotNull(tt.getId());
        assertEquals("Bananas", tt.getName());
        assertEquals("BAN", tt.getCode());
        List<SpatialLocation> sls = tt.getSpatialLocations();
        assertThat(sls).hasSize(2);
        for (int i = 0; i < sls.size(); i++) {
            SpatialLocation sl = sls.get(i);
            assertNotNull(sl.getId());
            assertEquals(i, sl.getCode());
            assertEquals(i==0 ? "No spatial information" : "SL1", sl.getName());
        }
    }

    @Test
    @Transactional
    public void testAddSpatialLocations() throws Exception {
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        ttRepo.save(new TissueType(null, "Bananas", "BAN"));
        String mutation = tester.readGraphQL("addspatiallocations.graphql");
        Object response = tester.post(mutation);
        Map<String, ?> ttData = chainGet(response, "data", "addSpatialLocations");
        assertEquals("Bananas", ttData.get("name"));
        List<Map<String, ?>> slsData = chainGet(ttData, "spatialLocations");
        assertThat(slsData).hasSize(2);
        assertThat(slsData).containsExactly(Map.of("code", 0, "name", "No spatial information"),
                Map.of("code", 1, "name", "SL1"));
        TissueType tt = ttRepo.findByName("Bananas").orElseThrow();
        assertNotNull(tt.getId());
        assertEquals("Bananas", tt.getName());
        assertEquals("BAN", tt.getCode());
        List<SpatialLocation> sls = tt.getSpatialLocations();
        assertThat(sls).hasSize(2);
        for (int i = 0; i < sls.size(); i++) {
            SpatialLocation sl = sls.get(i);
            assertNotNull(sl.getId());
            assertEquals(i, sl.getCode());
            assertEquals(i==0 ? "No spatial information" : "SL1", sl.getName());
        }
    }
}
