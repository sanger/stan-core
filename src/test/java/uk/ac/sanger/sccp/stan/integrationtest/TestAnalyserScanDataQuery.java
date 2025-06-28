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

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestAnalyserScanDataQuery {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    WorkRepo workRepo;
    @Autowired
    ProbePanelRepo probeRepo;
    @Autowired
    LabwareProbeRepo lwProbeRepo;

    @Test
    @Transactional
    void testAnalyserScanData() throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-1", lt, sample);
        OperationType cellSeg = entityCreator.createOpType("Cell segmentation", null, OperationTypeFlag.IN_PLACE);
        Operation op = entityCreator.simpleOp(cellSeg, entityCreator.createUser("user1"), lw, lw);
        Work work = entityCreator.createWork(null, null, null, null, null);
        work.getSampleSlotIds().add(new Work.SampleSlotId(sample.getId(), lw.getFirstSlot().getId()));
        workRepo.save(work);
        ProbePanel probe = probeRepo.save(new ProbePanel(ProbePanel.ProbeType.xenium, "Alpha"));
        lwProbeRepo.save(new LabwareProbe(null, probe, op.getId(), lw.getId(), "lot1", 1, SlideCosting.SGP));

        String query = tester.readGraphQL("analyserscandata.graphql");
        Object response = tester.post(query);
        Map<String, ?> data = chainGet(response, "data", "analyserScanData");
        assertEquals(lw.getBarcode(), data.get("barcode"));
        assertEquals(List.of(probe.getName()), data.get("probes"));
        assertEquals(List.of(work.getWorkNumber()), data.get("workNumbers"));
        assertTrue((Boolean) data.get("cellSegmentationRecorded"));
    }
}
