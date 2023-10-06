package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LabwareRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestLabwareRepo {
    @Autowired
    LabwareRepo labwareRepo;
    @Autowired
    LabwareTypeRepo labwareTypeRepo;
    @Autowired
    EntityCreator entityCreator;

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
        assertThat(created).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));

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

    @Test
    @Transactional
    public void testFindBarcodesByBarcodeIn() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");
        labwareRepo.saveAll(IntStream.range(0,4)
                .mapToObj(i -> new Labware(null, "STAN-A"+i, lt, null))
                .collect(toList()));

        assertThat(labwareRepo.findBarcodesByBarcodeIn(List.of("STAN-A1", "stan-A2", "STAN-a0", "STAN-A1", "STAN-A5")))
                .containsExactlyInAnyOrder("STAN-A0", "STAN-A1", "STAN-A2");
    }

    @Test
    @Transactional
    public void testFindAllContainingSampleIds() {
        Sample sample1 = entityCreator.createSample(null, null);
        Sample sample2 = entityCreator.createSample(sample1.getTissue(), null, sample1.getBioState());
        int[] sampleIds = { sample1.getId(), sample2.getId() };

        Labware lw1 = entityCreator.createTube("STAN-E");
        LabwareType lt = lw1.getLabwareType();
        int[] lwIds = {
                entityCreator.createLabware("STAN-0", lt, sample1).getId(),
                entityCreator.createLabware("STAN-1", lt, sample2).getId(),
                entityCreator.createLabware("STAN-2", lt, sample1, sample2).getId(),
        };

        assertThat(labwareRepo.findAllLabwareIdsContainingSampleIds(List.of(-17))).isEmpty();
        assertThat(labwareRepo.findAllLabwareIdsContainingSampleIds(List.of(sampleIds[0]))).containsExactlyInAnyOrder(lwIds[0], lwIds[2]);
        assertThat(labwareRepo.findAllLabwareIdsContainingSampleIds(List.of(sampleIds[1]))).containsExactlyInAnyOrder(lwIds[1], lwIds[2]);
        assertThat(labwareRepo.findAllLabwareIdsContainingSampleIds(List.of(sampleIds[0], sampleIds[1]))).containsExactlyInAnyOrder(lwIds[0], lwIds[1], lwIds[2]);
    }
}
