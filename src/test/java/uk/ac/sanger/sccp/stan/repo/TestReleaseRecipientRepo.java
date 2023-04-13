package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/**
 * Tests {@link ReleaseRecipientRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestReleaseRecipientRepo {
    @Autowired
    ReleaseRecipientRepo recRepo;

    @Test
    @Transactional
    public void testFindAllByEnabled() {
        List<ReleaseRecipient> newRecs = IntStream.range(0,6)
                .mapToObj(i -> new ReleaseRecipient(null, "rec"+i))
                .collect(toList());
        for (int i = 0; i < 6; ++i) {
            newRecs.get(i).setEnabled(i < 3);
        }
        List<ReleaseRecipient> savedRecs = asList(recRepo.saveAll(newRecs));
        final List<ReleaseRecipient> savedEnabled = savedRecs.subList(0, 3);
        final List<ReleaseRecipient> savedDisabled = savedRecs.subList(3, 6);
        final List<ReleaseRecipient> enabledRecs = recRepo.findAllByEnabled(true);
        assertThat(enabledRecs).containsAll(savedEnabled);
        assertThat(enabledRecs).doesNotContainAnyElementsOf(savedDisabled);
        final List<ReleaseRecipient> disabledRecs = recRepo.findAllByEnabled(false);
        assertThat(disabledRecs).containsAll(savedDisabled);
        assertThat(disabledRecs).doesNotContainAnyElementsOf(savedEnabled);
    }

    @Test
    @Transactional
    public void testGetByUsername() {
        ReleaseRecipient rec = recRepo.getByUsername("ET2");
        assertEquals("et2", rec.getUsername());
        assertEquals(1, rec.getId());
        assertTrue(rec.isEnabled());
    }

    @Test
    @Transactional
    public void testGetByUsernameNotFound() {
        assertThat(assertThrows(EntityNotFoundException.class, () -> recRepo.getByUsername("BANANAS!")))
                .hasMessage("No release recipient found with username \"BANANAS!\"");
    }

    @Test
    @Transactional
    public void testGetAllByUsernameIn() {
        List<ReleaseRecipient> recs = recRepo.getAllByUsernameIn(List.of("ET2", "Cm18"));
        assertEquals(1, recs.get(0).getId());
        assertEquals(2, recs.get(1).getId());
        assertEquals("et2", recs.get(0).getUsername());
        assertEquals("cm18", recs.get(1).getUsername());
    }

    @Test
    @Transactional
    public void testGetAllByUsernameInNotFound() {
        assertThat(assertThrows(EntityNotFoundException.class, () -> recRepo.getAllByUsernameIn(List.of("et2", "Banana!", "Banana!", "Custard"))))
                .hasMessage("Unknown recipients: [Banana!, Custard]");
    }
}
