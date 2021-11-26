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

    @Transactional
    @Test
    public void testStainAndWorkProgressAndRecordResult() throws Exception {
        entityCreator.createOpType("Record result", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        Work work = entityCreator.createWork(null, null, null);
        User user = entityCreator.createUser("user1");
        Sample sam = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), 5);
        LabwareType lt = entityCreator.createLabwareType("lt1", 1, 1);
        Labware lw = entityCreator.createLabware("STAN-50", lt, sam);

        tester.setUser(user);
        Object data = tester.post(tester.readGraphQL("stain.graphql").replace("SGP500", work.getWorkNumber()));
        Object stainData = chainGet(data, "data", "stain");
        assertThat(chainGetList(stainData, "operations")).hasSize(1);
        Map<String, ?> opData = chainGet(stainData, "operations", 0);
        Integer opId = (Integer) opData.get("id");
        assertNotNull(opId);
        assertEquals("Stain", chainGet(opData, "operationType", "name"));

        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(op.getStainType().getName(), "H&E");

        data = tester.post(tester.readGraphQL("workprogress.graphql").replace("SGP500", work.getWorkNumber()));
        Object progressData = chainGet(data, "data", "workProgress", 0);
        assertEquals(work.getWorkNumber(), chainGet(progressData, "work", "workNumber"));
        assertEquals(work.getWorkType().getName(), chainGet(progressData, "work", "workType", "name"));

        List<Map<String,?>> timeEntries = chainGetList(progressData, "timestamps");
        assertThat(timeEntries).hasSize(1);
        var timeEntry = timeEntries.get(0);
        assertEquals("Stain", timeEntry.get("type"));
        Assertions.assertEquals(GraphQLCustomTypes.TIMESTAMP.getCoercing().serialize(op.getPerformed()), timeEntry.get("timestamp"));
        String resultGraphql = tester.readGraphQL("stainresult.graphql")
                .replace("SGP500", work.getWorkNumber());
        data = tester.post(resultGraphql);

        opData = chainGet(data, "data", "recordStainResult", "operations", 0);
        Integer resultOpId = (Integer) opData.get("id");
        assertEquals("Record result", chainGet(opData, "operationType", "name"));
        assertNotNull(resultOpId);
        List<ResultOp> results = resultOpRepo.findAllByOperationIdIn(List.of(resultOpId));
        assertThat(results).hasSize(1);
        ResultOp result = results.get(0);
        assertEquals(PassFail.pass, result.getResult());
        assertEquals(resultOpId, result.getOperationId());
        assertEquals(sam.getId(), result.getSampleId());
        assertEquals(opId, result.getRefersToOpId());
        assertEquals(lw.getFirstSlot().getId(), result.getSlotId());

        Integer permOpId = testPerm();
        testVisiumAnalysis();
        Integer qcOpId = testVisiumQC(permOpId);
        testQueryVisiumQCResult(qcOpId);
    }

    // called by testStainAndWorkProgressAndRecordResult
    private Integer testPerm() throws Exception {
        entityCreator.createOpType("Visium permabilisation", null, OperationTypeFlag.IN_PLACE);
        Object data = tester.post(tester.readGraphQL("perm.graphql"));
        Integer opId = chainGet(data, "data", "recordPerm", "operations", 0, "id");
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(measurements).hasSize(1);
        Measurement meas = measurements.get(0);
        assertEquals("permabilisation time", meas.getName());
        assertEquals("120", meas.getValue());
        return opId;
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
        assertThat(permDatas).hasSize(1);
        Map<String, ?> permData = permDatas.get(0);
        assertEquals("A1", permData.get("address"));
        assertEquals(120, permData.get("seconds"));
        assertNull(permData.get("controlType"));
        assertEquals(true, permData.get("selected"));
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
        assertNull(spf.get("comment"));
    }}
