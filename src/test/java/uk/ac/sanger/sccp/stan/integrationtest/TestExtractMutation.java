package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import uk.ac.sanger.sccp.stan.request.EquipmentCategory;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
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
public class TestExtractMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BioStateRepo bioStateRepo;
    @Autowired
    private SampleRepo sampleRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private SlotRepo slotRepo;
    @Autowired
    private ResultOpRepo resultOpRepo;
    @Autowired
    private OperationCommentRepo opCommentRepo;
    @Autowired
    private EquipmentRepo equipmentRepo;

    @MockBean
    StorelightClient mockStorelightClient;

    @ParameterizedTest
    @Transactional
   @MethodSource("equipments")
    public void testExtract(String expectedEquipmentName, Equipment equipment) throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE");
        BioState tissueBs = bioStateRepo.getByName("Tissue");
        Sample[] samples = IntStream.range(0,2)
                .mapToObj(i -> entityCreator.createSample(tissue, null, tissueBs))
                .toArray(Sample[]::new);
        LabwareType lwType = entityCreator.createLabwareType("lwtype", 1, 1);
        String[] barcodes = { "STAN-A1", "STAN-A2" };
        Labware[] sources = IntStream.range(0, samples.length)
                .mapToObj(i -> entityCreator.createLabware(barcodes[i], lwType, samples[i]))
                .toArray(Labware[]::new);
        sources[1].getFirstSlot().addSample(samples[0]);
        sources[1].getSlots().set(0, slotRepo.save(sources[1].getFirstSlot())); // source 1 contains samples 1 and 0

        stubStorelightUnstore(mockStorelightClient);

        WorkType wt = entityCreator.createWorkType("Rocks");
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("4");
        ReleaseRecipient wr = entityCreator.createReleaseRecipient("test1");
        Work work = entityCreator.createWork(wt, pr, null, cc, wr);
        Integer equipmentId = equipment != null ? equipmentRepo.save(equipment).getId() : null;
        String mutation = tester.readGraphQL("extract.graphql")
                .replace("[]", "[\"STAN-A1\", \"STAN-A2\"]")
                .replace("LWTYPE", lwType.getName())
                .replace("999", String.valueOf(equipmentId))
                .replace("SGP4000", work.getWorkNumber());
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Object result = tester.post(mutation);

        Object extractData = chainGet(result, "data", "extract");
        List<Map<String,?>> lwData = chainGet(extractData, "labware");
        assertThat(lwData).hasSize(2);
        for (int i = 0; i < lwData.size(); i++) {
            Map<String, ?> lwd = lwData.get(i);
            assertEquals(lwType.getName(), chainGet(lwd, "labwareType", "name"));
            assertThat((String) lwd.get("barcode")).startsWith("STAN-");
            List<?> slotData = chainGet(lwd, "slots");
            assertThat(slotData).hasSize(1);
            List<?> sampleData = chainGet(slotData, 0, "samples");
            assertThat(sampleData).hasSize(i+1);
            assertNotNull(chainGet(sampleData, 0, "id"));
        }

        List<Map<String,?>> opData = chainGet(extractData, "operations");
        assertThat(opData).hasSize(2);
        int[] sampleIds = new int[2];
        int[] destIds = new int[2];
        int[] opIds = new int[2];
        for (int i = 0; i < 2; ++i) {
            Map<String, ?> opd = opData.get(i);
            Map<String, ?> lwd = lwData.get(i);
            opIds[i] = (int) opd.get("id");
            assertEquals("Extract", chainGet(opd, "operationType", "name"));
            assertNotNull(opd.get("performed"));
            List<Map<String,?>> actionData = chainGet(opd, "actions");
            assertThat(actionData).hasSize(i+1);
            Set<Integer> actionSampleIds = actionData.stream()
                    .<Integer>map(ad -> chainGet(ad, "sample", "id"))
                    .collect(toSet());
            Map<String, ?> acd = actionData.get(0);
            int sampleId = chainGet(acd, "sample", "id");
            sampleIds[i] = sampleId;
            int lwId = (int) lwd.get("id");
            destIds[i] = lwId;
            assertEquals(lwId, (int) chainGet(acd, "destination", "labwareId"));
            assertEquals("A1", chainGet(acd, "destination", "address"));
            assertThat(actionSampleIds).contains((Integer) chainGet(lwd, "slots", 0, "samples", 0, "id"));
            assertEquals(sources[i].getId(), (int) chainGet(acd, "source", "labwareId"));
        }

        entityManager.flush();

        Sample[] newSamples = Arrays.stream(sampleIds)
                .mapToObj((int id) -> sampleRepo.findById(id).orElseThrow())
                .toArray(Sample[]::new);
        Labware[] dests = Arrays.stream(destIds)
                .mapToObj(id -> lwRepo.findById(id).orElseThrow())
                .toArray(Labware[]::new);
        Operation[] ops = Arrays.stream(opIds)
                .mapToObj(id -> opRepo.findById(id).orElseThrow())
                .toArray(Operation[]::new);
        for (int i = 0; i < 2; ++i) {
            entityManager.refresh(sources[i]);
            assertTrue(sources[i].isDiscarded());

            Labware dest = dests[i];
            Sample sample = newSamples[i];
            assertEquals(lwType, dest.getLabwareType());
            assertEquals("RNA", sample.getBioState().getName());
            Operation op = ops[i];
            assertEquals("Extract", op.getOperationType().getName());
            assertThat(op.getActions()).hasSize(i+1);
            Action action = op.getActions().get(0);
            assertEquals(sources[i].getId(), action.getSource().getLabwareId());
            assertEquals(dest.getFirstSlot(), action.getDestination());
            assertNotNull(op.getPerformed());
            if(op.getEquipment() != null) {
                assertEquals(expectedEquipmentName, op.getEquipment().getName());
            } else {
                assertNull(expectedEquipmentName);
            }

        }

        verifyStorelightQuery(mockStorelightClient, List.of(sources[0].getBarcode(), sources[1].getBarcode()), user.getUsername());

        entityManager.flush();
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).containsExactlyInAnyOrderElementsOf(Arrays.stream(opIds).boxed().collect(toList()));
        assertThat(work.getSampleSlotIds()).hasSize(3);

        entityCreator.createOpType("Record result", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);

        mutation = tester.readGraphQL("extract_result.graphql")
                .replace("$BARCODE1$", dests[0].getBarcode())
                .replace("$BARCODE2$", dests[1].getBarcode())
                .replace("$WORKNUM$", work.getWorkNumber());
        result = tester.post(mutation);

        List<Map<String,?>> opsData = chainGet(result, "data", "recordExtractResult", "operations");
        List<Integer> resultOpIds = new ArrayList<>(2);
        for (var opsDatum : opsData) {
            assertEquals("Record result", chainGet(opsDatum, "operationType", "name"));
            resultOpIds.add((Integer) opsDatum.get("id"));
        }

        List<ResultOp> ros = resultOpRepo.findAllByOperationIdIn(resultOpIds);
        ResultOp ro0, ro1;
        if (ros.get(0).getOperationId().equals(resultOpIds.get(0))) {
            ro0 = ros.get(0);
            ro1 = ros.get(1);
        } else {
            ro0 = ros.get(1);
            ro1 = ros.get(0);
        }
        assertEquals(resultOpIds.get(0), ro0.getOperationId());
        assertEquals(PassFail.pass, ro0.getResult());
        assertEquals(resultOpIds.get(1), ro1.getOperationId());
        assertEquals(PassFail.fail, ro1.getResult());

        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(resultOpIds);
        assertThat(measurements).hasSize(1);
        Measurement meas = measurements.get(0);
        assertEquals(dests[0].getFirstSlot().getId(), meas.getSlotId());
        assertEquals("RNA concentration", meas.getName());
        assertEquals("-200.00", meas.getValue());
        assertEquals(resultOpIds.get(0), meas.getOperationId());

        List<OperationComment> opComs = opCommentRepo.findAllByOperationIdIn(resultOpIds);
        assertThat(opComs).hasSize(2);
        for (OperationComment opCom : opComs) {
            assertEquals(1, opCom.getComment().getId());
            assertEquals(resultOpIds.get(1), opCom.getOperationId());
            assertEquals(dests[1].getFirstSlot().getId(), opCom.getSlotId());
        }

        result = tester.post(tester.readGraphQL("extractresult.graphql").replace("$BARCODE", dests[0].getBarcode()));
        extractData = chainGet(result, "data", "extractResult");
        assertEquals(dests[0].getBarcode(), chainGet(extractData, "labware", "barcode"));
        assertEquals("pass", chainGet(extractData, "result"));
        assertEquals("-200.00", chainGet(extractData, "concentration"));

        entityCreator.createOpType("RIN analysis", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS);

        result = tester.post(tester.readGraphQL("analysis.graphql")
                .replace("$BARCODE", dests[0].getBarcode()).replace("SGP4000", work.getWorkNumber()));

        Integer opId = chainGet(result, "data", "recordRNAAnalysis", "operations", 0, "id");
        measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(measurements).hasSize(1);
        meas = measurements.get(0);
        assertEquals(dests[0].getFirstSlot().getId(), meas.getSlotId());
        assertEquals("RIN", meas.getName());
        assertEquals("55.0", meas.getValue());
    }

    static Stream<Arguments> equipments() {
        return Arrays.stream(new Object[][] {
                {"Robot X", new Equipment( "Robot X", EquipmentCategory.extract.name())},
                {null, null}
        }).map(Arguments::of);
    }
}
