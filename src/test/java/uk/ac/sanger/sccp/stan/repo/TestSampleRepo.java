package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link SampleRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestSampleRepo {
    private final EntityCreator entityCreator;
    private final SampleRepo sampleRepo;

    @Autowired
    public TestSampleRepo(EntityCreator entityCreator, SampleRepo sampleRepo) {
        this.entityCreator = entityCreator;
        this.sampleRepo = sampleRepo;
    }

    @Test
    @Transactional
    public void testFindMaxSectionForTissueId() {
        assertThat(sampleRepo.findMaxSectionForTissueId(-1)).isEmpty();
        Donor donor = entityCreator.createDonor("DONOR", LifeStage.adult);
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        BioState bioState = entityCreator.anyBioState();

        sampleRepo.save(new Sample(null, 3, tissue, bioState));
        sampleRepo.save(new Sample(null, 18, tissue, bioState));
        sampleRepo.save(new Sample(null, 4, tissue, bioState));
        assertThat(sampleRepo.findMaxSectionForTissueId(tissue.getId())).hasValue(18);
    }

    @Test
    @Transactional
    public void testFindAllByTissueIdIn() {
        Donor donor = entityCreator.createDonor("DONOR1", LifeStage.adult);
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1", 1);
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", 2);
        Sample sample10 = entityCreator.createSample(tissue1, null);
        Sample sample11 = entityCreator.createSample(tissue1, 1);
        Sample sample12 = entityCreator.createSample(tissue1, 2);
        Sample sample20 = entityCreator.createSample(tissue2, null);
        assertThat(sampleRepo.findAllByTissueIdIn(List.of(tissue1.getId(), tissue2.getId())))
                .containsOnly(sample10, sample11, sample12, sample20);
        assertThat(sampleRepo.findAllByTissueIdIn(List.of(tissue1.getId(), -400)))
                .containsOnly(sample10, sample11, sample12);
        assertThat(sampleRepo.findAllByTissueIdIn(List.of(-400)))
                .isEmpty();
    }
}
