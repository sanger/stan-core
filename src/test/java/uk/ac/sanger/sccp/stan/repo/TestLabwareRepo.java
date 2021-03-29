package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LabwareRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestLabwareRepo {
    private final LabwareRepo labwareRepo;
    private final LabwareTypeRepo labwareTypeRepo;

    @Autowired
    public TestLabwareRepo(LabwareRepo labwareRepo, LabwareTypeRepo labwareTypeRepo) {
        this.labwareRepo = labwareRepo;
        this.labwareTypeRepo = labwareTypeRepo;
    }

    @Test
    @Transactional
    public void testGetByBarcode() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");
        final String barcode = "STAN-0001A";
        Labware lw = new Labware(null, barcode, lt, null);
        labwareRepo.save(lw);

        assertEquals(labwareRepo.getByBarcode(barcode), lw);
        assertTrue(labwareRepo.existsByBarcode(barcode));
        LocalDateTime created = lw.getCreated();
        assertNotNull(created);
        assertThat(created).isCloseToUtcNow(within(1, ChronoUnit.MINUTES));

        assertThrows(EntityNotFoundException.class, () -> labwareRepo.getByBarcode("STAN-404"));
        assertFalse(labwareRepo.existsByBarcode("STAN-404"));
    }

    @Test
    @Transactional
    public void testFindAllByIdIn() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");
        Labware[] labware = IntStream.range(0,3)
                .mapToObj(i -> new Labware(null, "STAN-A"+i, lt, null))
                .map(labwareRepo::save)
                .toArray(Labware[]::new);
        int[] labwareIds = Arrays.stream(labware).mapToInt(Labware::getId).toArray();
        assertThat(labwareRepo.findAllByIdIn(List.of(labwareIds[0], labwareIds[1], labwareIds[2])))
                .containsExactlyInAnyOrder(labware);
        assertThat(labwareRepo.findAllByIdIn(List.of(labwareIds[0], labwareIds[1], -100)))
                .containsExactlyInAnyOrder(labware[0], labware[1]);
        assertThat(labwareRepo.findAllByIdIn(List.of(-100, -101)))
                .isEmpty();
    }

    @Test
    @Transactional
    public void testExistsByExternalBarcode() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");

        String xb = "EXT-11";
        Labware lw = new Labware(null, "STAN-001A", lt, null);
        lw.setExternalBarcode(xb);
        labwareRepo.save(lw);
        assertTrue(labwareRepo.existsByExternalBarcode(xb));
        assertTrue(labwareRepo.existsByExternalBarcode(xb.toLowerCase()));
        assertFalse(labwareRepo.existsByExternalBarcode("STAN-001A"));
    }
}
