package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.WorkType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link WorkTypeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestWorkTypeRepo {
    @Autowired
    private WorkTypeRepo workTypeRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> workTypeRepo.getByName("Drywalling"));
        WorkType pr = workTypeRepo.save(new WorkType(null, "Drywalling"));
        assertEquals(pr, workTypeRepo.getByName("drywalling"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<WorkType> workTypes = workTypeRepo.findAllByEnabled(true);

        WorkType pr = workTypeRepo.save(new WorkType(null, "Drywalling"));
        List<WorkType> moar = workTypeRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(workTypes.size()+1);

        pr.setEnabled(false);
        workTypeRepo.save(pr);
        List<WorkType> less = workTypeRepo.findAllByEnabled(true);
        assertThat(less).hasSize(workTypes.size()).containsExactlyInAnyOrderElementsOf(workTypes);

        List<WorkType> disabled = workTypeRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
