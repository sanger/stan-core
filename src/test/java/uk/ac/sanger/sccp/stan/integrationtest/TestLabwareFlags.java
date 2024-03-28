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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests recording and lookup up labware flags
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLabwareFlags {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;

    @Transactional
    @Test
    public void testRecordFlags() throws Exception {
        User user = entityCreator.createUser("user1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        entityCreator.createOpType("Flag labware", null, OperationTypeFlag.IN_PLACE);
        Sample sample = entityCreator.createSample(null, null);
        Sample sample2 = entityCreator.createSample(sample.getTissue(), null);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        Labware lw1 = entityCreator.createLabware("STAN-1", lt, sample);
        tester.setUser(user);
        String mutation = tester.readGraphQL("flaglabware.graphql");
        Object response1 = tester.post(mutation.replace("[BC]", lw1.getBarcode())
                .replace("[WORKNUM]", work.getWorkNumber())
                .replace("[DESC]", "Alpha"));
        Integer op1Id = chainGet(response1, "data", "flagLabware", "operations", 0, "id");
        assertNotNull(op1Id);

        OperationType ot = entityCreator.createOpType("ot", null);

        Labware lw2 = createOp(lw1, lt, ot, user, sample2, "STAN-2");
        Object response2 = tester.post(mutation.replace("[BC]", lw2.getBarcode())
                .replace("\"[WORKNUM]\"", "null")
                .replace("[DESC]", "Beta"));
        Integer op2Id = chainGet(response2, "data", "flagLabware", "operations", 0, "id");
        assertNotNull(op2Id);

        createOp(lw2, lt, ot, user, sample2, "STAN-3");
        testLookUpFlags();

    }

    private Labware createOp(Labware source, LabwareType lt, OperationType ot, User user, Sample sample, String barcode) {
        Labware lw = entityCreator.createLabware(barcode, lt, sample);
        entityCreator.simpleOp(ot, user, source, lw);
        return lw;
    }

    private void testLookUpFlags() throws Exception {
        String query = tester.readGraphQL("lookupflags.graphql");
        Object response = tester.post(query);
        List<Map<String, ?>> lfds = chainGet(response, "data", "labwareFlagDetails");
        assertThat(lfds).hasSize(2);
        Map<String, ?> lfd1 = lfds.stream()
                .filter(d -> d.get("barcode").equals("STAN-1"))
                .findAny()
                .orElseThrow();
        Map<String, ?> lfd3 = lfds.stream()
                .filter(d -> d.get("barcode").equals("STAN-3"))
                .findAny()
                .orElseThrow();

        List<Map<String, String>> flags1 = chainGet(lfd1, "flags");
        assertThat(flags1).containsExactly(Map.of("barcode", "STAN-1", "description", "Alpha"));

        List<Map<String, String>> flags3 = chainGet(lfd3, "flags");
        assertThat(flags3).containsExactlyInAnyOrder(
                Map.of("barcode", "STAN-1", "description", "Alpha"),
                Map.of("barcode", "STAN-2", "description", "Beta")
        );

        query = tester.readGraphQL("labwareflagged.graphql");
        response = tester.post(query);
        assertEquals("STAN-1", chainGet(response, "data", "labwareFlagged", "barcode"));
        assertEquals(Boolean.TRUE, chainGet(response, "data", "labwareFlagged", "flagged"));
    }
}
