package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.mlwh.SSStudy;
import uk.ac.sanger.sccp.stan.mlwh.SSStudyRepo;
import uk.ac.sanger.sccp.stan.model.User;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestUpdateDnapStudiesMutation {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;

    @MockBean
    SSStudyRepo mockSsRepo;

    @Test
    @Transactional
    public void testUpdateDnapStudies() throws Exception {
        final List<SSStudy> sss = List.of(new SSStudy(10, "Ten"), new SSStudy(20, "Twenty"));
        when(mockSsRepo.loadAllSs()).thenReturn(sss);
        User user = entityCreator.createUser("user1", User.Role.admin);
        tester.setUser(user);
        String mutation = "mutation { updateDnapStudies { ssId, name }}";

        Object response = tester.post(mutation);
        List<Map<String, ?>> studies = chainGet(response, "data", "updateDnapStudies");

        assertThat(studies).hasSize(sss.size());
        for (int i = 0; i < sss.size(); ++i) {
            SSStudy ss = sss.get(i);
            Map<String, ?> map = studies.get(i);
            assertEquals(ss.id(), map.get("ssId"));
            assertEquals(ss.name(), map.get("name"));
        }
    }
}
