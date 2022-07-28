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
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests work mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestWorkMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private ProjectRepo projectRepo;
    @Autowired
    private CostCodeRepo costCodeRepo;
    @Autowired
    private ReleaseRecipientRepo releaseRecipientRepo;

    @Transactional
    @Test
    public void testWork() throws Exception {
        Project project = projectRepo.save(new Project(null, "Stargate"));
        CostCode cc = costCodeRepo.save(new CostCode(null, "S666"));
        WorkType workType = entityCreator.createWorkType("Drywalling");
        ReleaseRecipient workRequester = releaseRecipientRepo.save(new ReleaseRecipient(null, "test1"));
        User user = entityCreator.createUser("user1", User.Role.normal);

        String worksQuery  = "query { works(status: [active]) { workNumber, workType {name}, workRequester {username}, project {name}, costCode {code}, status } }";
        Object data = tester.post(worksQuery);
        List<Map<String,?>> worksData = chainGet(data, "data", "works");
        assertNotNull(worksData);
        int startingNum = worksData.size();

        tester.setUser(user);
        data = tester.post(tester.readGraphQL("createWork.graphql"));

        Map<String, Object> workData = chainGet(data, "data", "createWork");
        String workNumber = (String) workData.get("workNumber");
        assertNotNull(workNumber);
        assertEquals(project.getName(), chainGet(workData, "project", "name"));
        assertEquals(cc.getCode(), chainGet(workData, "costCode", "code"));
        assertEquals(workType.getName(), chainGet(workData, "workType", "name"));
        assertEquals(workRequester.getUsername(), chainGet(workData, "workRequester", "username"));
        assertEquals("unstarted", workData.get("status"));

        data = tester.post(worksQuery);
        worksData = chainGet(data, "data", "works");
        assertEquals(startingNum, worksData.size());
        assertFalse(worksData.stream().anyMatch(d -> d.get("workNumber").equals(workNumber)));

        data = tester.post("mutation { updateWorkStatus(workNumber: \""+workNumber+"\", status: paused, commentId: 1) {work{status},comment}}");
        assertEquals("paused", chainGet(data, "data", "updateWorkStatus", "work", "status"));
        assertEquals("Section damaged.", chainGet(data, "data", "updateWorkStatus", "comment"));

        data = tester.post("query { worksWithComments(status:[paused]) { work { workNumber }, comment }}");
        List<Map<String,?>> wcDatas = chainGet(data, "data", "worksWithComments");
        Map<String, ?> wcData = wcDatas.stream().filter(wcd -> chainGet(wcd, "work", "workNumber").equals(workNumber))
                .findAny().orElse(null);
        assertNotNull(wcData);
        assertEquals("Section damaged.", wcData.get("comment"));

        data = tester.post("mutation { updateWorkPriority(workNumber: \""+workNumber+"\", priority: \"a4\") { priority }}");
        assertEquals("A4", chainGet(data, "data", "updateWorkPriority", "priority"));
        data = tester.post("mutation { updateWorkPriority(workNumber: \""+workNumber+"\", priority: \"B35\") { priority }}");
        assertEquals("B35", chainGet(data, "data", "updateWorkPriority", "priority"));

        data = tester.post("mutation { updateWorkStatus(workNumber: \""+workNumber+"\", status: active) {work{status,priority}, comment} }");
        assertEquals("active", chainGet(data, "data", "updateWorkStatus", "work", "status"));
        assertEquals("B35", chainGet(data, "data", "updateWorkStatus", "work", "priority"));
        assertNull(chainGet(data, "data", "updateWorkStatus", "comment"));

        data = tester.post(worksQuery);
        worksData = chainGet(data, "data", "works");
        assertEquals(startingNum+1, worksData.size());
        workData.put("status", "active");
        assertThat(worksData).contains(workData);

        data = tester.post("mutation { updateWorkNumBlocks(workNumber: \""+workNumber+"\", numBlocks: 5) { workNumber, numBlocks, numSlides, numOriginalSamples }}");
        workData = chainGet(data, "data", "updateWorkNumBlocks");
        assertEquals(workNumber, workData.get("workNumber"));
        assertEquals(5, workData.get("numBlocks"));
        assertNull(workData.get("numSlides"));
        assertNull(workData.get("numOriginalSamples"));

        data = tester.post("mutation { updateWorkNumSlides(workNumber: \""+workNumber+"\", numSlides: 0) { workNumber, numBlocks, numSlides, numOriginalSamples }}");
        workData = chainGet(data, "data", "updateWorkNumSlides");
        assertEquals(workNumber, workData.get("workNumber"));
        assertEquals(5, workData.get("numBlocks"));
        assertEquals(0, workData.get("numSlides"));

        data = tester.post("mutation { updateWorkNumOriginalSamples(workNumber: \""+workNumber+"\", numOriginalSamples: 2) { workNumber, numBlocks, numSlides, numOriginalSamples }}");
        workData = chainGet(data, "data", "updateWorkNumOriginalSamples");
        assertEquals(workNumber, workData.get("workNumber"));
        assertEquals(5, workData.get("numBlocks"));
        assertEquals(0, workData.get("numSlides"));
        assertEquals(2, workData.get("numOriginalSamples"));

        data = tester.post("mutation { updateWorkStatus(workNumber: \""+workNumber+"\", status: completed) {work{status,priority}}}");
        assertEquals("completed", chainGet(data, "data", "updateWorkStatus", "work", "status"));
        assertNull(chainGet(data, "data", "updateWorkStatus", "work", "priority"));
    }
}
