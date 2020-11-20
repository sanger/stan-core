package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.OperationType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestOperationTypeRepo {
    private final OperationTypeRepo opTypeRepo;

    @Autowired
    public TestOperationTypeRepo(OperationTypeRepo opTypeRepo) {
        this.opTypeRepo = opTypeRepo;
    }

    @Test
    @Transactional
    public void testGetOperationType() {
        opTypeRepo.save(new OperationType(null, "Science"));
        OperationType opType = opTypeRepo.getByName("SCIENCE");
        assertNotNull(opType);
        assertNotNull(opType.getId());

        assertThrows(EntityNotFoundException.class, () -> opTypeRepo.getByName("Homeopathy"));
    }
}
