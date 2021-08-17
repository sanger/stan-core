package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.CostCode;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CostCodeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestCostCodeRepo {
    @Autowired
    private CostCodeRepo costCodeRepo;

    @Test
    @Transactional
    public void testGetByName() {
        assertThrows(EntityNotFoundException.class, () -> costCodeRepo.getByCode("S616"));
        CostCode pr = costCodeRepo.save(new CostCode(null, "S616"));
        assertEquals(pr, costCodeRepo.getByCode("s616"));
    }

    @Test
    @Transactional
    public void findAllByEnabled() {
        List<CostCode> costCodes = costCodeRepo.findAllByEnabled(true);

        CostCode pr = costCodeRepo.save(new CostCode(null, "S616"));
        List<CostCode> moar = costCodeRepo.findAllByEnabled(true);
        assertThat(moar).contains(pr).hasSize(costCodes.size()+1);

        pr.setEnabled(false);
        costCodeRepo.save(pr);
        List<CostCode> less = costCodeRepo.findAllByEnabled(true);
        assertThat(less).hasSize(costCodes.size()).containsExactlyInAnyOrderElementsOf(costCodes);

        List<CostCode> disabled = costCodeRepo.findAllByEnabled(false);
        assertThat(disabled).contains(pr);
    }
}
