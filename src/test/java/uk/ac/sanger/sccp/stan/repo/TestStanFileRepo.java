package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test {@link StanFileRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestStanFileRepo {
    @Autowired
    StanFileRepo fileRepo;
    @Autowired
    EntityCreator entityCreator;

    private WorkType workType;
    private Project project;
    private CostCode costCode;
    private Program prog;

    private Work makeWork() {
        if (workType==null) {
            workType = entityCreator.createWorkType("Drywalling");
        }
        if (project==null) {
            project = entityCreator.createProject("Stargate");
        }
        if (costCode==null) {
            costCode = entityCreator.createCostCode("S400");
        }
        if (prog==null) {
            prog = entityCreator.createProgram("Hello");
        }
        return entityCreator.createWork(workType, project, prog, costCode, null);
    }

    @Test
    @Transactional
    public void testFindAllActiveByWorkId() {
        Work work1 = makeWork();
        Work work2 = makeWork();
        User user = entityCreator.createUser("user1");
        assertThat(fileRepo.findAllActiveByWorkIdIn(List.of(work1.getId()))).isEmpty();
        StanFile sf1 = fileRepo.save(new StanFile(work1, user, "Alpha", "Beta"));
        StanFile sf2 = fileRepo.save(new StanFile(work1, user, "Gamma", "Delta"));
        StanFile sf3 = fileRepo.save(new StanFile(work2, user, "Epsilon", "Zeta"));
        fileRepo.save(new StanFile(null, null, work2, user, "Epsilon", "Eta", LocalDateTime.now()));
        assertThat(fileRepo.findAllActiveByWorkIdIn(List.of(work1.getId()))).containsExactlyInAnyOrder(sf1, sf2);
        assertThat(fileRepo.findAllActiveByWorkIdIn(List.of(work2.getId()))).containsExactly(sf3);
        assertThat(fileRepo.findAllActiveByWorkIdIn(List.of(work1.getId(), work2.getId()))).containsExactlyInAnyOrder(sf1, sf2, sf3);
    }

    @Test
    @Transactional
    public void testFindAllActiveByWorkIdAndName() {
        Work work1 = makeWork();
        Work work2 = makeWork();
        User user = entityCreator.createUser("user1");
        StanFile sf1 = fileRepo.save(new StanFile(work1, user, "Alpha", "Beta"));
        fileRepo.save(new StanFile(work1, user, "Gamma", "Delta"));
        StanFile sf2 = fileRepo.save(new StanFile(work2, user, "Alpha", "Epsilon"));
        fileRepo.save(new StanFile(null, null, work1, user, "Alpha", "Eta", LocalDateTime.now()));
        assertThat(fileRepo.findAllActiveByWorkIdAndName(Set.of(work1.getId()), "Alpha")).containsExactly(sf1);
        assertThat(fileRepo.findAllActiveByWorkIdAndName(Set.of(work2.getId()), "Gamma")).isEmpty();
        assertThat(fileRepo.findAllActiveByWorkIdAndName(Set.of(work1.getId(), work2.getId()), "Alpha")).containsExactlyInAnyOrder(sf1,sf2);
    }

    @Test
    @Transactional
    public void testGetById() {
        Work work = makeWork();
        User user = entityCreator.createUser("user1");
        StanFile sf1 = fileRepo.save(new StanFile(work, user, "Alpha", "Beta"));
        StanFile sf2 = fileRepo.save(new StanFile(work, user, "Gamma", "Delta"));
        assertEquals(sf1, fileRepo.getById(sf1.getId()));
        assertEquals(sf2, fileRepo.getById(sf2.getId()));
        assertThrows(EntityNotFoundException.class, () -> fileRepo.getById(sf2.getId()+1));
    }

    @Test
    @Transactional
    public void testExistsByPath() {
        Work work = makeWork();
        Work work2 = makeWork();
        User user = entityCreator.createUser("user1");
        final String path = "my/path";
        assertFalse(fileRepo.existsByPath(path));
        fileRepo.save(new StanFile(work, user, "Alpha", path));
        assertTrue(fileRepo.existsByPath(path));
        assertFalse(fileRepo.existsByPath("my/other"));
        fileRepo.save(new StanFile(work2, user, "Alpha", path));
        assertTrue(fileRepo.existsByPath(path));
        assertFalse(fileRepo.existsByPath("my/other"));
    }
}
