package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.WorkEvent.Type;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link WorkEventRepo}
 * @author dr6
 * @see WorkEvent
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestWorkEventRepo {
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private WorkEventRepo workEventRepo;
    @Autowired
    private CommentRepo commentRepo;

    private User user;

    @Transactional
    @Test
    public void testGetLatestEventForEachWorkId() {
        user = entityCreator.createUser("user1");
        WorkType wt = entityCreator.createWorkType("Rocks");
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("4");
        ReleaseRecipient wr = entityCreator.createReleaseRecipient("test1");
        Work work1 = entityCreator.createWork(wt, pr, cc, wr);
        Work work2 = entityCreator.createWork(wt, pr, cc, wr);
        Comment com1 = commentRepo.save(new Comment(null, "Alpha", "work"));
        Comment com2 = commentRepo.save(new Comment(null, "Beta", "work"));

        createEvent(work1, Type.create, null, 1);
        WorkEvent event2 = createEvent(work1, Type.pause, com1, 2);
        createEvent(work2, Type.create, null, 3);
        WorkEvent event4 = createEvent(work2, Type.fail, com2, 4);

        Iterable<WorkEvent> events = workEventRepo.getLatestEventForEachWorkId(List.of(work1.getId(), work2.getId(), -5));
        assertThat(events).containsExactlyInAnyOrder(event2, event4);
    }

    private WorkEvent createEvent(Work work, Type type, Comment comment, int day) {
        var time = LocalDateTime.of(2021,10, day,0,0);
        WorkEvent event = new WorkEvent(work, type, user, comment);
        event = workEventRepo.save(event); // The time can only be explicitly set in an update, not an insert
        event.setPerformed(time);
        return workEventRepo.save(event);
    }

}
