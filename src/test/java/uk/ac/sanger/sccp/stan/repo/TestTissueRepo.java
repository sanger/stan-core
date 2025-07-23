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
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link TissueRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestTissueRepo {
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private TissueRepo tissueRepo;

    @Autowired
    private TissueTypeRepo tissueTypeRepo;
    @Autowired
    private MediumRepo mediumRepo;
    @Autowired
    private FixativeRepo fixativeRepo;
    @Autowired
    private HmdmcRepo hmdmcRepo;

    private int externalNameSeed = 1;

    @Test
    @Transactional
    public void testGetAllByExternalName() {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1", "1");
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", "2a");

        assertThat(tissueRepo.getAllByExternalName("tissue1")).containsExactly(tissue1);
        assertThat(tissueRepo.getAllByExternalName("tissue2")).containsExactly(tissue2);
        assertThat(assertThrows(EntityNotFoundException.class, () -> tissueRepo.getAllByExternalName("Bananas")))
                .hasMessage("Tissue external name not found: \"Bananas\"");
    }

    @Test
    @Transactional
    public void testFindByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate() {
        Donor donor1 = entityCreator.createDonor("DONOR1");
        Donor donor2 = entityCreator.createDonor("DONOR2");
        TissueType tissueType = tissueTypeRepo.findByName("Heart").orElseThrow();
        SpatialLocation sl1 = tissueType.getSpatialLocations().get(0);
        SpatialLocation sl2 = tissueType.getSpatialLocations().get(1);
        Medium med1 = mediumRepo.findByName("OCT").orElseThrow();
        Medium med2 = mediumRepo.findByName("Paraffin").orElseThrow();
        Fixative fix1 = fixativeRepo.findByName("None").orElseThrow();
        Fixative fix2 = fixativeRepo.findByName("Formalin").orElseThrow();
        CellClass cellClass = entityCreator.getTissueCellClass();
        String rep1 = "1";
        String rep2 = "2";

        Tissue tissue = tissueRepo.save(new Tissue(null, "TISSUE1", rep1, sl1, donor1,
                med1, fix1, cellClass, entityCreator.getAny(hmdmcRepo), null, null));

        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor1.getId(), sl1.getId(), med1.getId(), fix1.getId(), rep1))
                .contains(tissue);

        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor2.getId(), sl1.getId(), med1.getId(), fix1.getId(), rep1))
                .isEmpty();
        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor1.getId(), sl2.getId(), med1.getId(), fix1.getId(), rep1))
                .isEmpty();
        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor1.getId(), sl1.getId(), med2.getId(), fix1.getId(), rep1))
                .isEmpty();
        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor1.getId(), sl1.getId(), med1.getId(), fix2.getId(), rep1))
                .isEmpty();
        assertThat(tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(donor1.getId(), sl1.getId(), med1.getId(), fix1.getId(), rep2))
                .isEmpty();
    }

    @Test
    @Transactional
    public void testFindByDonorId() {
        Donor donor1 = entityCreator.createDonor("DONOR1");
        Donor donor2 = entityCreator.createDonor("DONOR2");
        Tissue tissue1A = entityCreator.createTissue(donor1, "TISSUE1A", "1");
        Tissue tissue1B = entityCreator.createTissue(donor1, "TISSUE1B", "2");
        Tissue tissue2 = entityCreator.createTissue(donor2, "TISSUE2", "1");

        assertThat(tissueRepo.findByDonorId(donor1.getId())).containsExactlyInAnyOrder(tissue1A, tissue1B);
        assertThat(tissueRepo.findByDonorId(donor2.getId())).containsExactly(tissue2);
        assertThat(tissueRepo.findByDonorId(-400)).isEmpty();
    }

    @Test
    @Transactional
    public void testFindAllByExternalNameIn() {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1", "1");
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", "1");
        entityCreator.createTissue(donor, "TISSUE3", "1");

        assertThat(tissueRepo.findAllByExternalNameIn(List.of("tissue1", "Tissue2"))).containsExactlyInAnyOrder(tissue1, tissue2);
    }

    @Test
    @Transactional
    public void testFindAllByExternalNameLike() {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1", "1");
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", "1");
        Tissue tissue3 = entityCreator.createTissue(donor, "XTIS2", "1");
        assertThat(tissueRepo.findAllByExternalNameLike("%TIS%")).containsExactlyInAnyOrder(tissue1, tissue2, tissue3);
        assertThat(tissueRepo.findAllByExternalNameLike("TIS%")).containsExactlyInAnyOrder(tissue1, tissue2);
        assertThat(tissueRepo.findAllByExternalNameLike("%tis%2")).containsExactlyInAnyOrder(tissue2, tissue3);
        assertThat(tissueRepo.findAllByExternalNameLike("TIS2%")).isEmpty();
    }

    @Test
    @Transactional
    public void testFindByTissueTypeId() {
        Donor donor = entityCreator.createDonor("DONOR1");
        TissueType tt1 = tissueTypeRepo.findByName("Heart").orElseThrow();
        TissueType tt2 = tissueTypeRepo.findByName("Kidney").orElseThrow();
        final SpatialLocation[] sls = {
                tt1.getSpatialLocations().get(0),
                tt1.getSpatialLocations().get(1),
                tt2.getSpatialLocations().get(0),
        };
        Fixative fix = entityCreator.getAny(fixativeRepo);
        Medium med = entityCreator.getAny(mediumRepo);
        Hmdmc hmdmc = entityCreator.getAny(hmdmcRepo);
        CellClass cellClass = entityCreator.getTissueCellClass();
        Tissue[] tissues = IntStream.range(0, 3)
                .mapToObj(i -> tissueRepo.save(new Tissue(null, "TISSUE"+i, String.valueOf(i+1), sls[i],
                        donor, med, fix, cellClass, hmdmc, null, null)))
                .toArray(Tissue[]::new);
        assertThat(tissueRepo.findByTissueTypeId(tt1.getId())).containsExactly(tissues[0], tissues[1]);
    }

    @Test
    @Transactional
    public void testFindMaxReplicateForDonorIdAndSpatialLocationId() {
        Donor d1 = entityCreator.createDonor("DONOR1");
        Donor d2 = entityCreator.createDonor("DONOR2");
        TissueType tt1 = tissueTypeRepo.findByName("Heart").orElseThrow();
        SpatialLocation sl1 = tt1.getSpatialLocations().get(0);
        SpatialLocation sl2 = tt1.getSpatialLocations().get(1);
        createTissue(d2, sl1, "5");
        createTissue(d1, sl2, "6");
        assertNull(tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
        createTissue(d1, sl1, "1");
        createTissue(d1, sl1, "2");
        assertEquals(2, tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
        createTissue(d1, sl1, "10");
        createTissue(d1, sl1, "3");
        assertEquals(10, tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
        createTissue(d1, sl1, "5a");
        assertEquals(10, tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
        createTissue(d1, sl1, "10a");
        assertEquals(10, tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
        createTissue(d1, sl1, "11a");
        assertEquals(11, tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(d1.getId(), sl1.getId()));
    }

    private Tissue createTissue(Donor donor, SpatialLocation sl, String rep) {
        String name = "" + (++externalNameSeed);
        return entityCreator.createTissue(donor, sl, name, rep);
    }
}
