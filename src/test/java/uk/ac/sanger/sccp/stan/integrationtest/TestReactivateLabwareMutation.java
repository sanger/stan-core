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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * Tests recordProbeOperation mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestReactivateLabwareMutation {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    EntityManager entityManager;
    @Autowired
    LabwareRepo lwRepo;
    @Autowired
    CommentRepo commentRepo;
    @Autowired
    OperationRepo opRepo;
    @Autowired
    OperationCommentRepo opComRepo;
    @Autowired
    WorkRepo workRepo;

    @Test
    @Transactional
    public void testReactivateLabware() throws Exception {
        // Setup
        User user = entityCreator.createUser("user1");
        OperationType opType = entityCreator.createOpType("Reactivate", null, OperationTypeFlag.IN_PLACE);
        Work work = entityCreator.createWork(null, null, null, null, null);
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware disLw = entityCreator.createLabware("STAN-DIS", lt, sample);
        Labware desLw = entityCreator.createLabware("STAN-DES", lt, sample);
        disLw.setDiscarded(true);
        desLw.setDestroyed(true);
        final List<Labware> lws = List.of(disLw, desLw);
        lwRepo.saveAll(lws);

        // Mutation
        String mutation = tester.readGraphQL("reactivatelabware.graphql")
                .replace("[WORK]", work.getWorkNumber());
        tester.setUser(user);
        Object resultData = chainGet(tester.post(mutation), "data", "reactivateLabware");

        // Checking response
        List<Map<String, ?>> lwData = chainGet(resultData, "labware");
        List<Map<String, ?>> opData = chainGet(resultData, "operations");
        assertThat(lwData).hasSameSizeAs(lws);
        for (int i = 0; i < lwData.size(); i++) {
            var lwd = lwData.get(i);
            assertEquals(lws.get(i).getBarcode(), lwd.get("barcode"));
            assertEquals("active", lwd.get("state"));
        }
        assertThat(opData).hasSameSizeAs(lws);
        List<Integer> opIds = opData.stream()
                .map(opd -> (Integer) opd.get("id"))
                .collect(toList());

        // Check ops
        Iterable<Operation> ops = opRepo.findAllById(opIds);
        assertThat(stream(ops).map(Operation::getId)).containsExactlyInAnyOrderElementsOf(opIds);
        assertThat(ops).allMatch(op -> op.getOperationType().equals(opType));

        Map<Integer, Set<String>> opWorks = workRepo.findWorkNumbersForOpIds(opIds);
        assertThat(opWorks.keySet()).containsExactlyInAnyOrderElementsOf(opIds);
        assertThat(opIds).allSatisfy(id -> assertThat(opWorks.get(id)).containsExactly(work.getWorkNumber()));

        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(opIds);
        assertThat(opcoms).hasSameSizeAs(lws);
        for (int i = 0; i < lws.size(); ++i) {
            final Integer opId = opIds.get(i);
            final Integer commentId = i+1;
            final Integer slotId = lws.get(i).getFirstSlot().getId();
            OperationComment opcom = opcoms.stream()
                    .filter(oc -> oc.getOperationId().equals(opId))
                    .findAny().orElse(null);
            assertNotNull(opcom);
            assertEquals(commentId, opcom.getComment().getId());
            assertEquals(slotId, opcom.getSlotId());
            assertEquals(sample.getId(), opcom.getSampleId());
        }

        // Check labware is now active
        for (Labware lw : lws) {
            entityManager.refresh(lw);
            assertEquals(Labware.State.active, lw.getState());
        }
    }
}
