package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link DestructionRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestDestructionRepo {
    @Autowired
    DestructionRepo destructionRepo;
    @Autowired
    DestructionReasonRepo destructionReasonRepo;

    @Autowired
    EntityCreator entityCreator;
    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    public void testDestruction() {
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), null);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.createLabwareType("lt", 1, 1), sample);
        User user = entityCreator.createUser("user1");
        DestructionReason reason = destructionReasonRepo.save(new DestructionReason(null, "Everything is bad."));

        Destruction dest = destructionRepo.save(new Destruction(null, lw, user, null, reason));

        assertNotNull(dest.getId());
        assertNotNull(dest.getDestroyed());
        assertEquals(dest.getLabware(), lw);
        assertEquals(dest.getUser(), user);
        assertEquals(dest.getReason(), reason);
    }
}
