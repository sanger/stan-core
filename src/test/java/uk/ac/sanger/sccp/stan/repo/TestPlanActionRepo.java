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
 * Tests for {@link PlanActionRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestPlanActionRepo {
    private final PlanOperationRepo planOpRepo;
    private final PlanActionRepo planActionRepo;
    private final OperationTypeRepo opTypeRepo;
    private final SampleRepo sampleRepo;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SpatialLocationRepo slRepo;
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final HmdmcRepo hmdmcRepo;
    private final UserRepo userRepo;
    private final LabwareTypeRepo labwareTypeRepo;
    private final LabwareRepo labwareRepo;
    private final SlotRepo slotRepo;

    @Autowired
    public TestPlanActionRepo(PlanOperationRepo planOpRepo, PlanActionRepo planActionRepo, OperationTypeRepo opTypeRepo,
                              SampleRepo sampleRepo, DonorRepo donorRepo, TissueRepo tissueRepo,
                              SpatialLocationRepo slRepo, MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                              FixativeRepo fixativeRepo, HmdmcRepo hmdmcRepo, UserRepo userRepo,
                              LabwareTypeRepo labwareTypeRepo, LabwareRepo labwareRepo, SlotRepo slotRepo) {
        this.planOpRepo = planOpRepo;
        this.planActionRepo = planActionRepo;
        this.opTypeRepo = opTypeRepo;
        this.sampleRepo = sampleRepo;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.slRepo = slRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.userRepo = userRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.labwareRepo = labwareRepo;
        this.slotRepo = slotRepo;
    }

    @Test
    @Transactional
    public void testFindMaxPlannedSectionForTissueId() {
        assertThat(planActionRepo.findMaxPlannedSectionForTissueId(-1)).isEmpty();
        Donor donor = new Donor(null, "DONOR", LifeStage.adult);
        donorRepo.save(donor);
        Tissue tissue = new Tissue(null, "TISSUE1", 1, any(slRepo), donor, any(mouldSizeRepo),
                any(mediumRepo), any(fixativeRepo), any(hmdmcRepo));
        tissueRepo.save(tissue);
        final Sample sample = new Sample(null, 3, tissue);

        sampleRepo.save(sample);

        PlanOperation plan = new PlanOperation();
        plan.setOperationType(any(opTypeRepo));
        plan.setUser(any(userRepo));
        planOpRepo.save(plan);

        Labware lw = new Labware(null, "STAN-001A", any(labwareTypeRepo), null);
        labwareRepo.save(lw);
        Slot slot1 = new Slot(null, lw.getId(), new Address(1,1), null, null, null);
        Slot slot2 = new Slot(null, lw.getId(), new Address(1,2), null, null, null);
        Slot slot3 = new Slot(null, lw.getId(), new Address(1,3), null, null, null);
        slotRepo.save(slot1);
        slotRepo.save(slot2);
        slotRepo.save(slot3);

        planActionRepo.save(new PlanAction(null, plan.getId(), slot1, slot1, sample, 3));
        planActionRepo.save(new PlanAction(null, plan.getId(), slot2, slot2, sample, 18));
        planActionRepo.save(new PlanAction(null, plan.getId(), slot3, slot3, sample, 4));
        assertThat(planActionRepo.findMaxPlannedSectionForTissueId(tissue.getId())).hasValue(18);
    }

    private <T> T any(CrudRepository<T, ?> repo) {
        return repo.findAll().iterator().next();
    }
}
