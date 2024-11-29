package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests {@link BarcodeIntRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestBarcodeIntRepo {
    private static final int MIN_SEED = 10_000, MAX_SEED = 0xFFFF;

    private final Pattern BC_PTN = Pattern.compile("^STAN-[0-9A-F]{5}$");

    @Autowired
    BarcodeIntRepo barcodeIntRepo;

    @Transactional
    @Test
    public void testSeed() {
        int seed1 = barcodeIntRepo.next();
        checkRange(seed1);
        int seed2 = barcodeIntRepo.next();
        checkRange(seed2);
        int seed3 = barcodeIntRepo.next();
        checkRange(seed3);
        assertNotEquals(seed1, seed2);
        assertNotEquals(seed1, seed3);
        assertNotEquals(seed2, seed3);
    }

    @Test
    @Transactional
    public void testSeeds() {
        List<Integer> seeds = barcodeIntRepo.next(3);
        seeds.forEach(TestBarcodeIntRepo::checkRange);
        assertThat(new HashSet<>(seeds)).hasSameSizeAs(seeds);
        int seed = barcodeIntRepo.next();
        checkRange(seed);
        assertThat(seeds).doesNotContain(seed);
    }

    @Transactional
    @Test
    public void testBarcode() {
        List<String> barcodes = IntStream.range(0,3)
                .mapToObj(i -> barcodeIntRepo.createStanBarcode())
                .toList();
        barcodes.forEach(this::checkFormat);
        assertThat(new HashSet<>(barcodes)).hasSameSizeAs(barcodes);
    }

    @Transactional
    @Test
    public void testBarcodes() {
        List<String> barcodes = barcodeIntRepo.createStanBarcodes(3);
        barcodes.forEach(this::checkFormat);
        final HashSet<String> bcSet = new HashSet<>(barcodes);
        assertThat(bcSet).hasSameSizeAs(barcodes);
        List<String> moreBarcodes = barcodeIntRepo.createStanBarcodes(2);
        moreBarcodes.forEach(this::checkFormat);
        assertNotEquals(moreBarcodes.get(0), moreBarcodes.get(1));
        assertThat(bcSet).doesNotContainAnyElementsOf(moreBarcodes);
    }

    private static void checkRange(int seed) {
        assertThat(seed).isBetween(MIN_SEED, MAX_SEED);
    }
    private void checkFormat(String bc) {
        assertThat(bc).matches(BC_PTN);
    }
}
