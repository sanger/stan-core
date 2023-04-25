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

    @Transactional
    @Test
    public void testGetLatestEventForEachWorkId() {
        User user = entityCreator.createUser("user1");
        WorkType wt = entityCreator.createWorkType("Rocks");
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("4");
        ReleaseRecipient wr = entityCreator.createReleaseRecipient("test1");
        Program prog = entityCreator.createProgram("Hello");
        Work work1 = entityCreator.createWork(wt, pr, prog, cc, wr);
        Work work2 = entityCreator.createWork(wt, pr, prog, cc, wr);
        Comment com1 = commentRepo.save(new Comment(null, "Alpha", "work"));
        Comment com2 = commentRepo.save(new Comment(null, "Beta", "work"));

        createEvent(work1, Type.create, null, 1, user);
        WorkEvent event2 = createEvent(work1, Type.pause, com1, 2, user);
        createEvent(work2, Type.create, null, 3, user);
        WorkEvent event4 = createEvent(work2, Type.fail, com2, 4, user);

        Iterable<WorkEvent> events = workEventRepo.getLatestEventForEachWorkId(List.of(work1.getId(), work2.getId(), -5));
        assertThat(events).containsExactlyInAnyOrder(event2, event4);
    }

    @Transactional
    @Test
    public void testFindAllByUserAndType() {
        User user1 = entityCreator.createUser("user1");
        User user2 = entityCreator.createUser("user2");
        Work[] works = new Work[4];
        works[0] = entityCreator.createWork(null, null, null, null, null);
        for (int i = 1; i < works.length; ++i) {
            works[i] = entityCreator.createWorkLike(works[0]);
        }
        WorkEvent[] events = {createEvent(works[0], Type.create, null, 1, user1),
                createEvent(works[1], Type.create, null, 1, user2),
                createEvent(works[1], Type.pause, null, 1, user1),
                createEvent(works[2], Type.create, null, 1, user1)};
        assertThat(workEventRepo.findAllByUserAndType(user1, Type.create))
                .containsExactlyInAnyOrder(events[0], events[3]);
    }

    private WorkEvent createEvent(Work work, Type type, Comment comment, int day, User user) {
        var time = LocalDateTime.of(2021,10, day,0,0);
        WorkEvent event = new WorkEvent(work, type, user, comment);
        event = workEventRepo.save(event); // The time can only be explicitly set in an update, not an insert
        event.setPerformed(time);
        return workEventRepo.save(event);
    }

}
