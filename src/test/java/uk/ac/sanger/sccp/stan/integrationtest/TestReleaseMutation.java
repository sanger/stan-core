package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.BasicLocation;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.ReleaseColumn;
import uk.ac.sanger.sccp.stan.service.releasefile.ReleaseFileMode;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
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

    @MockBean
    JavaMailSender mockMailSender;

    @Test
    @Transactional
    public void testRelease() throws Exception {
        entityCreator.createOpType("Probe hybridisation Xenium", null, OperationTypeFlag.IN_PLACE);
        entityCreator.createOpType("Probe hybridisation QC", null, OperationTypeFlag.IN_PLACE);
        entityCreator.createOpType("Xenium analyser", null, OperationTypeFlag.IN_PLACE);
        entityCreator.createOpType("Xenium QC", null, OperationTypeFlag.IN_PLACE);
        Work work1 = entityCreator.createWork(null, null, null, null, null);
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
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("dr6");
        tester.setUser(user);
        String mutation = tester.readGraphQL("release.graphql")
                .replace("BC1", "STAN-001")
                .replace("BC2", "STAN-002")
                .replace("WN1", work1.getWorkNumber())
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

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("Stan test<no-reply@sanger.ac.uk>", message.getFrom());
        assertThat(message.getTo()).containsExactly(recipient.getUsername()+"@sanger.ac.uk");
        assertThat(message.getCc()).containsExactly("beagledev@sanger.ac.uk");
        String releaseUrl = "stantestroot/releaseOptions?id=" + releaseIds.stream().map(Object::toString).collect(joining(","));
        assertEquals("Release to "+recipient.getUsername()+"@sanger.ac.uk for work number "+work1.getWorkNumber()+
                ".\nThe details of the release are available at "+releaseUrl, message.getText());

        String tsvString = getReleaseFile(releaseIds, EnumSet.allOf(ReleaseFileOption.class));
        var tsvMaps = tsvToMap(tsvString);
        assertEquals(tsvMaps.size(), 4);
        Set<String> expectedColumns = Arrays.stream(ReleaseColumn.values())
                .filter(c -> c.getMode()!=ReleaseFileMode.CDNA)
                .map(ReleaseColumn::toString)
                .collect(toSet());
        assertThat(tsvMaps.get(0).keySet()).containsExactlyInAnyOrderElementsOf(expectedColumns);
        var row0 = tsvMaps.get(0);
        assertMapValue(row0, ReleaseColumn.Released_labware_barcode, block.getBarcode());
        assertMapValue(row0, ReleaseColumn.Labware_type, block.getLabwareType().getName());
        assertMapValue(row0, ReleaseColumn.Last_section_number, "6");
        assertMapValue(row0, ReleaseColumn.Released_from_box_location, "A2");
        String bsName = sample.getBioState().getName();
        for (int i = 1; i < 4; ++i) {
            var row = tsvMaps.get(i);
            assertMapValue(row, ReleaseColumn.Released_labware_barcode, lw.getBarcode());
            assertMapValue(row, ReleaseColumn.Labware_type, lw.getLabwareType().getName());
            assertMapValue(row, ReleaseColumn.Released_from_box_location, "C4");
            assertMapValue(row, ReleaseColumn.Stain_type, st.getName());
            assertMapValue(row, ReleaseColumn.Bond_barcode, bondBarcode);
            assertMapValue(row, ReleaseColumn.Biological_state,bsName);
            assertMapValue(row, ReleaseColumn.IHC_plex, String.valueOf(ihcPlex));
            assertMapValue(row, ReleaseColumn.RNAscope_plex, String.valueOf(rnaPlex));
        }

        entityManager.refresh(work1);
        assertThat(work1.getReleaseIds()).contains(releaseIds.get(0));

        entityCreator.createOpType("Unrelease", null, OperationTypeFlag.IN_PLACE);

        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getProgram(),
                work1.getCostCode(), work1.getWorkRequester());

        String unreleaseMutation = tester.readGraphQL("unrelease.graphql")
                .replace("BARCODE", lw.getBarcode())
                .replace("WORK", work2.getWorkNumber());
        result = tester.post(unreleaseMutation);
        Object unreleaseResult = chainGet(result, "data", "unrelease");
        assertEquals("active", chainGet(unreleaseResult, "labware", 0, "state"));
        assertEquals("Unrelease", chainGet(unreleaseResult, "operations", 0, "operationType", "name"));
        final Integer opId = chainGet(unreleaseResult, "operations", 0, "id");
        assertNotNull(opId);
        entityManager.refresh(lw);
        assertTrue(lw.isReleased());
        entityManager.flush();
        entityManager.refresh(work2);
        assertThat(work2.getOperationIds()).containsExactly(opId);
    }

    private static <V> void assertMapValue(Map<? super String, V> map, Object column, V expected) {
        String key = column.toString();
        assertEquals(expected, map.get(key), key);
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


    private String getReleaseFile(List<Integer> releaseIds, Set<ReleaseFileOption> options) throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        releaseIds.forEach(id -> params.add("id", id.toString()));
        options.forEach(rfo -> params.add("groups", rfo.getQueryParamName()));
        return tester.getMockMvc().perform(MockMvcRequestBuilders.get("/release")
                        .queryParams(params)).andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @Transactional
    public void testReleaseFileOptionsQuery() throws Exception {
        String query = tester.readGraphQL("releasecolumnoptions.graphql");
        List<Map<String, String>> maps = chainGet(tester.post(query), "data", "releaseColumnOptions");
        ReleaseFileOption[] options = ReleaseFileOption.values();
        assertThat(maps).hasSize(options.length);
        for (int i = 0; i < options.length; ++i) {
            ReleaseFileOption option = options[i];
            Map<String, String> map = maps.get(i);
            assertEquals(option.getDisplayName(), map.get("displayName"));
            assertEquals(option.getQueryParamName(), map.get("queryParamName"));
        }
    }

}
