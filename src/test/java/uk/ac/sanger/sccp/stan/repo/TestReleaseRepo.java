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
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link ReleaseRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestReleaseRepo {
    @Autowired
    EntityCreator entityCreator;

    @Autowired
    ReleaseRepo releaseRepo;
    @Autowired
    ReleaseDetailRepo releaseDetailRepo;

    @Autowired
    EntityManager entityManager;

    /**
     * Tests creating a release in {@link ReleaseRepo} and {@link ReleaseDetailRepo}
     */
    @Test
    @Transactional
    public void testSaveRelease() {
        Donor donor = entityCreator.createDonor("DONOR1", LifeStage.adult);
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, null);
        Sample sample1 = entityCreator.createSample(tissue, 1);
        User user = entityCreator.createUser("user1");
        ReleaseDestination destination = entityCreator.createReleaseDestination("Venus");
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("Mekon");
        LabwareType lwtype = entityCreator.createLabwareType("plate4", 1, 4);
        Labware labware = entityCreator.createLabware("STAN-123", lwtype, sample, sample, sample1);
        Release release = new Release(labware, user, destination, recipient);
        Release saved = releaseRepo.save(release);
        Integer releaseId = saved.getId();
        assertNotNull(releaseId);
        List<ReleaseDetail> newDetails = labware.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sm -> new ReleaseDetail(null, releaseId, slot.getId(), sm.getId())))
                .collect(Collectors.toList());
        releaseDetailRepo.saveAll(newDetails);

        entityManager.refresh(saved);
        for (int i = 0; i < 2; ++i) {
            Release rel;
            if (i==0) {
                rel = saved;
            } else {
                entityManager.flush();
                rel = releaseRepo.findById(saved.getId()).orElseThrow();
            }
            assertNotNull(rel.getId());
            assertNotNull(rel.getReleased());
            assertEquals(labware, rel.getLabware());
            assertEquals(destination, rel.getDestination());
            assertEquals(recipient, rel.getRecipient());
            var details = rel.getDetails();
            assertThat(details).hasSize(newDetails.size());
            for (ReleaseDetail expected : newDetails) {
                ReleaseDetail actual = details.stream()
                        .filter(det -> det.getSampleId().equals(expected.getSampleId())
                                && det.getSlotId().equals(expected.getSlotId()))
                        .findAny()
                        .orElseThrow();
                assertNotNull(actual.getId());
                assertEquals(releaseId, actual.getReleaseId());
            }
        }
    }

}
