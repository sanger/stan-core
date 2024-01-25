package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests recording a stain and a bunch of following operations
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestStainAndSubsequentMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ResultOpRepo resultOpRepo;
    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private TissueRepo tissueRepo;
    @Autowired
    private OperationCommentRepo opCommentRepo;
    @Autowired
    private StainTypeRepo stainTypeRepo;
    @Autowired
    private LabwareNoteRepo lwNoteRepo;

    @Transactional
    @Test
    public void testStainAndWorkProgressAndRecordResult() throws Exception {
        entityCreator.createOpType("Stain QC", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        entityCreator.createOpType("Tissue coverage", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        entityCreator.createOpType("Pretreatment QC", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        WorkType wt = entityCreator.createWorkType("Rocks");
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("4");
        ReleaseRecipient wr = entityCreator.createReleaseRecipient("test1");
        Work work = entityCreator.createWork(wt, pr, null, cc, wr);
        work.setWorkNumber("SGP500");
        User user = entityCreator.createUser("user1");
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 5);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1, 2);
        Labware lw = entityCreator.createLabware("STAN-50", lt, sam);

        tester.setUser(user);
        Object data = tester.post(tester.readGraphQL("stain.graphql"));
        Object stainData = chainGet(data, "data", "stain");
        assertThat(chainGetList(stainData, "operations")).hasSize(1);
        Map<String, ?> opData = chainGet(stainData, "operations", 0);
        Integer opId = (Integer) opData.get("id");
        assertNotNull(opId);
        assertEquals("Stain", chainGet(opData, "operationType", "name"));

        Operation op = opRepo.findById(opId).orElseThrow();
        final List<StainType> stainTypes = stainTypeRepo.loadOperationStainTypes(List.of(opId)).get(opId);
        assertThat(stainTypes).hasSize(1);
        assertEquals("H&E", stainTypes.get(0).getName());

        data = tester.post(tester.readGraphQL("workprogress.graphql"));
        Object progressData = chainGet(data, "data", "workProgress", 0);
        assertEquals(work.getWorkNumber(), chainGet(progressData, "work", "workNumber"));
        assertEquals(work.getWorkType().getName(), chainGet(progressData, "work", "workType", "name"));

        List<Map<String,?>> timeEntries = chainGetList(progressData, "timestamps");
        assertThat(timeEntries).hasSize(1);
        var timeEntry = timeEntries.get(0);
        assertEquals("Stain", timeEntry.get("type"));
        Assertions.assertEquals(GraphQLCustomTypes.DATE_TIME_FORMAT.format(op.getPerformed()), timeEntry.get("timestamp"));

        String pretreatmentQcGraphql = tester.readGraphQL("pretreatmentqc.graphql")
                .replace("SGP500", work.getWorkNumber())
                .replace("999", sam.getId().toString());
        data = tester.post(pretreatmentQcGraphql);

        opData = chainGet(data, "data", "recordStainResult", "operations", 0);
        Integer pretreatmentOpId = (Integer) opData.get("id");
        assertEquals("Pretreatment QC", chainGet(opData, "operationType", "name"));
        assertNotNull(pretreatmentOpId);
        List<ResultOp> pretreatmentOpResults = resultOpRepo.findAllByOperationIdIn(List.of(pretreatmentOpId));
        assertThat(pretreatmentOpResults).hasSize(0);
        var operationComments = opCommentRepo.findAllByOperationIdIn(List.of(pretreatmentOpId));
        assertThat(operationComments).hasSize(1);
        OperationComment opComment = operationComments.get(0);
        assertEquals(opComment.getComment().getId(), 2);
        assertEquals(opComment.getSlotId(), lw.getFirstSlot().getId());
        assertEquals(opComment.getSampleId(), sam.getId());

        String resultGraphql = tester.readGraphQL("stainqc.graphql")
                .replace("SGP500", work.getWorkNumber())
                .replace("999", sam.getId().toString());
        data = tester.post(resultGraphql);

        opData = chainGet(data, "data", "recordStainResult", "operations", 0);
        Integer resultOpId = (Integer) opData.get("id");
        assertEquals("Stain QC", chainGet(opData, "operationType", "name"));
        assertNotNull(resultOpId);
        List<ResultOp> results = resultOpRepo.findAllByOperationIdIn(List.of(resultOpId));
        assertThat(results).hasSize(1);
        ResultOp result = results.get(0);
        var opComs = opCommentRepo.findAllByOperationIdIn(List.of(resultOpId));
        assertThat(opComs).hasSize(2);
        OperationComment opCom = opComs.get(0);
        assertEquals(opCom.getComment().getId(), 1);
        assertEquals(opCom.getSlotId(), lw.getFirstSlot().getId());
        opCom = opComs.get(1);
        assertEquals(opCom.getComment().getId(), 2);
        assertEquals(opCom.getSampleId(), sam.getId());
        assertEquals(opCom.getSlotId(), lw.getFirstSlot().getId());
        assertEquals(PassFail.pass, result.getResult());
        assertEquals(resultOpId, result.getOperationId());
        assertEquals(sam.getId(), result.getSampleId());
        assertEquals(opId, result.getRefersToOpId());
        assertEquals(lw.getFirstSlot().getId(), result.getSlotId());

        String coverageMutation = tester.readGraphQL("tissuecoverage.graphql")
                .replace("SGP500", work.getWorkNumber())
                .replace("999", sam.getId().toString());
        data = tester.post(coverageMutation);
        opData = chainGet(data, "data", "recordStainResult", "operations", 0);
        Integer coverageOpId = (Integer) opData.get("id");

        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(coverageOpId));
        assertThat(measurements).hasSize(1);
        Measurement measurement = measurements.get(0);
        assertEquals(lw.getFirstSlot().getId(), measurement.getSlotId());
        assertEquals("Tissue coverage", measurement.getName());
        assertEquals("50", measurement.getValue());

        Integer permOpId = testPerm(lw);
        testVisiumAnalysis();
        Integer qcOpId = testVisiumQC(permOpId);
        testQueryVisiumQCResult(qcOpId);
    }

    // called by testStainAndWorkProgressAndRecordResult
    private Integer testPerm(Labware lw) throws Exception {
        var addControlOpType = entityCreator.createOpType("Add control", null);
        Tissue tissue = entityCreator.getAny(tissueRepo);
        Sample controlSample = entityCreator.createSample(tissue, 50);
        Labware controlLabware = entityCreator.createLabware("STAN-C0", entityCreator.getTubeType(), controlSample);

        entityCreator.createOpType("Visium permeabilisation", null, OperationTypeFlag.IN_PLACE);
        Object data = tester.post(tester.readGraphQL("perm.graphql"));
        List<?> ops = chainGet(data, "data", "recordPerm", "operations");
        assertThat(ops).hasSize(2);

        Integer addControlId = chainGet(ops, 0, "id");
        Integer permId = chainGet(ops, 1, "id");

        Operation controlOp = opRepo.findById(addControlId).orElseThrow();
        assertEquals(addControlOpType, controlOp.getOperationType());
        assertThat(controlOp.getActions()).hasSize(1);
        Action controlAction = controlOp.getActions().get(0);
        assertEquals(controlLabware.getFirstSlot(), controlAction.getSource());
        assertEquals(new Address(1,2), controlAction.getDestination().getAddress());
        assertEquals(controlSample, controlAction.getSample());

        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(permId));
        assertThat(measurements).hasSize(2);
        Measurement timeMeasurement = measurements.stream()
                .filter(m -> m.getName().equalsIgnoreCase("permeabilisation time"))
                .findAny().orElseThrow();
        Measurement controlMeasurement = measurements.stream()
                .filter(m -> m.getName().equalsIgnoreCase("control"))
                .findAny().orElseThrow();
        assertEquals("120", timeMeasurement.getValue());
        assertEquals(lw.getFirstSlot().getId(), timeMeasurement.getSlotId());
        assertEquals("positive", controlMeasurement.getValue());
        assertEquals(lw.getSlots().get(1).getId(), controlMeasurement.getSlotId());
        return permId;
    }

    // called by testStainAndWorkProgressAndRecordResult
    private void testVisiumAnalysis() throws Exception {
        entityCreator.createOpType("Visium analysis", null, OperationTypeFlag.IN_PLACE);
        Object data = tester.post(tester.readGraphQL("visium_analysis.graphql"));
        final Map<String, ?> opData = chainGet(data, "data", "visiumAnalysis", "operations", 0);
        assertEquals("Visium analysis", chainGet(opData, "operationType", "name"));
        Integer opId = (Integer) opData.get("id");
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(measurements).hasSize(1);
        Measurement meas = measurements.get(0);
        assertEquals("selected time", meas.getName());
        assertEquals("120", meas.getValue());

        data = tester.post("query { visiumPermData(barcode: \"STAN-50\") { labware { barcode } " +
                "addressPermData { address, seconds, controlType, selected } }}");
        assertEquals("STAN-50", chainGet(data, "data", "visiumPermData", "labware", "barcode"));
        List<Map<String, ?>> permDatas = chainGet(data, "data", "visiumPermData", "addressPermData");
        assertThat(permDatas).hasSize(2);
        Map<String, ?> permData = permDatas.stream()
                .filter(m -> m.get("address").equals("A1"))
                .findAny()
                .orElseThrow();
        assertEquals("A1", permData.get("address"));
        assertEquals(120, permData.get("seconds"));
        assertNull(permData.get("controlType"));
        assertEquals(true, permData.get("selected"));

        Map<String, ?> controlData = permDatas.stream()
                .filter(m -> m.get("address").equals("A2"))
                .findAny()
                .orElseThrow();
        assertEquals(false, controlData.get("selected"));
    }

    // called by testStainAndWorkProgressAndRecordResult
    private Integer testVisiumQC(Integer permOpId) throws Exception {
        final String resultOpName = "Slide processing";
        entityCreator.createOpType(resultOpName, null, OperationTypeFlag.RESULT, OperationTypeFlag.IN_PLACE);
        Object data = tester.post(tester.readGraphQL("visiumqc.graphql"));
        Integer opId = chainGet(data, "data", "recordVisiumQC", "operations", 0, "id");
        assertEquals(resultOpName, chainGet(data, "data", "recordVisiumQC", "operations", 0, "operationType", "name"));
        var resultOps = resultOpRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(resultOps).hasSize(1);
        ResultOp ro = resultOps.get(0);
        assertEquals(PassFail.pass, ro.getResult());
        assertEquals(permOpId, ro.getRefersToOpId());
        var opComs = opCommentRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opComs).hasSize(1);
        OperationComment opCom = opComs.get(0);
        assertEquals(opCom.getComment().getId(), 1);
        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(notes).hasSize(2);
        Map<String, String> noteMap = notes.stream()
                        .collect(toMap(LabwareNote::getName, LabwareNote::getValue));
        assertEquals("Faculty", noteMap.get("costing"));
        assertEquals("234567", noteMap.get("reagent lot"));

        return opId;
    }

    // called by testStainAndWorkProgressAndRecordResult
    private void testQueryVisiumQCResult(Integer qcOpId) throws Exception {
        Object data = tester.post(tester.readGraphQL("passfails.graphql"));

        List<Map<String,?>> opfs = chainGet(data, "data", "passFails");
        assertThat(opfs).hasSize(1);
        Map<String, ?> opf = opfs.get(0);
        assertEquals(qcOpId, chainGet(opf, "operation", "id"));
        //noinspection unchecked
        List<Map<String,?>> spfs = (List<Map<String, ?>>) opf.get("slotPassFails");
        assertThat(spfs).hasSize(1);
        Map<String, ?> spf = spfs.get(0);
        assertEquals("A1", spf.get("address"));
        assertEquals("pass", spf.get("result"));
        Comment comment = opCommentRepo.findAllByOperationIdIn(List.of(qcOpId)).get(0).getComment();
        assertEquals(comment.getText(), spf.get("comment"));
    }
}
