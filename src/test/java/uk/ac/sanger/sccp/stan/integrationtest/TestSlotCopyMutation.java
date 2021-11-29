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
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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

    @MockBean
    StorelightClient mockStorelightClient;

    @Test
    @Transactional
    public void testSlotCopy() throws Exception {
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
        slide1.getSlot(A1).getSamples().add(samples[0]);
        slide1.getSlot(B1).getSamples().addAll(List.of(samples[0], samples[1]));

        slotRepo.saveAll(List.of(slide1.getSlot(A1), slide1.getSlot(B1)));

        stubStorelightUnstore(mockStorelightClient);

        Work work = entityCreator.createWork(null, null, null);
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        String mutation = tester.readGraphQL("slotcopy.graphql");
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
        assertThat(slotsData).hasSize(96);
        Map<String, ?> slotA1Data = slotsData.stream().filter(sd -> sd.get("address").equals("A1"))
                .findAny().orElseThrow();
        Map<String, ?> slotA2Data = slotsData.stream().filter(sd -> sd.get("address").equals("A2"))
                .findAny().orElseThrow();
        List<Integer> A1SampleIds = IntegrationTestUtils.<List<Map<String,?>>>chainGet(slotA1Data, "samples")
                .stream()
                .map((Map<String, ?> m) -> (Integer) (m.get("id")))
                .collect(toList());
        assertThat(A1SampleIds).hasSize(1).doesNotContainNull();
        Integer newSample1Id = A1SampleIds.get(0);
        List<Integer> A2SampleIds = IntegrationTestUtils.<List<Map<String,?>>>chainGet(slotA2Data, "samples")
                .stream()
                .map((Map<String, ?> m) -> (Integer) (m.get("id")))
                .collect(toList());
        assertThat(A2SampleIds).hasSize(2).doesNotContainNull().doesNotHaveDuplicates().contains(newSample1Id);
        Integer newSample2Id = A2SampleIds.stream().filter(n -> !n.equals(newSample1Id)).findAny().orElseThrow();

        List<Map<String, ?>> opsData = chainGet(data, "operations");
        assertThat(opsData).hasSize(1);
        Map<String, ?> opData = opsData.get(0);
        assertNotNull(opData.get("id"));
        int opId = (int) opData.get("id");
        assertEquals("Visium cDNA", chainGet(opData, "operationType", "name"));
        List<Map<String, ?>> actionsData = chainGet(opData, "actions");
        assertThat(actionsData).hasSize(3);
        int sourceLabwareId = slide1.getId();
        Map<String, String> bsData = Map.of("name", "cDNA");
        assertThat(actionsData).containsExactlyInAnyOrder(
                Map.of("source", Map.of("address", "A1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", "A1", "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample1Id, "bioState", bsData)),
                Map.of("source", Map.of("address", "B1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", "A2", "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample1Id, "bioState", bsData)),
                Map.of("source", Map.of("address", "B1", "labwareId", sourceLabwareId),
                        "destination", Map.of("address", "A2", "labwareId", destLabwareId),
                        "sample", Map.of("id", newSample2Id, "bioState", bsData))
        );

        entityManager.flush();
        entityManager.refresh(slide1);
        assertTrue(slide1.isDiscarded());
        Operation op = opRepo.findById(opId).orElseThrow();
        assertNotNull(op.getPerformed());
        assertEquals("Visium cDNA", op.getOperationType().getName());
        assertThat(op.getActions()).hasSize(actionsData.size());
        Labware newLabware = lwRepo.getById(destLabwareId);
        assertThat(newLabware.getSlot(A1).getSamples()).hasSize(1);
        assertTrue(newLabware.getSlot(A1).getSamples().stream().allMatch(sam -> sam.getBioState().getName().equals("cDNA")));
        assertThat(newLabware.getSlot(A2).getSamples()).hasSize(2);
        assertTrue(newLabware.getSlot(A2).getSamples().stream().allMatch(sam -> sam.getBioState().getName().equals("cDNA")));

        verifyStorelightQuery(mockStorelightClient, List.of("STAN-01"), user.getUsername());
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).containsExactly(opId);
        assertThat(work.getSampleSlotIds()).hasSize(3);
    }
}
