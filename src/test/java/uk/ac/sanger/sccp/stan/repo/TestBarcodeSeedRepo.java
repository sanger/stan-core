package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link BarcodeSeedRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestBarcodeSeedRepo {
    private final BarcodeSeedRepo barcodeSeedRepo;

    @Autowired
    public TestBarcodeSeedRepo(BarcodeSeedRepo barcodeSeedRepo) {
        this.barcodeSeedRepo = barcodeSeedRepo;
    }

    @Test
    @Transactional
    public void testBarcodeSeed() {
        String barcode1 = barcodeSeedRepo.createStanBarcode();
        String barcode2 = barcodeSeedRepo.createStanBarcode();
        assertThat(barcode1).matches("STAN-[0-9A-F]{5,}");
        assertThat(barcode2).matches("STAN-[0-9A-F]{5,}");
        assertNotEquals(barcode1, barcode2);
    }
}
