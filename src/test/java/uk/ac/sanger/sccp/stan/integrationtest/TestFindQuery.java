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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.nullableMapOf;

/**
 * Tests the find tissue query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestFindQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @MockBean
    StorelightClient mockStorelightClient;

    @Test
    @Transactional
    public void testFind() throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1");
        BioState bs = entityCreator.anyBioState();
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", "2");

        Sample[] samples = {
                entityCreator.createSample(tissue1, 1, bs),
                entityCreator.createSample(tissue1, 2, bs),
                entityCreator.createSample(tissue2, 3, bs),
        };

        LabwareType lt1 = entityCreator.createLabwareType("lt1", 1, 1);

        Labware[] labware = {
                entityCreator.createLabware("STAN-01", lt1, samples[0]),
                entityCreator.createLabware("STAN-02", lt1, samples[1]),
                entityCreator.createLabware("STAN-03", lt1, samples[2]),
        };

        Project pr = new Project(1, "project", true);
        CostCode cc = new CostCode(1, "cc1");
        WorkType workType = new WorkType(1, "worktype", true);
        ReleaseRecipient workRequester = new ReleaseRecipient(1, "test1");
        Work work = entityCreator.createWork(workType, pr, cc,workRequester);
        List<String> workNumbers = List.of(work.getWorkNumber());
        work.setSampleSlotIds(List.of(
                new Work.SampleSlotId(samples[0].getId(), labware[0].getSlots().get(0).getId()),
                new Work.SampleSlotId(samples[1].getId(), labware[1].getSlots().get(0).getId()),
                new Work.SampleSlotId(samples[2].getId(), labware[2].getSlots().get(0).getId())
        ));

        Location[] locations = {
                new Location(), new Location()
        };
        locations[0].setId(10);
        locations[0].setBarcode("STO-10");
        locations[1].setId(20);
        locations[1].setBarcode("STO-20");

        String[] storageAddresses = {null, "B3"};

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode[] locationNodes = Arrays.stream(locations)
                .map(loc -> objectMapper.createObjectNode()
                        .put("id", loc.getId())
                        .put("barcode", loc.getBarcode()))
                .toArray(ObjectNode[]::new);
        ObjectNode[] storedItemNodes = IntStream.range(0, 2)
                .<ObjectNode>mapToObj(i -> objectMapper.createObjectNode()
                        .put("barcode", labware[i].getBarcode())
                        .put("address", storageAddresses[i])
                        .set("location", locationNodes[i])
                )
                .toArray(ObjectNode[]::new);
        ArrayNode storedItemArray = objectMapper.createArrayNode()
                .addAll(Arrays.asList(storedItemNodes));
        ObjectNode storelightDataNode = objectMapper.createObjectNode()
                .set("stored", storedItemArray);
        GraphQLClient.GraphQLResponse storelightResponse = new GraphQLClient.GraphQLResponse(storelightDataNode, null);
        when(mockStorelightClient.postQuery(anyString(), any())).thenReturn(storelightResponse);

        String query = tester.readGraphQL("find_tissue.graphql").replace("TISSUE_NAME", tissue1.getExternalName());

        Object response = tester.post(query);
        final Object findData = chainGet(response, "data", "find");

        List<Map<String, ?>> entriesData = chainGet(findData, "entries");
        assertThat(entriesData).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 2)
                        .mapToObj(i -> Map.of("labwareId", labware[i].getId(), "sampleId", samples[i].getId(), "workNumbers", workNumbers))
                        .collect(toList())
        );

        List<Map<String, ?>> lwData = chainGet(findData, "labware");
        assertThat(lwData).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(labware, 0, 2)
                        .map(lw -> Map.of("id", lw.getId(), "barcode", lw.getBarcode()))
                        .collect(toList())
        );

        List<Map<String, ?>> samplesData = chainGet(findData, "samples");
        assertThat(samplesData).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(samples, 0, 2)
                        .map(sam -> Map.of("id", sam.getId(), "section", sam.getSection()))
                        .collect(toList())
        );

        List<Map<String, ?>> locationsData = chainGet(findData, "locations");
        assertThat(locationsData).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(locations)
                        .map(loc -> Map.of("id", loc.getId(), "barcode", loc.getBarcode()))
                        .collect(toList())
        );

        List<Map<String, ?>> labwareLocationsData = chainGet(findData, "labwareLocations");
        assertThat(labwareLocationsData).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 2)
                        .mapToObj(i -> nullableMapOf("labwareId", labware[i].getId(), "locationId", locations[i].getId(), "address", storageAddresses[i]))
                        .collect(toList())
        );
    }

}
