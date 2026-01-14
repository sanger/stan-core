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
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
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
    EntityManager entityManager;

    private Sample createSample() {
        return entityCreator.createSample(
                entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "EXT1"),
                null);
    }

    /**
     * Tests creating a release
     */
    @Test
    @Transactional
    public void testSaveRelease() {
        Sample sample = createSample();
        Sample sample1 = entityCreator.createSample(sample.getTissue(), 1);
        User user = entityCreator.createUser("user1");
        ReleaseDestination destination = entityCreator.createReleaseDestination("Venus");
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("Mekon");
        List<ReleaseRecipient> otherRecs = IntStream.range(0,3)
                .mapToObj(i -> entityCreator.createReleaseRecipient("rec"+i))
                .collect(toList());
        LabwareType lwtype = entityCreator.createLabwareType("plate4", 1, 4);
        Labware labware = entityCreator.createLabware("STAN-123", lwtype, sample, sample, sample1);
        Snapshot snap = entityCreator.createSnapshot(labware);
        Release release = new Release(labware, user, destination, recipient, snap.getId());
        release.setOtherRecipients(otherRecs);
        Release saved = releaseRepo.save(release);
        Integer releaseId = saved.getId();
        assertNotNull(releaseId);
        entityManager.flush();

        entityManager.refresh(saved);
        for (int i = 0; i < 2; ++i) {
            Release rel;
            if (i==0) {
                rel = saved;
            } else {
                rel = releaseRepo.findById(saved.getId()).orElseThrow();
            }
            assertNotNull(rel.getId());
            assertNotNull(rel.getReleased());
            assertEquals(labware, rel.getLabware());
            assertEquals(destination, rel.getDestination());
            assertEquals(recipient, rel.getRecipient());
            assertEquals(snap.getId(), rel.getSnapshotId());
            assertThat(rel.getOtherRecipients()).containsExactlyInAnyOrderElementsOf(otherRecs);
        }
    }

    private Release[] createReleases() {
        Sample sample = entityCreator.createBlockSample(null);
        Labware lw1 = entityCreator.createTube("STAN-01", sample);
        Labware lw2 = entityCreator.createTube("STAN-02", sample);
        User user = entityCreator.createUser("user1");
        ReleaseDestination destination = entityCreator.createReleaseDestination("Venus");
        ReleaseRecipient recipient = entityCreator.createReleaseRecipient("Mekon");
        Release r1 = releaseRepo.save(new Release(lw1, user, destination, recipient, entityCreator.createSnapshot(lw1).getId()));
        Release r2 = releaseRepo.save(new Release(lw2, user, destination, recipient, entityCreator.createSnapshot(lw2).getId()));
        return new Release[] { r1, r2 };
    }

    @Test
    @Transactional
    public void testFindAllByLabwareIdIn() {
        Release[] releases = createReleases();
        int lw1id = releases[0].getLabware().getId();
        int lw2id = releases[1].getLabware().getId();

        assertThat(releaseRepo.findAllByLabwareIdIn(List.of(lw1id, lw2id)))
                .containsExactlyInAnyOrder(releases);
        assertThat(releaseRepo.findAllByLabwareIdIn(List.of(lw1id)))
                .containsExactlyInAnyOrder(releases[0]);
        assertThat(releaseRepo.findAllByLabwareIdIn(List.of(lw1id+lw2id))).isEmpty();
    }

    @Test
    @Transactional
    public void testFindAllIdIn() {
        Release[] releases = createReleases();
        int id0 = releases[0].getId();
        int id1 = releases[1].getId();

        assertThat(releaseRepo.findAllByIdIn(List.of(id0, id1)))
                .containsExactlyInAnyOrder(releases);
        assertThat(releaseRepo.findAllByIdIn(List.of(id0)))
                .containsExactlyInAnyOrder(releases[0]);
        assertThat(releaseRepo.findAllByIdIn(List.of(id0+id1))).isEmpty();
    }

    @Test
    @Transactional
    public void testFindReleaseSlotSampleIds() {
        Release[] releases = createReleases();
        int r0id = releases[0].getId();
        int r1id = releases[1].getId();
        List<Integer> rids = List.of(r0id, r1id);
        Map<Integer, Set<SlotIdSampleId>> rssids = releaseRepo.findReleaseSlotSampleIds(rids);
        assertThat(rssids.keySet()).containsExactlyInAnyOrderElementsOf(rids);
        Slot[] slots = Arrays.stream(releases).map(r -> r.getLabware().getFirstSlot()).toArray(Slot[]::new);
        assertThat(rssids.get(r0id)).containsExactly(
                new SlotIdSampleId(slots[0], slots[0].getSamples().get(0))
        );
        assertThat(rssids.get(r1id)).containsExactly(
                new SlotIdSampleId(slots[1], slots[1].getSamples().get(0))
        );
    }

}
