package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link LabwareTypeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestLabwareTypeRepo {
    private final LabwareTypeRepo labwareTypeRepo;

    @Autowired
    public TestLabwareTypeRepo(LabwareTypeRepo labwareTypeRepo) {
        this.labwareTypeRepo = labwareTypeRepo;
    }

    @Test
    @Transactional
    public void testGetByName() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");
        assertNotNull(lt);
        assertThat(lt.getName()).isEqualToIgnoringCase("Proviasette");
        assertThrows(EntityNotFoundException.class, () -> labwareTypeRepo.getByName("Bananas"));
    }
}
