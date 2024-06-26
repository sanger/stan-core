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
import uk.ac.sanger.sccp.stan.repo.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.nullableMapOf;

/**
 * Tests the query for suggested work for labware
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSuggestedWorkForLabwareQuery {
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Transactional
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSuggestedWork(boolean includeInactive) throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.getTubeType(), sample);
        entityCreator.createLabware("STAN-A2", entityCreator.getTubeType(), sample);
        final Slot slot = lw.getFirstSlot();
        OperationType opType = entityCreator.createOpType("Scrape", null);
        Operation op = opRepo.save(new Operation(null, opType, null, null, entityCreator.createUser("user1")));
        Action ac = new Action(null, op.getId(), slot, slot, sample, sample);
        Work work = entityCreator.createWork(null, null, null, null, null);
        work.getOperationIds().add(op.getId());
        workRepo.save(work);
        actionRepo.save(ac);
        String query = tester.readGraphQL("suggestedwork.graphql");
        if (!includeInactive) {
            query = query.replace(", includeInactive: true", "");
        }
        Object response = tester.post(query);
        Map<String, List<Map<String, String>>> swfl = chainGet(response, "data", "suggestedWorkForLabware");
        assertThat(swfl.get("suggestedWorks")).containsExactlyInAnyOrder(
                Map.of("barcode", "STAN-A1", "workNumber", work.getWorkNumber()),
                nullableMapOf("barcode", "STAN-A2", "workNumber", null)
        );
        assertThat(swfl.get("works")).containsExactly(Map.of("workNumber", work.getWorkNumber()));
    }
}
