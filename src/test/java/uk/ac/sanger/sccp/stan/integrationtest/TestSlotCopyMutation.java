package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

/**
 * Tests the slot copy mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSlotCopyMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SlotRepo slotRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private LabwareTypeRepo lwTypeRepo;
    @Autowired
    private LabwareNoteRepo lwNoteRepo;

    @MockBean
    StorelightClient mockStorelightClient;

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    @Transactional
    public void testSlotCopy(boolean cytAssist) throws Exception {
        OperationType cytOpType = null;
        if (cytAssist) {
            lwTypeRepo.save(new LabwareType(null, "CytAssist 6.5", 4, 1, null, true));
            BioState bs = entityCreator.createBioState("Probes");
            cytOpType = entityCreator.createOpType("CytAssist", bs, OperationTypeFlag.MARK_SOURCE_USED);
        }
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample[] samples = IntStream.range(1, 3)
                .mapToObj(i -> entityCreator.createSample(tissue, i))
                .toArray(Sample[]::new);
        LabwareType slideType = entityCreator.createLabwareType("4x1", 4, 1);
        Labware slide1 = entityCreator.createLabware("STAN-01", slideType);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B1 = new Address(2,1);
        final Address D1 = new Address(4,1);
        slide1.getSlot(A1).getSamples().add(samples[0]);
        slide1.getSlot(B1).getSamples().addAll(List.of(samples[0], samples[1]));

        slotRepo.saveAll(List.of(slide1.getSlot(A1), slide1.getSlot(B1)));

        stubStorelightUnstore(mockStorelightClient);

        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        String mutation = tester.readGraphQL(cytAssist ? "cytassist.graphql":"slotcopy.graphql");
        mutation = mutation.replace("SGP5000", work.getWorkNumber());
        Object result = tester.post(mutation);
        Object data = chainGet(result, "data", "slotCopy");
        List<Map<String, ?>> lwsData = chainGet(data, "labware");
        assertThat(lwsData).hasSize(1);
        Map<String, ?> lwData = lwsData.get(0);
        Integer destLabwareId = (Integer) lwData.get("id");
        assertNotNull(destLabwareId);
        assertNotNull(lwData.get("barcode"));
        List<Map<String, ?>> slotsData = chainGetList(lwData, "slots");
        assertThat(slotsData).hasSize(cytAssist ? 4 : 96);
        Map<String, ?> slot1Data = slotsData.stream().filter(sd -> sd.get("address").equals("A1"))
                .findAny().orElseThrow();
        Map<String, ?> slot2Data = slotsData.stream().filter(sd -> sd.get("address").equals(cytAssist ? "D1" : "A2"))
                .findAny().orElseThrow();
        List<Integer> slot1SampleIds = IntegrationTestUtils.<List<Map<String,?>>>chainGet(slot1Data, "samples")
                .stream()
                .map((Map<String, ?> m) -> (Integer) (m.get("id")))
                .collect(toList());
        assertThat(slot1SampleIds).hasSize(1).doesNotContainNull();
        Integer newSample1Id = slot1SampleIds.get(0);
        List<Integer> slot2SampleIds = IntegrationTestUtils.<List<Map<String,?>>>chainGet(slot2Data, "samples")
                .stream()
                .map((Map<String, ?> m) -> (Integer) (m.get("id")))
                .collect(toList());
        assertThat(slot2SampleIds).hasSize(2).doesNotContainNull().doesNotHaveDuplicates().contains(newSample1Id);
        Integer newSample2Id = slot2SampleIds.stream().filter(n -> !n.equals(newSample1Id)).findAny().orElseThrow();

        List<Map<String, ?>> opsData = chainGet(data, "operations");
        assertThat(opsData).hasSize(1);
        Map<String, ?> opData = opsData.get(0);
        assertNotNull(opData.get("id"));
        int opId = (int) opData.get("id");
        assertEquals(cytAssist ? cytOpType.getName() : "Visium cDNA", chainGet(opData, "operationType", "name"));
        List<Map<String, ?>> actionsData = chainGet(opData, "actions");
        assertThat(actionsData).hasSize(3);
        int sourceLabwareId = slide1.getId();
        final String newBsName = cytAssist ? cytOpType.getNewBioState().getName() : "cDNA";
        Map<String, String> bsData = Map.of("name", newBsName);
        String slot2AddressString = cytAssist ? "D1" : "A2";
        assertThat(actionsData).containsExactlyInAnyOrder(
                Map.of("source", Map.of("address", "A1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", "A1", "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample1Id, "bioState", bsData)),
                Map.of("source", Map.of("address", "B1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", slot2AddressString, "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample1Id, "bioState", bsData)),
                Map.of("source", Map.of("address", "B1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", slot2AddressString, "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample2Id, "bioState", bsData))
        );

        entityManager.flush();
        entityManager.refresh(slide1);
        assertTrue(slide1.isUsed());
        Operation op = opRepo.findById(opId).orElseThrow();
        assertNotNull(op.getPerformed());
        assertEquals(cytAssist ? cytOpType.getName() : "Visium cDNA", op.getOperationType().getName());
        assertThat(op.getActions()).hasSize(actionsData.size());
        Labware newLabware = lwRepo.getById(destLabwareId);
        assertThat(newLabware.getSlot(A1).getSamples()).hasSize(1);
        assertTrue(newLabware.getSlot(A1).getSamples().stream().allMatch(sam -> sam.getBioState().getName().equals(newBsName)));
        assertThat(newLabware.getSlot(cytAssist ? D1 : A2).getSamples()).hasSize(2);
        assertTrue(newLabware.getSlot(cytAssist ? D1 : A2).getSamples().stream().allMatch(sam -> sam.getBioState().getName().equals(newBsName)));
        if (cytAssist) {
            List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
            assertThat(notes).hasSize(5);
            Map<String, String> noteMap = notes.stream().collect(toMap(LabwareNote::getName, LabwareNote::getValue));
            notes.forEach(note -> assertEquals(destLabwareId, note.getLabwareId()));
            assertEquals("Faculty", noteMap.get("costing"));
            assertEquals("1234567", noteMap.get("lot"));
            assertEquals("7777777", noteMap.get("probe lot"));
            assertEquals("000000", noteMap.get("reagent lot"));
            assertEquals("SGP", noteMap.get("reagent costing"));
        }

        verifyNoInteractions(mockStorelightClient);
        //verifyStorelightQuery(mockStorelightClient, List.of("STAN-01"), user.getUsername());
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).containsExactly(opId);
        assertThat(work.getSampleSlotIds()).hasSize(3);
    }

    @Test
    @Transactional
    public void testTransferMore() throws Exception {
        entityCreator.createOpType("Transfer", null, OperationTypeFlag.ACTIVE_DEST);
        User user = entityCreator.createUser("user1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        tester.setUser(user);
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt0 = entityCreator.getTubeType();
        entityCreator.createLabware("STAN-01", lt0, sample);
        String mutation0 = tester.readGraphQL("transfer.graphql")
                .replace("SGP5000", work.getWorkNumber());

        Object result = tester.post(mutation0);
        int destId = chainGet(result, "data", "slotCopy", "labware", 0, "id");

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B1 = new Address(2,1);
        final Address B2 = new Address(2,2);

        Labware dest = lwRepo.getById(destId);
        assertThat(dest.getSlot(A1).getSamples()).isNotEmpty();
        assertThat(dest.getSlot(A2).getSamples()).isNotEmpty();
        assertThat(dest.getSlot(B1).getSamples()).isEmpty();
        assertThat(dest.getSlot(B2).getSamples()).isEmpty();

        String mutation1 = tester.readGraphQL("transfer_more.graphql")
                .replace("SGP5000", work.getWorkNumber())
                .replace("[BC]", dest.getBarcode());

        result = tester.post(mutation1);
        assertNotNull(chainGet(result, "data", "slotCopy", "labware", 0));
        Stream.of(A1, A2, B1, B2).forEach(ad -> assertThat(dest.getSlot(ad).getSamples()).isNotEmpty());
    }
}
