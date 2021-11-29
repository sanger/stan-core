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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the complex stain mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestComplexStainMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private StainTypeRepo stainTypeRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private LabwareNoteRepo lwNoteRepo;

    @Test
    @Transactional
    public void testComplexStain() throws Exception {
        Sample sample = entityCreator.createSample(null, 3);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.getTubeType(), sample);
        StainType stainType = stainTypeRepo.save(new StainType(null, "RNAscope"));
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Object result = tester.post(tester.readGraphQL("complexstain.graphql"));
        List<?> opData = chainGet(result, "data", "recordComplexStain", "operations");
        assertThat(opData).hasSize(1);
        Integer opId = chainGet(opData, 0, "id");
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals("Stain", op.getOperationType().getName());
        assertEquals(stainType, op.getStainType());
        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(notes).hasSize(4);
        Map<String, String> noteData = new HashMap<>(4);
        for (LabwareNote note : notes) {
            assertEquals(opId, note.getOperationId());
            assertEquals(lw.getId(), note.getLabwareId());
            noteData.put(note.getName(), note.getValue());
        }
        assertEquals(Map.of("Bond barcode", "1234 ABC", "Bond run", "8",
                        "Panel", "positive", "Plex", "6"),
                noteData);
    }
}
