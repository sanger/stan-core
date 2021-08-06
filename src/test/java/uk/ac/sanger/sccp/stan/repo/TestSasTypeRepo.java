package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.SasType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link SasTypeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestSasTypeRepo {
    @Autowired
    private SasTypeRepo sasTypeRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> sasTypeRepo.getByName("Drywalling"));
        SasType pr = sasTypeRepo.save(new SasType(null, "Drywalling"));
        assertEquals(pr, sasTypeRepo.getByName("drywalling"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<SasType> sasTypes = sasTypeRepo.findAllByEnabled(true);

        SasType pr = sasTypeRepo.save(new SasType(null, "Drywalling"));
        List<SasType> moar = sasTypeRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(sasTypes.size()+1);

        pr.setEnabled(false);
        sasTypeRepo.save(pr);
        List<SasType> less = sasTypeRepo.findAllByEnabled(true);
        assertThat(less).hasSize(sasTypes.size()).containsExactlyInAnyOrderElementsOf(sasTypes);

        List<SasType> disabled = sasTypeRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
