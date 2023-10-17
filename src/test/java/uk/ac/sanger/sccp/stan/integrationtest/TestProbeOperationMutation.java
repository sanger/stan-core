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

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

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

        OperationType opType = entityCreator.createOpType(CompletionServiceImp.PROBE_HYBRIDISATION_NAME, null, OperationTypeFlag.PROBES, OperationTypeFlag.IN_PLACE);
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
        assertThat(lwProbes).hasSize(2);
        if (lwProbes.get(0).getProbePanel().getName().equals("probe2")) {
            lwProbes = IntStream.of(lwProbes.size()-1,-1,-1).mapToObj(lwProbes::get).collect(toList());
        }
        for (LabwareProbe lwp : lwProbes) {
            assertEquals(opId, lwp.getOperationId());
            assertEquals(lw.getId(), lwp.getLabwareId());
        }
        LabwareProbe lwp = lwProbes.get(0);
        assertEquals("probe1", lwp.getProbePanel().getName());
        assertEquals(1, lwp.getPlex());
        assertEquals("LOT1", lwp.getLotNumber());
        assertEquals(SlideCosting.Faculty, lwp.getCosting());
        lwp = lwProbes.get(1);
        assertEquals("probe2", lwp.getProbePanel().getName());
        assertEquals(2, lwp.getPlex());
        assertEquals("LOT2", lwp.getLotNumber());
        assertEquals(SlideCosting.SGP, lwp.getCosting());

        testCompletion(lw, work, sample);
        testAnalyser(lw, work, sample);
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
        OperationComment opcom = opcoms.get(0);
        assertEquals(lw.getFirstSlot().getId(), opcom.getSlotId());
        assertEquals(sample.getId(), opcom.getSampleId());
        assertEquals(1, opcom.getComment().getId());
    }

    private void testAnalyser(Labware lw, Work work, Sample sample) throws Exception {
        entityCreator.createOpType(AnalyserServiceImp.ANALYSER_OP_NAME, null, OperationTypeFlag.IN_PLACE);
        String mutation = tester.readGraphQL("recordanalyser.graphql")
                .replace("SGP1", work.getWorkNumber())
                .replace("555", String.valueOf(sample.getId()));
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordAnalyser", "operations", 0, "id");
        assertNotNull(opId);

        List<Roi> rois = roiRepo.findAllByOperationIdIn(List.of(opId));

        assertThat(rois).containsExactly(new Roi(lw.getFirstSlot().getId(), sample.getId(), opId, "roi1"));
        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        Map<String, String> noteValues = new HashMap<>(3);
        notes.forEach(note -> {
            assertEquals(lw.getId(), note.getLabwareId());
            assertEquals(opId, note.getOperationId());
            noteValues.put(note.getName(), note.getValue());
        });
        assertThat(noteValues).hasSize(4);
        assertEquals("RUN1", noteValues.get("run"));
        assertEquals("LOT1", noteValues.get("decoding reagent A lot"));
        assertEquals("LOT2", noteValues.get("decoding reagent B lot"));
        assertEquals("left", noteValues.get("cassette position"));
    }
}
