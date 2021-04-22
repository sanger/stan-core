package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.Location;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;
import uk.ac.sanger.sccp.stan.service.label.print.PrintClient;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-exhaustive integration tests.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class IntegrationTests {

    @Autowired
    private GraphQLTester tester;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private LabwarePrintRepo labwarePrintRepo;
    @Autowired
    private BioStateRepo bioStateRepo;
    @Autowired
    private SampleRepo sampleRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private SlotRepo slotRepo;
    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private SpeciesRepo speciesRepo;
    @Autowired
    private ReleaseDestinationRepo releaseDestinationRepo;
    @Autowired
    private ReleaseRecipientRepo releaseRecipientRepo;
    @Autowired
    private HmdmcRepo hmdmcRepo;
    @Autowired
    private DestructionRepo destructionRepo;
    @Autowired
    private DestructionReasonRepo destructionReasonRepo;
    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private CommentRepo commentRepo;
    @Autowired
    private UserRepo userRepo;

    @MockBean
    StorelightClient mockStorelightClient;

    @Test
    @Transactional
    public void testRegister() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        String mutation = tester.readResource("graphql/register.graphql");
        Object result = tester.post(mutation);
        Object data = chainGet(result, "data", "register");
        assertThat(chainGetList(data, "clashes")).isEmpty();
        String barcode = chainGet(data, "labware", 0, "barcode");
        assertNotNull(barcode);
        Map<String, ?> tissueData = chainGet(data, "labware", 0, "slots", 0, "samples", 0, "tissue");
        assertEquals("TISSUE1", tissueData.get("externalName"));
        assertEquals("Human", chainGet(tissueData, "donor", "species", "name"));

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
    public void testPlanAndRecordOperation() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), null);
        Labware sourceBlock = entityCreator.createBlock("STAN-B70C", sample);
        String mutation = tester.readResource("graphql/plan.graphql");
        mutation = mutation.replace("55555", String.valueOf(sample.getId()));
        Map<String, ?> result = tester.post(mutation);
        assertNull(result.get("errors"));
        Object resultPlan = chainGet(result, "data", "plan");
        List<?> planResultLabware = chainGet(resultPlan, "labware");
        assertEquals(1, planResultLabware.size());
        String barcode = chainGet(planResultLabware, 0, "barcode");
        assertNotNull(barcode);
        assertEquals("Tube", chainGet(planResultLabware, 0, "labwareType", "name"));
        List<?> resultOps = chainGet(resultPlan, "operations");
        assertEquals(resultOps.size(), 1);
        assertEquals("Section", chainGet(resultOps, 0, "operationType", "name"));
        List<?> resultActions = chainGet(resultOps, 0, "planActions");
        assertEquals(2, resultActions.size());
        Map<String, ?> resultAction = chainGet(resultActions, 0);
        assertEquals("A1", chainGet(resultAction, "source", "address"));
        assertEquals(sourceBlock.getId(), chainGet(resultAction, "source", "labwareId"));
        assertEquals("A1", chainGet(resultAction, "destination", "address"));
        assertNotNull(chainGet(resultAction, "destination", "labwareId"));
        assertEquals(sample.getId(), chainGet(resultAction, "sample", "id"));
        assertNotNull(chainGet(resultAction, "newSection"));

        String recordMutation = tester.readResource("graphql/confirm.graphql");
        recordMutation = recordMutation.replace("$BARCODE", barcode);
        result = tester.post(recordMutation);
        assertNull(result.get("errors"));

        Object resultConfirm = chainGet(result, "data", "confirmOperation");
        List<?> resultLabware = chainGet(resultConfirm, "labware");
        assertEquals(1, resultLabware.size());
        assertEquals(barcode, chainGet(resultLabware, 0, "barcode"));
        List<?> slots = chainGet(resultLabware, 0, "slots");
        assertEquals(1, slots.size());
        assertEquals((Integer) 1, chainGet(slots, 0, "samples", 0, "section"));
        assertThat(chainGetList(slots, 0, "samples")).hasSize(2);
        assertNotNull(chainGet(slots, 0, "samples", 0, "id"));
        assertEquals((Integer) 1, chainGet(slots, 0, "samples", 0, "section"));
        assertEquals((Integer) 2, chainGet(slots, 0, "samples", 1, "section"));

        resultOps = chainGet(resultConfirm, "operations");
        assertEquals(resultOps.size(), 1);
        Object resultOp = resultOps.get(0);
        assertNotNull(chainGet(resultOp, "performed"));
        assertEquals("Section", chainGet(resultOp, "operationType", "name"));
        List<?> actions = chainGet(resultOp, "actions");
        assertEquals(2, actions.size());
        Object action = actions.get(0);
        assertEquals((Integer) 1, chainGet(action, "sample", "section"));
        assertEquals("A1", chainGet(action, "destination", "address").toString());
        action = actions.get(1);
        assertEquals((Integer) 2, chainGet(action, "sample", "section"));
        assertEquals("A1", chainGet(action, "destination", "address").toString());
    }

    @Test
    @Transactional
    public void testPrintLabware() throws Exception {
        //noinspection unchecked
        PrintClient<LabelPrintRequest> mockPrintClient = mock(PrintClient.class);
        when(tester.mockPrintClientFactory.getClient(any())).thenReturn(mockPrintClient);
        tester.setUser(entityCreator.createUser("dr6"));
        BioState rna = bioStateRepo.getByName("RNA");
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1");
        Labware lw = entityCreator.createLabware("STAN-SLIDE", entityCreator.createLabwareType("slide6", 3, 2),
                entityCreator.createSample(tissue, 1), entityCreator.createSample(tissue, 2),
                entityCreator.createSample(tissue, 3), entityCreator.createSample(tissue, 4, rna));
        lw.setCreated(LocalDateTime.of(2021,3,17,15,57));
        Printer printer = entityCreator.createPrinter("stub");
        String mutation = "mutation { printLabware(barcodes: [\"STAN-SLIDE\"], printer: \"stub\") }";
        assertThat(tester.<Map<?,?>>post(mutation)).isEqualTo(Map.of("data", Map.of("printLabware", "OK")));
        String donorName = tissue.getDonor().getDonorName();
        String tissueDesc = getTissueDesc(tissue);
        Integer replicate = tissue.getReplicate();
        verify(mockPrintClient).print("stub", new LabelPrintRequest(
                lw.getLabwareType().getLabelType(),
                List.of(new LabwareLabelData(lw.getBarcode(), tissue.getMedium().getName(), "2021-03-17",
                        List.of(
                                new LabelContent(donorName, tissueDesc, replicate, 1),
                                new LabelContent(donorName, tissueDesc, replicate, 2),
                                new LabelContent(donorName, tissueDesc, replicate, 3),
                                new LabelContent(donorName, tissueDesc, replicate, "RNA")
                        ))
                ))
        );

        Iterator<LabwarePrint> recordIter = labwarePrintRepo.findAll().iterator();
        assertTrue(recordIter.hasNext());
        LabwarePrint record = recordIter.next();
        assertFalse(recordIter.hasNext());

        assertNotNull(record.getPrinted());
        assertNotNull(record.getId());
        assertEquals(record.getLabware().getId(), lw.getId());
        assertEquals(record.getPrinter().getId(), printer.getId());
        assertEquals(record.getUser().getUsername(), "dr6");
    }

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
        ReleaseDestination destination = entityCreator.createReleaseDestination("Venus");
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("Mekon");
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        String mutation = tester.readResource("graphql/release.graphql")
                .replace("[]", "[\"STAN-001\", \"STAN-002\"]")
                .replace("DESTINATION", destination.getName())
                .replace("RECIPIENT", recipient.getUsername());

        stubStorelightUnstore();

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

        verifyUnstored(List.of("STAN-001", "STAN-002"), user.getUsername());

        List<Integer> releaseIds = releaseData.stream()
                .map(rd -> (Integer) rd.get("id"))
                .collect(toList());
        String tsvString = getReleaseFile(releaseIds);
        var tsvMaps = tsvToMap(tsvString);
        assertEquals(tsvMaps.size(), 4);
        assertThat(tsvMaps.get(0).keySet()).containsOnly("Barcode", "Labware type", "Address", "Donor name",
                "Life stage", "External identifier", "Tissue type", "Spatial location", "Replicate number", "Section number",
                "Last section number", "Source barcode", "Section thickness");
        var row0 = tsvMaps.get(0);
        assertEquals(block.getBarcode(), row0.get("Barcode"));
        assertEquals(block.getLabwareType().getName(), row0.get("Labware type"));
        assertEquals("6", row0.get("Last section number"));
        for (int i = 1; i < 4; ++i) {
            var row = tsvMaps.get(i);
            assertEquals(lw.getBarcode(), row.get("Barcode"));
            assertEquals(lw.getLabwareType().getName(), row.get("Labware type"));
        }
    }

    @Test
    @Transactional
    public void testExtract() throws Exception {
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

        stubStorelightUnstore();

        String mutation = tester.readResource("graphql/extract.graphql")
                .replace("[]", "[\"STAN-A1\", \"STAN-A2\"]")
                .replace("LWTYPE", lwType.getName());
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Object result = tester.post(mutation);

        Object extractData = chainGet(result, "data", "extract");
        List<Map<String,?>> lwData = chainGet(extractData, "labware");
        assertThat(lwData).hasSize(2);
        for (var lwd : lwData) {
            assertEquals(lwType.getName(), chainGet(lwd, "labwareType", "name"));
            assertThat((String) lwd.get("barcode")).startsWith("STAN-");
            List<?> slotData = chainGet(lwd, "slots");
            assertThat(slotData).hasSize(1);
            List<?> sampleData = chainGet(slotData, 0, "samples");
            assertThat(sampleData).hasSize(1);
            assertNotNull(chainGet(sampleData, 0, "id"));
        }

        List<Map<String,?>> opData = chainGet(extractData, "operations");
        assertThat(opData).hasSize(2);
        if (sources[0].getId().equals(chainGet(opData, 1, "actions", 0, "source", "labwareId"))) {
            swap(opData, 0, 1);
        }
        if (chainGet(opData, 0, "actions", 0, "destination", "labwareId").equals(
                chainGet(lwData, 1, "id"))) {
            swap(lwData, 0, 1);
        }
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
            assertThat(actionData).hasSize(1);
            Map<String, ?> acd = actionData.get(0);
            int sampleId = chainGet(acd, "sample", "id");
            sampleIds[i] = sampleId;
            int lwId = (int) lwd.get("id");
            destIds[i] = lwId;
            assertEquals(lwId, (int) chainGet(acd, "destination", "labwareId"));
            assertEquals("A1", chainGet(acd, "destination", "address"));
            assertEquals(sampleId, (int) chainGet(lwd, "slots", 0, "samples", 0, "id"));
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
            Slot slot = dest.getFirstSlot();
            assertThat(slot.getSamples()).containsOnly(sample);
            assertEquals("RNA", sample.getBioState().getName());
            Operation op = ops[i];
            assertEquals("Extract", op.getOperationType().getName());
            assertThat(op.getActions()).hasSize(1);
            Action action = op.getActions().get(0);
            assertEquals(sources[i].getId(), action.getSource().getLabwareId());
            assertEquals(samples[i], action.getSourceSample());
            assertEquals(dest.getFirstSlot(), action.getDestination());
            assertEquals(sample, action.getSample());
            assertNotNull(op.getPerformed());
        }

        verifyUnstored(List.of(sources[0].getBarcode(), sources[1].getBarcode()), user.getUsername());
    }

    @Test
    @Transactional
    public void testFind() throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1");
        BioState bs = entityCreator.anyBioState();
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", 2);

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
        GraphQLResponse storelightResponse = new GraphQLResponse(storelightDataNode, null);
        when(mockStorelightClient.postQuery(anyString(), any())).thenReturn(storelightResponse);

        String query = tester.readResource("graphql/find_tissue.graphql").replace("TISSUE_NAME", tissue1.getExternalName());

        Object response = tester.post(query);
        final Object findData = chainGet(response, "data", "find");

        List<Map<String, ?>> entriesData = chainGet(findData, "entries");
        assertThat(entriesData).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 2)
                        .mapToObj(i -> Map.of("labwareId", labware[i].getId(), "sampleId", samples[i].getId()))
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

        String mutation = tester.readResource("graphql/destroy.graphql")
                .replace("[]", "[\"STAN-A1\", \"STAN-B2\"]")
                .replace("99", String.valueOf(reason.getId()));

        stubStorelightUnstore();

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

        verifyUnstored(barcodes, user.getUsername());
    }

    @Test
    @Transactional
    public void testSectionRegister() throws Exception {
        String mutation = tester.readResource("graphql/registersections.graphql");
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

        assertThat(labware.getSlot(new Address(1,1)).getSamples()).hasSize(2);
        final Slot slotB2 = labware.getSlot(new Address(2, 2));
        assertThat(slotB2.getSamples()).hasSize(1);
        Sample sample = slotB2.getSamples().get(0);
        assertEquals("TISSUE3", sample.getTissue().getExternalName());
        assertEquals("DONOR1", sample.getTissue().getDonor().getDonorName());
        assertEquals(8, sample.getTissue().getReplicate());
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

        stubStorelightUnstore();
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        String mutation = tester.readResource("graphql/slotcopy.graphql");
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
        List<Integer> A1SampleIds = IntegrationTests.<List<Map<String,?>>>chainGet(slotA1Data, "samples")
                .stream()
                .map((Map<String, ?> m) -> (Integer) (m.get("id")))
                .collect(toList());
        assertThat(A1SampleIds).hasSize(1).doesNotContainNull();
        Integer newSample1Id = A1SampleIds.get(0);
        List<Integer> A2SampleIds = IntegrationTests.<List<Map<String,?>>>chainGet(slotA2Data, "samples")
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

        verifyUnstored(List.of("STAN-01"), user.getUsername());
    }

    @Test
    @Transactional
    public void testAddCommentNonAdmin() throws Exception {
        String mutation = tester.readResource("graphql/addnewcomment.graphql");
        tester.setUser(entityCreator.createUser("normo", User.Role.normal));
        Object result = tester.post(mutation);
        String errorMessage = chainGet(result, "errors", 0, "message");
        assertThat(errorMessage).contains("Requires role: admin");
        assertThat(commentRepo.findByCategoryAndText("section", "Fell in the bin.")).isEmpty();
    }

    @Test
    @Transactional
    public void testAddCommentAdmin() throws Exception {
        final String category = "section";
        final String text = "Fell in the bin.";
        String mutation = tester.readResource("graphql/addnewcomment.graphql");
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        Object result = tester.post(mutation);
        assertEquals(Map.of("category", category, "text", text, "enabled", true),
                chainGet(result, "data", "addComment"));
        Comment comment = commentRepo.findByCategoryAndText(category, text).orElseThrow();
        assertEquals(category, comment.getCategory());
        assertEquals(text, comment.getText());
        assertTrue(comment.isEnabled());
    }

    @Test
    @Transactional
    public void testSetCommentEnabled() throws Exception {
        Comment comment = StreamSupport.stream(commentRepo.findAll().spliterator(), false)
                .filter(Comment::isEnabled)
                .findAny()
                .orElseThrow();
        String mutation = tester.readResource("graphql/setcommentenabled.graphql")
                .replace("666", String.valueOf(comment.getId()));
        tester.setUser(entityCreator.createUser("admo"));
        Object result = tester.post(mutation);
        assertEquals(Map.of("category", comment.getCategory(), "text", comment.getText(), "enabled", false),
                chainGet(result, "data", "setCommentEnabled"));

        assertFalse(commentRepo.getById(comment.getId()).isEnabled());
    }

    public <E extends HasEnabled> void testGenericAddNewAndSetEnabled(String entityTypeName, String fieldName,
                                                                      String string,
                                                                      Function<String, Optional<E>> findFunction,
                                                                      Function<E, String> stringFunction,
                                                                      String queryString) throws Exception {
        String mutation = tester.readResource("graphql/genericaddnew.graphql")
                .replace("Species", entityTypeName).replace("name", fieldName)
                .replace("Unicorn", string);
        tester.setUser(entityCreator.createUser("dr6"));
        Object result = tester.post(mutation);
        Map<String, ?> newEntityMap = Map.of(fieldName, string, "enabled", true);
        assertEquals(newEntityMap, chainGet(result, "data", "add"+entityTypeName));
        E entity = findFunction.apply(string).orElseThrow();
        assertEquals(string, stringFunction.apply(entity));
        assertTrue(entity.isEnabled());

        result = tester.post(String.format("query { %s { %s, enabled }}", queryString, fieldName));
        assertThat(chainGetList(result, "data", queryString)).contains(newEntityMap);

        mutation = tester.readResource("graphql/genericsetenabled.graphql")
                .replace("Species", entityTypeName).replace("name", fieldName).replace("Unicorn", string);
        result = tester.post(mutation);
        newEntityMap = Map.of(fieldName, string, "enabled", false);
        assertEquals(newEntityMap, chainGet(result, "data", "set"+entityTypeName+"Enabled"));
        entity = findFunction.apply(string).orElseThrow();
        assertFalse(entity.isEnabled());
    }

    @Test
    @Transactional
    public void testAddSpeciesAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Species", "name", "Unicorn", speciesRepo::findByName, Species::getName, "species");
    }
    @Test
    @Transactional
    public void testAddReleaseDestinationAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("ReleaseDestination", "name", "Venus", releaseDestinationRepo::findByName, ReleaseDestination::getName, "releaseDestinations");
    }
    @Test
    @Transactional
    public void testAddReleaseRecipientAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("ReleaseRecipient", "username", "mekon", releaseRecipientRepo::findByUsername, ReleaseRecipient::getUsername, "releaseRecipients");
    }
    @Test
    @Transactional
    public void testAddHmdmcAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Hmdmc", "hmdmc", "12/345", hmdmcRepo::findByHmdmc, Hmdmc::getHmdmc, "hmdmcs");
    }
    @Test
    @Transactional
    public void testAddDestructionReasonAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("DestructionReason", "text", "Dropped.", destructionReasonRepo::findByText, DestructionReason::getText, "destructionReasons");
    }

    @Test
    @Transactional
    public void testAddUserNonAdmin() throws Exception {
        tester.setUser(entityCreator.createUser("normo", User.Role.normal));
        String mutation = tester.readResource("graphql/adduser.graphql");
        Object result = tester.post(mutation);
        String errorMessage = chainGet(result, "errors", 0, "message");
        assertThat(errorMessage).contains("Requires role: admin");
        assertThat(userRepo.findByUsername("ford")).isEmpty();
    }

    @Test
    @Transactional
    public void testAddUserAndSetRole() throws Exception {
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        String mutation = tester.readResource("graphql/adduser.graphql");
        Object result = tester.post(mutation);
        Map<String, String> userMap = Map.of("username", "ford", "role", "normal");
        assertEquals(userMap, chainGet(result, "data", "addUser"));
        final String userQuery = "query { users { username, role }}";
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).contains(userMap);

        mutation = tester.readResource("graphql/setuserrole.graphql");
        result = tester.post(mutation);
        userMap = Map.of("username", "ford", "role", "disabled");
        assertEquals(userMap, chainGet(result, "data", "setUserRole"));
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).noneMatch(map -> "ford".equalsIgnoreCase((String) ((Map<?,?>) map).get("username")));

        mutation = mutation.replace("disabled", "normal");
        result = tester.post(mutation);
        userMap = Map.of("username", "ford", "role", "normal");
        assertEquals(userMap, chainGet(result, "data", "setUserRole"));
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).contains(userMap);
    }

    private void stubStorelightUnstore() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode storelightDataNode = objectMapper.createObjectNode()
                .set("unstoreBarcodes", objectMapper.createObjectNode().put("numUnstored", 2));
        GraphQLResponse storelightResponse = new GraphQLResponse(storelightDataNode, null);
        when(mockStorelightClient.postQuery(anyString(), anyString())).thenReturn(storelightResponse);
    }

    private void verifyUnstored(Collection<String> barcodes, String username) throws Exception {
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStorelightClient).postQuery(queryCaptor.capture(), eq(username));
        String storelightQuery = queryCaptor.getValue();
        assertThat(storelightQuery).contains(barcodes);
    }

    private List<Map<String, String>> tsvToMap(String tsv) {
        String[] lines = tsv.split("\n");
        String[] headers = lines[0].split("\t");
        return IntStream.range(1, lines.length)
                .mapToObj(i -> lines[i].split("\t", -1))
                .map(values ->
                    IntStream.range(0, headers.length)
                            .boxed()
                            .collect(toMap(j -> headers[j], j -> values[j]))
        ).collect(toList());
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

    private static String getTissueDesc(Tissue tissue) {
        String prefix;
        switch (tissue.getDonor().getLifeStage()) {
            case paediatric:
                prefix = "P";
                break;
            case fetal:
                prefix = "F";
                break;
            default:
                prefix = "";
        }
        return String.format("%s%s-%s", prefix, tissue.getTissueType().getCode(), tissue.getSpatialLocation().getCode());
    }

    @SuppressWarnings("unchecked")
    private static <T> T chainGet(Object container, Object... accessors) {
        for (int i = 0; i < accessors.length; i++) {
            Object accessor = accessors[i];
            assert container != null;
            Object item;
            if (accessor instanceof Integer) {
                if (!(container instanceof List)) {
                    throw new IllegalArgumentException("["+accessor+"]: container is not list: "+container);
                }
                item = ((List<?>) container).get((int) accessor);
            } else {
                if (!(container instanceof Map)) {
                    throw new IllegalArgumentException("["+accessor+"]: container is not map: "+container);
                }
                item = ((Map<?, ?>) container).get(accessor);
            }
            if (item==null && i < accessors.length-1) {
                throw new IllegalArgumentException("No such element as "+accessor+" in object "+container);
            }
            container = item;
        }
        return (T) container;
    }

    private static <T> List<T> chainGetList(Object container, Object... accessors) {
        return chainGet(container, accessors);
    }

    private static <E> void swap(List<E> list, int i, int j) {
        list.set(i, list.set(j, list.get(i)));
    }

    private static <K, V> Map<K, V> nullableMapOf(K key1, V value1, K key2, V value2, K key3, V value3) {
        Map<K, V> map = new HashMap<>(3);
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        return map;
    }
}
