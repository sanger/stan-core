package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.BasicLocation;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

/**
 * Tests the release and unrelease mutations
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestReleaseMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SlotRepo slotRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private OperationTypeRepo opTypeRepo;
    @Autowired
    private LabwareNoteRepo lwNoteRepo;
    @Autowired
    private StainTypeRepo stainTypeRepo;

    @MockBean
    StorelightClient mockStorelightClient;

    @Test
    @Transactional
    public void testRelease() throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, null);
        Sample sample1 = entityCreator.createSample(tissue, 1);
        LabwareType lwtype = entityCreator.createLabwareType("lwtype4", 1, 4);
        Labware block = entityCreator.createBlock("STAN-001", sample);
        Slot blockSlot = block.getFirstSlot();
        blockSlot.setBlockSampleId(sample.getId());
        blockSlot.setBlockHighestSection(6);
        blockSlot = slotRepo.save(blockSlot);
        block.getSlots().set(0, blockSlot);
        Labware lw = entityCreator.createLabware("STAN-002", lwtype, sample, sample, null, sample1);
        User user = entityCreator.createUser("user1");

        StainType st = stainTypeRepo.save(new StainType(null, "Varnish"));
        String bondBarcode = "1234ABCD";
        Integer rnaPlex = 15;
        Integer ihcPlex = 16;
        recordStain(lw, st, bondBarcode, rnaPlex, ihcPlex, user);

        ReleaseDestination destination = entityCreator.createReleaseDestination("Venus");
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("Mekon");
        tester.setUser(user);
        String mutation = tester.readGraphQL("release.graphql")
                .replace("[]", "[\"STAN-001\", \"STAN-002\"]")
                .replace("DESTINATION", destination.getName())
                .replace("RECIPIENT", recipient.getUsername());

        stubStorelightUnstore(mockStorelightClient);
        UCMap<BasicLocation> basicLocationMap = new UCMap<>(2);
        basicLocationMap.put("STAN-001", new BasicLocation("STO-1", new Address(1,2)));
        basicLocationMap.put("STAN-002", new BasicLocation("STO-1", new Address(3,4)));
        stubStorelightBasicLocation(mockStorelightClient, basicLocationMap);

        Object result = tester.post(mutation);

        List<Map<String, ?>> releaseData = chainGet(result, "data", "release", "releases");
        assertThat(releaseData).hasSize(2);
        List<String> barcodesData = releaseData.stream()
                .<String>map(map -> chainGet(map, "labware", "barcode"))
                .collect(toList());
        assertThat(barcodesData).containsOnly("STAN-001", "STAN-002");
        for (Map<String, ?> releaseItem : releaseData) {
            assertEquals(destination.getName(), chainGet(releaseItem, "destination", "name"));
            assertEquals(recipient.getUsername(), chainGet(releaseItem, "recipient", "username"));
            assertTrue((boolean) chainGet(releaseItem, "labware", "released"));
        }

        verifyStorelightQuery(mockStorelightClient, List.of("STAN-001", "STAN-002"), user.getUsername());

        List<Integer> releaseIds = releaseData.stream()
                .map(rd -> (Integer) rd.get("id"))
                .collect(toList());

        String tsvString = getReleaseFile(releaseIds);
        var tsvMaps = tsvToMap(tsvString);
        assertEquals(tsvMaps.size(), 4);
        assertThat(tsvMaps.get(0).keySet()).containsOnly("Barcode", "Labware type", "Address", "Donor name",
                "Life stage", "External identifier", "Tissue type", "Spatial location", "Replicate number", "Section number",
                "Last section number", "Source barcode", "Section thickness", "Released from box location",
                "Stain type", "Bond barcode", "Tissue coverage", "Cq value", "Visium concentration", "Visium concentration type",
                "Dual index plate name", "RNAscope plex", "IHC plex", "Date sectioned", "Permeabilisation time");
        var row0 = tsvMaps.get(0);
        assertEquals(block.getBarcode(), row0.get("Barcode"));
        assertEquals(block.getLabwareType().getName(), row0.get("Labware type"));
        assertEquals("6", row0.get("Last section number"));
        assertEquals("A2", row0.get("Released from box location"));
        for (int i = 1; i < 4; ++i) {
            var row = tsvMaps.get(i);
            assertEquals(lw.getBarcode(), row.get("Barcode"));
            assertEquals(lw.getLabwareType().getName(), row.get("Labware type"));
            assertEquals("C4", row.get("Released from box location"));
            assertEquals(st.getName(), row.get("Stain type"));
            assertEquals(bondBarcode, row.get("Bond barcode"));
            assertEquals(String.valueOf(ihcPlex), row.get("IHC plex"));
            assertEquals(String.valueOf(rnaPlex), row.get("RNAscope plex"));
        }

        entityCreator.createOpType("Unrelease", null, OperationTypeFlag.IN_PLACE);

        String unreleaseMutation = tester.readGraphQL("unrelease.graphql")
                .replace("BARCODE", lw.getBarcode());
        result = tester.post(unreleaseMutation);
        Object unreleaseResult = chainGet(result, "data", "unrelease");
        assertEquals("active", chainGet(unreleaseResult, "labware", 0, "state"));
        assertEquals("Unrelease", chainGet(unreleaseResult, "operations", 0, "operationType", "name"));
        assertNotNull(chainGet(unreleaseResult, "operations", 0, "id"));
        entityManager.refresh(lw);
        assertTrue(lw.isReleased());
    }

    private void recordStain(Labware lw, StainType st, String bondBarcode, Integer rnaPlex, Integer ihcPlex, User user) {
        OperationType opType = opTypeRepo.getByName("Stain");
        Operation op = new Operation(null, opType, null, null, user);
        op = opRepo.save(op);
        if (st!=null) {
            stainTypeRepo.saveOperationStainTypes(op.getId(), List.of(st));
        }
        Slot slot = lw.getFirstSlot();
        Sample sample = slot.getSamples().get(0);
        actionRepo.save(new Action(null, op.getId(), slot, slot, sample, sample));
        lwNoteRepo.save(new LabwareNote(null, lw.getId(), op.getId(), "Bond barcode", bondBarcode));
        if (rnaPlex!=null) {
            lwNoteRepo.save(new LabwareNote(null, lw.getId(), op.getId(), "RNAscope plex", String.valueOf(rnaPlex)));
        }
        if (ihcPlex!=null) {
            lwNoteRepo.save(new LabwareNote(null, lw.getId(), op.getId(), "IHC plex", String.valueOf(ihcPlex)));
        }
        entityManager.refresh(op);
    }


    private String getReleaseFile(List<Integer> releaseIds) throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        releaseIds.forEach(id -> params.add("id", id.toString()));
        return tester.getMockMvc().perform(MockMvcRequestBuilders.get("/release")
                        .queryParams(params)).andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

}
