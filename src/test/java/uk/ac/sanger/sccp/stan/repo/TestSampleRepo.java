package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SampleRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestSampleRepo {
    private final SampleRepo sampleRepo;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SpatialLocationRepo slRepo;
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final HmdmcRepo hmdmcRepo;

    @Autowired
    public TestSampleRepo(SampleRepo sampleRepo, DonorRepo donorRepo, TissueRepo tissueRepo,
                          SpatialLocationRepo slRepo, MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                          FixativeRepo fixativeRepo, HmdmcRepo hmdmcRepo) {
        this.sampleRepo = sampleRepo;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.slRepo = slRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.hmdmcRepo = hmdmcRepo;
    }

    @Test
    @Transactional
    public void testFindMaxSectionForTissueId() {
        assertThat(sampleRepo.findMaxSectionForTissueId(-1)).isEmpty();
        Donor donor = new Donor(null, "DONOR", LifeStage.adult);
        donorRepo.save(donor);
        Tissue tissue = new Tissue(null, "TISSUE1", 1, any(slRepo), donor, any(mouldSizeRepo),
                any(mediumRepo), any(fixativeRepo), any(hmdmcRepo));
        tissueRepo.save(tissue);

        sampleRepo.save(new Sample(null, 3, tissue));
        sampleRepo.save(new Sample(null, 18, tissue));
        sampleRepo.save(new Sample(null, 4, tissue));
        assertThat(sampleRepo.findMaxSectionForTissueId(tissue.getId())).hasValue(18);
    }

    private <T> T any(CrudRepository<T, ?> repo) {
        return repo.findAll().iterator().next();
    }
}
