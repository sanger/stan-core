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
import java.util.*;
import java.util.stream.IntStream;

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
    private LabwareRepo lwRepo;
    @Autowired
    private DestructionRepo destructionRepo;
    @Autowired
    private DestructionReasonRepo destructionReasonRepo;

    @MockBean
    StorelightClient mockStorelightClient;

    @Test
    @Transactional
    public void testRegister() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        String mutation = tester.readResource("graphql/register.graphql");
        Map<String, Map<String, Map<String, List<Map<String, String>>>>> result = tester.post(mutation);
        assertNotNull(result.get("data").get("register").get("labware").get(0).get("barcode"));
        assertEquals("TISSUE1", result.get("data").get("register").get("tissue").get(0).get("externalName"));
    }

    @Test
    @Transactional
    public void testPlanAndRecordOperation() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1"), null);
        Labware sourceBlock = entityCreator.createBlock("STAN-B70C", sample);
        String mutation = tester.readResource("graphql/plan.graphql");
        mutation = mutation.replace("$sampleId", String.valueOf(sample.getId()));
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
        assertEquals(1, resultActions.size());
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
        assertNotNull(chainGet(slots, 0, "samples", 0, "id"));

        resultOps = chainGet(resultConfirm, "operations");
        assertEquals(resultOps.size(), 1);
        Object resultOp = resultOps.get(0);
        assertNotNull(chainGet(resultOp, "performed"));
        assertEquals("Section", chainGet(resultOp, "operationType", "name"));
        List<?> actions = chainGet(resultOp, "actions");
        assertEquals(1, actions.size());
        Object action = actions.get(0);
        assertEquals((Integer) 1, chainGet(action, "sample", "section"));
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
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1");
        Labware lw = entityCreator.createLabware("STAN-SLIDE", entityCreator.createLabwareType("slide6", 3, 2),
                entityCreator.createSample(tissue, 1), entityCreator.createSample(tissue, 2),
                entityCreator.createSample(tissue, 3), entityCreator.createSample(tissue, 4, rna));
        Printer printer = entityCreator.createPrinter("stub");
        String mutation = "mutation { printLabware(barcodes: [\"STAN-SLIDE\"], printer: \"stub\") }";
        assertThat(tester.<Map<?,?>>post(mutation)).isEqualTo(Map.of("data", Map.of("printLabware", "OK")));
        String donorName = tissue.getDonor().getDonorName();
        String tissueDesc = getTissueDesc(tissue);
        Integer replicate = tissue.getReplicate();
        verify(mockPrintClient).print("stub", new LabelPrintRequest(
                lw.getLabwareType().getLabelType(),
                List.of(new LabwareLabelData(lw.getBarcode(), tissue.getMedium().getName(),
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
        Donor donor = entityCreator.createDonor("DONOR1", LifeStage.adult);
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, null);
        Sample sample1 = entityCreator.createSample(tissue, 1);
        LabwareType lwtype = entityCreator.createLabwareType("lwtype4", 1, 4);
        Labware block = entityCreator.createBlock("STAN-001", sample);
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
                "Life stage", "Tissue type", "Spatial location", "Replicate number", "Section number",
                "Last section number", "Source barcode", "Section thickness");
        var row0 = tsvMaps.get(0);
        assertEquals(block.getBarcode(), row0.get("Barcode"));
        assertEquals(block.getLabwareType().getName(), row0.get("Labware type"));
        assertEquals("1", row0.get("Last section number"));
        for (int i = 1; i < 4; ++i) {
            var row = tsvMaps.get(i);
            assertEquals(lw.getBarcode(), row.get("Barcode"));
            assertEquals(lw.getLabwareType().getName(), row.get("Labware type"));
        }
    }

    @Test
    @Transactional
    public void testExtract() throws Exception {
        Donor donor = entityCreator.createDonor("DONOR1", LifeStage.adult);
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
