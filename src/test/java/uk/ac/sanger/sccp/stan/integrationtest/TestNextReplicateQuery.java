package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import uk.ac.sanger.sccp.stan.model.store.Location;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.GraphQLClient;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.nullableMapOf;

/**
 * Tests nextReplicateNumbers query.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestNextReplicateQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @SuppressWarnings("unchecked")
    @Test
    @Transactional
    public void testFind() throws Exception {
        Donor donor1 = entityCreator.createDonor("DONOR1");
        Donor donor2 = entityCreator.createDonor("DONOR2");

        Tissue[] tissues = {
                entityCreator.createTissue(donor1, "TISSUE1", null),
                entityCreator.createTissue(donor1, "TISSUE2", "10b"),
                entityCreator.createTissue(donor1, "TISSUE3", "9"),
                entityCreator.createTissue(donor2, "TISSUE4", null)
        };
        Sample[] samples = Arrays.stream(tissues)
                .map(t -> entityCreator.createSample(t, null))
                .toArray(Sample[]::new);

        final LabwareType lt = entityCreator.getTubeType();
        final PrimitiveIterator.OfInt intIter = IntStream.rangeClosed(1,samples.length).iterator();

        Arrays.stream(samples)
                .forEach(sam -> entityCreator.createLabware("STAN-A"+intIter.next(), lt, sam));

        String query = tester.readGraphQL("nextreplicatenumbers.graphql");

        Object response = tester.post(query);

        List<Map<String, ?>> results = chainGet(response, "data", "nextReplicateNumbers");
        assertThat(results).hasSize(2);
        Map<String, ?> result1 = results.get(0), result2;
        if (result1.get("donorId").equals(donor1.getId())) {
            result2 = results.get(1);
        } else {
            result2 = result1;
            result1 = results.get(1);
        }
        assertThat((List<String>) result1.get("barcodes")).containsExactlyInAnyOrder("STAN-A1", "STAN-A2");
        assertEquals(donor1.getId(), result1.get("donorId"));
        assertEquals(tissues[0].getSpatialLocation().getId(), result1.get("spatialLocationId"));
        assertEquals(11, result1.get("nextReplicateNumber"));

        assertThat((List<String>) result2.get("barcodes")).containsExactly("STAN-A4");
        assertEquals(donor2.getId(), result2.get("donorId"));
        assertEquals(tissues[3].getSpatialLocation().getId(), result2.get("spatialLocationId"));
        assertEquals(1, result2.get("nextReplicateNumber"));
    }

}
