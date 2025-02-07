package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.OperationType;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecord;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecordNote;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link SlotCopyRecordRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
class TestSlotCopyRecordRepo {
    @Autowired
    private SlotCopyRecordRepo repo;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    private OperationType opType;
    private Work work;

    @BeforeEach
    void setup() {
        opType = entityCreator.createOpType("opname", null);
        work = entityCreator.createWork(null, null, null, null, null);
    }

    @Test
    @Transactional
    void testSlotCopyRecord_minimal() {
        SlotCopyRecord record = new SlotCopyRecord(opType, work, "LP1");
        record = repo.save(record);
        assertNotNull(record);
        assertNotNull(record.getId());
        SlotCopyRecord loaded = repo.findByOperationTypeAndWorkAndLpNumber(opType, work, record.getLpNumber()).orElseThrow();
        assertNotNull(loaded);
        assertEquals(opType, loaded.getOperationType());
        assertEquals(work, loaded.getWork());
        assertEquals(loaded.getId(), record.getId());
        assertThat(loaded.getNotes()).isEmpty();
    }

    @Transactional
    @Test
    void testSlotCopyRecord_maximal() {
        SlotCopyRecord record = new SlotCopyRecord(opType, work, "LP1");
        record.setNotes(List.of(new SlotCopyRecordNote("Alpha", "Alabama"),
                new SlotCopyRecordNote("Beta", 3, "Banana")));

        record = repo.save(record);
        SlotCopyRecord loaded = repo.findByOperationTypeAndWorkAndLpNumber(opType, work, record.getLpNumber()).orElseThrow();
        entityManager.refresh(loaded);
        assertNotNull(loaded);
        assertEquals(opType, loaded.getOperationType());
        assertEquals(work, loaded.getWork());
        assertEquals("LP1", loaded.getLpNumber());

        assertThat(loaded.getNotes()).hasSize(2);
        List<SlotCopyRecordNote> notes = loaded.getNotes().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (int i = 0; i < notes.size(); i++) {
            SlotCopyRecordNote note = notes.get(i);
            assertEquals(i==0 ? "Alpha" : "Beta", note.getName());
            assertEquals(i==0 ? "Alabama" : "Banana", note.getValue());
            assertEquals(i==0 ? 0 : 3, note.getValueIndex());
        }
    }

    @Transactional
    @Test
    void testUpdate() {
        SlotCopyRecord record = new SlotCopyRecord(opType, work, "LP1");
        record.setNotes(List.of(new SlotCopyRecordNote("Alpha", 0, "Alpha0"),
                new SlotCopyRecordNote("Alpha", 1, "Alpha1"),
                new SlotCopyRecordNote("Beta", "Banana")));
        record = repo.save(record);

        entityManager.flush();

        Integer id = record.getId();

        record = repo.findById(id).orElseThrow();
        record.setNotes(List.of(new SlotCopyRecordNote("Alpha", 0, "Alabama")));

        repo.save(record);

        entityManager.flush();
        entityManager.refresh(record);
        assertThat(record.getNotes()).hasSize(1);
        SlotCopyRecordNote note = record.getNotes().iterator().next();
        assertNotNull(note.getId());
        assertEquals("Alpha", note.getName());
        assertEquals(0, note.getValueIndex());
        assertEquals("Alabama", note.getValue());
    }
}