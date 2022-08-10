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
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests worksSummary query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestWorksSummaryQuery {
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Transactional
    @Test
    public void testWorksSummary() throws Exception {
        WorkType wt1 = entityCreator.createWorkType("wt1");
        WorkType wt2 = entityCreator.createWorkType("wt2");
        Project project = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("CC1");
        Work work1 = entityCreator.createWork(wt1, project, cc, null);
        Work work2 = entityCreator.createWork(wt1, project, cc, null);
        Work work3 = entityCreator.createWork(wt2, project, cc, null);

        work1.setNumBlocks(5);
        work2.setNumSlides(6);
        work3.setNumOriginalSamples(7);

        workRepo.saveAll(List.of(work1, work2, work3));

        String query = tester.readGraphQL("workssummary.graphql");

        Object result = tester.post(query);

        List<Map<String,?>> groupsData = chainGet(result, "data", "worksSummary");
        assertThat(groupsData).hasSize(2);
        Map<String, ?> g1 = groupsData.get(0);
        Map<String, ?> g2 = groupsData.get(1);
        if (chainGet(g1, "workType", "name").equals("wt2")) {
            var swap = g1;
            g1 = g2;
            g2 = swap;
        }
        assertEquals("wt1", chainGet(g1, "workType", "name"));
        assertEquals("active", g1.get("status"));
        assertEquals(2, g1.get("numWorks"));
        assertEquals(5, g1.get("totalNumBlocks"));
        assertEquals(6, g1.get("totalNumSlides"));
        assertEquals(0, g1.get("totalNumOriginalSamples"));
        assertEquals("wt2", chainGet(g2, "workType", "name"));
        assertEquals("active", g2.get("status"));
        assertEquals(1, g2.get("numWorks"));
        assertEquals(0, g2.get("totalNumBlocks"));
        assertEquals(0, g2.get("totalNumSlides"));
        assertEquals(7, g2.get("totalNumOriginalSamples"));
    }
}
