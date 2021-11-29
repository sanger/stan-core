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
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.DestructionReasonRepo;
import uk.ac.sanger.sccp.stan.repo.DestructionRepo;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

/**
 * Tests the extract mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestDestroyMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DestructionReasonRepo destructionReasonRepo;
    @Autowired
    private DestructionRepo destructionRepo;

    @MockBean
    StorelightClient mockStorelightClient;


    @Test
    @Transactional
    public void testDestroy() throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, 1);
        final List<String> barcodes = List.of("STAN-A1", "STAN-B2");
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        Labware[] labware = barcodes.stream()
                .map(bc -> entityCreator.createLabware(bc, lt, sample))
                .toArray(Labware[]::new);
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        DestructionReason reason = destructionReasonRepo.save(new DestructionReason(null, "Everything."));

        String mutation = tester.readGraphQL("destroy.graphql")
                .replace("[]", "[\"STAN-A1\", \"STAN-B2\"]")
                .replace("99", String.valueOf(reason.getId()));

        stubStorelightUnstore(mockStorelightClient);

        Object response = tester.post(mutation);
        entityManager.flush();

        List<Map<String, ?>> destructionsData = chainGet(response, "data", "destroy", "destructions");
        assertThat(destructionsData).hasSize(labware.length);
        for (int i = 0; i < labware.length; ++i) {
            Labware lw = labware[i];
            Map<String, ?> destData = destructionsData.get(i);
            assertEquals(lw.getBarcode(), chainGet(destData, "labware", "barcode"));
            assertTrue((boolean) chainGet(destData, "labware", "destroyed"));
            assertEquals(reason.getText(), chainGet(destData, "reason", "text"));
            assertNotNull(destData.get("destroyed"));
            assertEquals(user.getUsername(), chainGet(destData, "user", "username"));

            entityManager.refresh(lw);
            assertTrue(lw.isDestroyed());
        }

        List<Destruction> destructions = destructionRepo.findAllByLabwareIdIn(List.of(labware[0].getId(), labware[1].getId()));
        assertEquals(labware.length, destructions.size());

        verifyStorelightQuery(mockStorelightClient, barcodes, user.getUsername());
    }
}
