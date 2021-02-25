package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DonorRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestDonorRepo {
    @Autowired
    DonorRepo donorRepo;

    @Autowired
    SpeciesRepo speciesRepo;

    @Test
    @Transactional
    public void testGetByDonorName() {
        Species species = speciesRepo.findByName("Human").orElseThrow();
        Donor donor1 = donorRepo.save(new Donor(null, "DONOR1", LifeStage.adult, species));

        assertEquals(donor1, donorRepo.getByDonorName("donor1"));

        assertThat(assertThrows(EntityNotFoundException.class, () -> donorRepo.getByDonorName("donorX")))
                .hasMessage("Donor name not found: \"donorX\"");
    }
}
