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
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests the register mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRegisterMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private OperationRepo opRepo;

    @Test
    @Transactional
    public void testRegister() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        String mutation = tester.readGraphQL("register.graphql");
        Object result = tester.post(mutation);
        Object data = chainGet(result, "data", "register");
        assertThat(chainGetList(data, "clashes")).isEmpty();
        String barcode = chainGet(data, "labware", 0, "barcode");
        assertNotNull(barcode);
        Map<String, ?> tissueData = chainGet(data, "labware", 0, "slots", 0, "samples", 0, "tissue");
        assertEquals("TISSUE1", tissueData.get("externalName"));
        assertEquals("Human", chainGet(tissueData, "donor", "species", "name"));
        assertEquals("2021-02-03", tissueData.get("collectionDate"));

        result = tester.post(mutation);
        data = chainGet(result, "data", "register");
        assertThat(chainGetList(data, "labware")).isEmpty();
        List<Map<String, ?>> clashes = chainGet(data, "clashes");
        assertThat(clashes).hasSize(1);
        assertEquals("TISSUE1", chainGet(clashes, 0, "tissue", "externalName"));
        assertEquals(barcode, chainGet(clashes, 0, "labware", 0, "barcode"));
    }


    @Test
    @Transactional
    public void testSectionRegister() throws Exception {
        String mutation = tester.readGraphQL("registersections.graphql");
        User user = entityCreator.createUser("user1");
        tester.setUser(user);

        Map<String, ?> response = tester.post(mutation);

        assertNull(response.get("errors"));
        Map<String,?> data = chainGet(response, "data", "registerSections");

        List<Map<String,?>> labwareData = chainGet(data, "labware");
        assertThat(labwareData).hasSize(1);
        Map<String, ?> lwData = labwareData.get(0);
        String barcode = (String) lwData.get("barcode");
        assertNotNull(barcode);
        List<Map<String, ?>> slotsData = chainGetList(lwData, "slots");
        assertThat(slotsData).hasSize(6);
        Map<String, List<String>> addressExtNames = new HashMap<>(4);
        Map<String, List<String>> addressDonorNames = new HashMap<>(4);
        for (var slotData : slotsData) {
            String ad = (String) slotData.get("address");
            assertFalse(addressExtNames.containsKey(ad));
            List<Map<String,?>> samplesData = chainGet(slotData, "samples");
            addressExtNames.put(ad, new ArrayList<>(samplesData.size()));
            addressDonorNames.put(ad, new ArrayList<>(samplesData.size()));
            for (var sampleData : samplesData) {
                addressExtNames.get(ad).add(chainGet(sampleData, "tissue", "externalName"));
                addressDonorNames.get(ad).add(chainGet(sampleData, "tissue", "donor", "donorName"));
            }
        }
        Map<String, List<String>> expectedExtNames = Map.of(
                "A1", List.of("TISSUE1", "TISSUE2"),
                "A2", List.of(),
                "B1", List.of(),
                "B2", List.of("TISSUE3"),
                "C1", List.of(),
                "C2", List.of()
        );
        Map<String, List<String>> expectedDonorNames = Map.of(
                "A1", List.of("DONOR1", "DONOR2"),
                "A2", List.of(),
                "B1", List.of(),
                "B2", List.of("DONOR1"),
                "C1", List.of(),
                "C2", List.of()
        );
        assertEquals(expectedExtNames, addressExtNames);
        assertEquals(expectedDonorNames, addressDonorNames);

        entityManager.flush();

        Labware labware = lwRepo.getByBarcode(barcode);

        final Slot slotA1 = labware.getSlot(new Address(1, 1));
        assertThat(slotA1.getSamples()).hasSize(2);
        assertEquals("7", slotA1.getSamples().get(0).getTissue().getReplicate());
        assertEquals("9a", slotA1.getSamples().get(1).getTissue().getReplicate());

        final Slot slotB2 = labware.getSlot(new Address(2, 2));
        assertThat(slotB2.getSamples()).hasSize(1);
        Sample sample = slotB2.getSamples().get(0);
        assertEquals("TISSUE3", sample.getTissue().getExternalName());
        assertEquals("DONOR1", sample.getTissue().getDonor().getDonorName());
        assertEquals("8", sample.getTissue().getReplicate());
        assertEquals(11, sample.getSection());

        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(List.of(slotB2.getId()));
        assertThat(measurements).hasSize(1);
        Measurement measurement = measurements.get(0);
        assertNotNull(measurement.getId());
        assertEquals("Thickness", measurement.getName());
        assertEquals("14", measurement.getValue());
        assertEquals(sample.getId(), measurement.getSampleId());
        assertNotNull(measurement.getOperationId());
        Operation op = opRepo.findById(measurement.getOperationId()).orElseThrow();
        assertEquals("Register", op.getOperationType().getName());
        assertThat(op.getActions()).hasSize(3);
        assertEquals(user, op.getUser());
    }

}
