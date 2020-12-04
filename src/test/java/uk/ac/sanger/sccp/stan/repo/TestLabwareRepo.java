package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

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
        lw.setBarcode(barcode);
        labwareRepo.save(lw);

        assertEquals(labwareRepo.getByBarcode(barcode), lw);
        assertTrue(labwareRepo.existsByBarcode(barcode));

        assertThrows(EntityNotFoundException.class, () -> labwareRepo.getByBarcode("STAN-404"));
        assertFalse(labwareRepo.existsByBarcode("STAN-404"));
    }
}
