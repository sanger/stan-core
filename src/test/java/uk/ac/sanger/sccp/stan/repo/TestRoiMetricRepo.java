package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
class TestRoiMetricRepo {
    @Autowired
    private RoiMetricRepo roiMetricRepo;
    @Autowired
    private RoiRepo roiRepo;
    @Autowired
    private OperationRepo opRepo;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    void testDeprecateMetrics() {
        Integer sampleId = entityCreator.createSample(null, null).getId();

        Labware[] labwares = IntStream.rangeClosed(1,2)
                .mapToObj(i -> entityCreator.createTube("STAN-"+i))
                .toArray(Labware[]::new);
        Integer[] slotIds = Arrays.stream(labwares)
                .map(lw -> lw.getFirstSlot().getId())
                .toArray(Integer[]::new);
        Integer[] lwIds = Arrays.stream(labwares)
                .map(Labware::getId)
                .toArray(Integer[]::new);

        OperationType opType = entityCreator.createOpType("metrics", null, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");
        Integer opId = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        roiRepo.saveAll(List.of(
                new Roi(slotIds[0], sampleId, opId, "Alpha"),
                new Roi(slotIds[1], sampleId, opId, "Beta")
        ));
        LocalDateTime time0 = LocalDateTime.now().withNano(0);

        roiMetricRepo.deprecateMetrics(lwIds[0], List.of("Alpha", "Beta"), time0);

        // Create metrics with different labware and rois
        List<RoiMetric> metrics1 = asList(roiMetricRepo.saveAll(List.of(
                new RoiMetric(lwIds[0], opId, "Alpha", "NAME0", "VAL0"),
                new RoiMetric(lwIds[0], opId, "Alpha", "NAME1", "VAL1"),
                new RoiMetric(lwIds[0], opId, "Beta", "NAME0", "VAL2"),
                new RoiMetric(lwIds[0], opId, "Beta", "NAME2", "VAL3")
        )));
        List<RoiMetric> metrics2 = asList(roiMetricRepo.saveAll(List.of(
                new RoiMetric(lwIds[1], opId, "Alpha", "NAME0", "VAL4"),
                new RoiMetric(lwIds[1], opId, "Alpha", "NAME1", "VAL5"),
                new RoiMetric(lwIds[1], opId, "Beta", "NAME0", "VAL6"),
                new RoiMetric(lwIds[1], opId, "Beta", "NAME2", "VAL7")
        )));
        metrics1.forEach(m -> assertNull(m.getDeprecated()));
        metrics2.forEach(m -> assertNull(m.getDeprecated()));

        // Deprecate some of them

        roiMetricRepo.deprecateMetrics(lwIds[0], List.of("alpha", "gamma"), time0);

        // Check if the appropriate ones are deprecated
        for (RoiMetric rm : metrics1) {
            entityManager.refresh(rm);
            if (rm.getRoi().equalsIgnoreCase("alpha")) {
                assertEquals(time0, rm.getDeprecated());
            } else {
                assertNull(rm.getDeprecated());
            }
        }
        for (RoiMetric rm : metrics2) {
            entityManager.refresh(rm);
            assertNull(rm.getDeprecated());
        }

        // Deprecate some others

        roiMetricRepo.deprecateMetrics(lwIds[1], List.of("BETA", "GAMMA"), time0);

        // Check if the appropriate ones are deprecated

        for (RoiMetric rm : metrics1) {
            entityManager.refresh(rm);
            if (rm.getRoi().equalsIgnoreCase("alpha")) {
                assertEquals(time0, rm.getDeprecated());
            } else {
                assertNull(rm.getDeprecated());
            }
        }
        for (RoiMetric rm : metrics2) {
            entityManager.refresh(rm);
            if (rm.getRoi().equalsIgnoreCase("beta")) {
                assertEquals(time0, rm.getDeprecated());
            } else {
                assertNull(rm.getDeprecated());
            }
        }

        LocalDateTime time1 = LocalDateTime.now().withNano(0).plusHours(1);

        // Deprecate the old ones
        roiMetricRepo.deprecateMetrics(lwIds[0], List.of("ALPHA", "BETA"), time1);
        // Create some new ones
        List<RoiMetric> metrics3 = asList(roiMetricRepo.saveAll(List.of(
                new RoiMetric(lwIds[0], opId, "Alpha", "NAME0", "VAL8"),
                new RoiMetric(lwIds[0], opId, "Alpha", "NAME1", "VAL9"),
                new RoiMetric(lwIds[0], opId, "Beta", "NAME0", "VALA"),
                new RoiMetric(lwIds[0], opId, "Beta", "NAME1", "VALB")
        )));

        // Check that newly deprecated ones have the new timestamp, and previously deprecated ones
        //  have the old timestamp
        for (RoiMetric rm : metrics1) {
            entityManager.refresh(rm);
            assertEquals(rm.getRoi().equalsIgnoreCase("alpha") ? time0 : time1, rm.getDeprecated());
        }
        for (RoiMetric rm : metrics2) {
            entityManager.refresh(rm);
            if (rm.getRoi().equalsIgnoreCase("beta")) {
                assertEquals(time0, rm.getDeprecated());
            } else {
                assertNull(rm.getDeprecated());
            }
        }
        for (RoiMetric rm : metrics3) {
            entityManager.refresh(rm);
            assertNull(rm.getDeprecated());
        }
    }
}