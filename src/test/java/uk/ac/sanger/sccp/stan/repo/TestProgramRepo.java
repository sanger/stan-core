package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Program;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ProgramRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestProgramRepo {
    @Autowired
    private ProgramRepo programRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> programRepo.getByName("Hello"));
        Program pr = programRepo.save(new Program("Hello"));
        assertEquals(pr, programRepo.getByName("hello"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<Program> programs = programRepo.findAllByEnabled(true);

        Program pr = programRepo.save(new Program("Hello"));
        List<Program> moar = programRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(programs.size()+1);

        pr.setEnabled(false);
        programRepo.save(pr);
        List<Program> less = programRepo.findAllByEnabled(true);
        assertThat(less).hasSize(programs.size()).containsExactlyInAnyOrderElementsOf(programs);

        List<Program> disabled = programRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
