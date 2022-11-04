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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        return entityCreator.createWork(workType, project, costCode, null);
    }

    @Test
    @Transactional
    public void testFindAllActiveByWorkId() {
        Work work1 = makeWork();
        Work work2 = makeWork();
        assertThat(fileRepo.findAllActiveByWorkId(work1.getId())).isEmpty();
        StanFile sf1 = fileRepo.save(new StanFile(work1, "Alpha", "Beta"));
        StanFile sf2 = fileRepo.save(new StanFile(work1, "Gamma", "Delta"));
        StanFile sf3 = fileRepo.save(new StanFile(work2, "Epsilon", "Zeta"));
        fileRepo.save(new StanFile(null, null, work2, "Epsilon", "Eta", LocalDateTime.now()));
        assertThat(fileRepo.findAllActiveByWorkId(work1.getId())).containsExactlyInAnyOrder(sf1, sf2);
        assertThat(fileRepo.findAllActiveByWorkId(work2.getId())).containsExactly(sf3);
    }

    @Test
    @Transactional
    public void testFindAllActiveByWorkIdAndName() {
        Work work1 = makeWork();
        Work work2 = makeWork();
        StanFile sf1 = fileRepo.save(new StanFile(work1, "Alpha", "Beta"));
        fileRepo.save(new StanFile(work1, "Gamma", "Delta"));
        fileRepo.save(new StanFile(work2, "Alpha", "Epsilon"));
        fileRepo.save(new StanFile(null, null, work1, "Alpha", "Eta", LocalDateTime.now()));
        assertThat(fileRepo.findAllActiveByWorkIdAndName(work1.getId(), "Alpha")).containsExactly(sf1);
        assertThat(fileRepo.findAllActiveByWorkIdAndName(work2.getId(), "Gamma")).isEmpty();
    }

    @Test
    @Transactional
    public void testGetById() {
        Work work = makeWork();
        StanFile sf1 = fileRepo.save(new StanFile(work, "Alpha", "Beta"));
        StanFile sf2 = fileRepo.save(new StanFile(work, "Gamma", "Delta"));
        assertEquals(sf1, fileRepo.getById(sf1.getId()));
        assertEquals(sf2, fileRepo.getById(sf2.getId()));
        assertThrows(EntityNotFoundException.class, () -> fileRepo.getById(sf2.getId()+1));
    }
}
