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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests the labware query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLabwareQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    public void testLabwareQuery() throws Exception {
        LabwareType lt = entityCreator.createLabwareType("pair", 2, 1);
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DNR1"), "EXT1"),
                25);
        Labware lw = entityCreator.createLabware("STAN-100", lt, sample);

        String query = tester.readGraphQL("labware.graphql").replace("BARCODE", lw.getBarcode());
        Object result = tester.post(query);
        Map<String, ?> lwData = chainGet(result, "data", "labware");
        assertEquals("active", lwData.get("state"));
        assertEquals(lw.getCreated().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")), lwData.get("created"));
        assertEquals(lt.getName(), chainGet(lwData, "labwareType", "name"));
        List<Map<String, ?>> slotsData = chainGetList(lwData, "slots");
        assertEquals(2, slotsData.size());
        Map<String, ?> firstSlotData = slotsData.get(0);
        assertEquals("A1", firstSlotData.get("address"));
        assertEquals(1, chainGetList(firstSlotData, "samples").size());
    }

}
