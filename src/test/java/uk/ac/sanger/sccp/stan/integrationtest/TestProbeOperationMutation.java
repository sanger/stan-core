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
import uk.ac.sanger.sccp.stan.service.CompletionServiceImp;
import uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * Tests recordProbeOperation mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestProbeOperationMutation {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    OperationRepo opRepo;
    @Autowired
    LabwareProbeRepo lwProbeRepo;
    @Autowired
    OperationCommentRepo opComRepo;
    @Autowired
    RoiRepo roiRepo;
    @Autowired
    LabwareNoteRepo lwNoteRepo;
    @Autowired
    private EquipmentRepo equipmentRepo;
    @Autowired
    private RoiMetricRepo roiMetricRepo;

    @Test
    @Transactional
    public void testRecordProbeOperation() throws Exception {
        User user = entityCreator.createUser("user1");
        String addProbeMutation = tester.readGraphQL("createprobepanels.graphql");
        tester.setUser(user);
        Object result = tester.post(addProbeMutation.replace("PROBENAME", "probe1"));
        assertEquals("probe1", chainGet(result, "data", "addProbePanel", "name"));
        result = tester.post(addProbeMutation.replace("PROBENAME", "probe2"));
        assertEquals("probe2", chainGet(result, "data", "addProbePanel", "name"));
        result = tester.post(addProbeMutation.replace("PROBENAME", "william").replace("xenium", "spike"));
        assertEquals("william", chainGet(result, "data", "addProbePanel", "name"));

        OperationType opType = entityCreator.createOpType(CompletionServiceImp.PROBE_HYBRIDISATION_NAMES.getFirst(), null, OperationTypeFlag.PROBES, OperationTypeFlag.IN_PLACE);
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-1", entityCreator.getTubeType(), sample);
        Work work = entityCreator.createWork(null, null, null, null, null);

        String opMutation = tester.readGraphQL("recordprobeoperation.graphql").replace("SGP1", work.getWorkNumber());
        result = tester.post(opMutation);

        assertEquals("STAN-1", chainGet(result, "data", "recordProbeOperation", "labware", 0, "barcode"));
        Integer opId = chainGet(result, "data", "recordProbeOperation", "operations", 0, "id");
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        List<LabwareProbe> lwProbes = lwProbeRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(lwProbes).hasSize(3);
        for (LabwareProbe lwp : lwProbes) {
            assertEquals(opId, lwp.getOperationId());
            assertEquals(lw.getId(), lwp.getLabwareId());
        }
        LabwareProbe lwp = lwProbes.getFirst();
        assertEquals("probe1", lwp.getProbePanel().getName());
        assertEquals(1, lwp.getPlex());
        assertEquals("LOT1", lwp.getLotNumber());
        assertEquals(SlideCosting.Faculty, lwp.getCosting());
        lwp = lwProbes.get(1);
        assertEquals("probe2", lwp.getProbePanel().getName());
        assertNull(lwp.getPlex());
        assertEquals("LOT2", lwp.getLotNumber());
        assertEquals(SlideCosting.Warranty_replacement, lwp.getCosting());
        lwp = lwProbes.get(2);
        assertEquals("william", lwp.getProbePanel().getName());
        assertNull(lwp.getPlex());
        assertNull(lwp.getLotNumber());
        assertNull(lwp.getCosting());
        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(notes).hasSize(2);
        notes.forEach(note -> assertEquals(lw.getId(), note.getLabwareId()));
        UCMap<String> noteValues = notes.stream()
                .collect(UCMap.toUCMap(LabwareNote::getName, LabwareNote::getValue));
        assertEquals("Faculty", noteValues.get("kit costing"));
        assertEquals("123456", noteValues.get("reagent lot"));
        testCompletion(lw, work, sample);
        testAnalyser(lw, work, sample);
        testSampleMetrics(lw, work);
    }

    private void testCompletion(Labware lw, Work work, Sample sample) throws Exception {
        OperationType opType = entityCreator.createOpType(CompletionServiceImp.PROBE_QC_NAME, null, OperationTypeFlag.IN_PLACE);
        String mutation = tester.readGraphQL("recordcompletion.graphql")
                .replace("SGP1", work.getWorkNumber())
                .replace("555", String.valueOf(sample.getId()));
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordCompletion", "operations", 0, "id");
        assertNotNull(opId);
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opcoms).hasSize(1);
        OperationComment opcom = opcoms.getFirst();
        assertEquals(lw.getFirstSlot().getId(), opcom.getSlotId());
        assertEquals(sample.getId(), opcom.getSampleId());
        assertEquals(1, opcom.getComment().getId());
    }

    private void testAnalyser(Labware lw, Work work, Sample sample) throws Exception {
        entityCreator.createOpType(AnalyserServiceImp.ANALYSER_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        Equipment equipment =  equipmentRepo.save(new Equipment("Xenium 1", AnalyserServiceImp.EQUIPMENT_CATEGORY));
        String mutation = tester.readGraphQL("recordanalyser.graphql")
                .replace("SGP1", work.getWorkNumber())
                .replace("555", String.valueOf(sample.getId()))
                .replace("999", String.valueOf(equipment.getId()));
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordAnalyser", "operations", 0, "id");
        assertNotNull(opId);

        List<Roi> rois = roiRepo.findAllByOperationIdIn(List.of(opId));

        assertThat(rois).containsExactly(new Roi(lw.getFirstSlot().getId(), sample.getId(), opId, "roi1"));
        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        Map<String, String> noteValues = new HashMap<>(5);
        notes.forEach(note -> {
            assertEquals(lw.getId(), note.getLabwareId());
            assertEquals(opId, note.getOperationId());
            noteValues.put(note.getName(), note.getValue());
        });
        assertThat(noteValues).hasSize(5);
        assertEquals("RUN1", noteValues.get("run"));
        assertEquals("LOT1", noteValues.get("decoding reagent A lot"));
        assertEquals("LOT2", noteValues.get("decoding reagent B lot"));
        assertEquals("left", noteValues.get("cassette position"));
        assertEquals("123456", noteValues.get("decoding consumables lot"));

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(op.getEquipment(), equipment);

        String runNamesQuery = String.format("query { runNames(barcode: \"%s\") }", lw.getBarcode());
        Object queryResult = tester.post(runNamesQuery);
        assertThat(chainGetList(queryResult, "data", "runNames")).containsExactly("RUN1");
    }

    private void testSampleMetrics(Labware lw, Work work) throws Exception {
        entityCreator.createOpType("Xenium metrics", null, OperationTypeFlag.IN_PLACE);
        String mutation = tester.readGraphQL("recordsamplemetrics.graphql")
                .replace("BC", lw.getBarcode())
                .replace("WORK", work.getWorkNumber());
        Object result = tester.post(mutation);
        assertEquals(lw.getBarcode(), chainGet(result, "data", "recordSampleMetrics", "labware", 0, "barcode"));
        Integer opId = chainGet(result, "data", "recordSampleMetrics", "operations", 0, "id");
        assertNotNull(opId);
        List<RoiMetric> roiMetrics = stream(roiMetricRepo.findAll())
                .filter(rm -> rm.getOperationId().equals(opId))
                .toList();
        assertThat(roiMetrics).hasSize(2);
        if (roiMetrics.getFirst().getName().equalsIgnoreCase("N2")) {
            roiMetrics = roiMetrics.reversed();
        }
        for (int i = 0; i < roiMetrics.size(); i++) {
            int j = i+1;
            RoiMetric rm = roiMetrics.get(i);
            assertNotNull(rm.getId());
            assertEquals(opId, rm.getOperationId());
            assertEquals(lw.getId(), rm.getLabwareId());
            assertEquals("roi1", rm.getRoi());
            assertEquals("n"+j, rm.getName());
            assertEquals("v"+j, rm.getValue());
        }

        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(notes).hasSize(1);
        LabwareNote note = notes.getFirst();
        assertEquals(lw.getId(), note.getLabwareId());
        assertEquals(opId, note.getOperationId());
        assertEquals("run", note.getName());
        assertEquals("RUN1", note.getValue());
    }
}
