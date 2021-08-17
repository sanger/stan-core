package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Project;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ProjectRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestProjectRepo {
    @Autowired
    private ProjectRepo projectRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> projectRepo.getByName("Stargate"));
        Project pr = projectRepo.save(new Project(null, "Stargate"));
        assertEquals(pr, projectRepo.getByName("stargate"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<Project> projects = projectRepo.findAllByEnabled(true);

        Project pr = projectRepo.save(new Project(null, "Stargate"));
        List<Project> moar = projectRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(projects.size()+1);

        pr.setEnabled(false);
        projectRepo.save(pr);
        List<Project> less = projectRepo.findAllByEnabled(true);
        assertThat(less).hasSize(projects.size()).containsExactlyInAnyOrderElementsOf(projects);

        List<Project> disabled = projectRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
