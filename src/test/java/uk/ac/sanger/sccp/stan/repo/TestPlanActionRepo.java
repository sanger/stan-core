package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PlanActionRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestPlanActionRepo {
    private final EntityCreator entityCreator;

    private final PlanOperationRepo planOpRepo;
    private final PlanActionRepo planActionRepo;
    private final OperationTypeRepo opTypeRepo;
    private final SampleRepo sampleRepo;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SpatialLocationRepo slRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final HmdmcRepo hmdmcRepo;
    private final UserRepo userRepo;
    private final LabwareTypeRepo labwareTypeRepo;
    private final LabwareRepo labwareRepo;
    private final SlotRepo slotRepo;
    private final BioStateRepo bioStateRepo;


    @Autowired
    public TestPlanActionRepo(EntityCreator entityCreator,
                              PlanOperationRepo planOpRepo, PlanActionRepo planActionRepo, OperationTypeRepo opTypeRepo,
                              SampleRepo sampleRepo, DonorRepo donorRepo, TissueRepo tissueRepo,
                              SpatialLocationRepo slRepo, MediumRepo mediumRepo,
                              FixativeRepo fixativeRepo, HmdmcRepo hmdmcRepo, UserRepo userRepo,
                              LabwareTypeRepo labwareTypeRepo, LabwareRepo labwareRepo, SlotRepo slotRepo, BioStateRepo bioStateRepo) {
        this.entityCreator = entityCreator;
        this.planOpRepo = planOpRepo;
        this.planActionRepo = planActionRepo;
        this.opTypeRepo = opTypeRepo;
        this.sampleRepo = sampleRepo;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.slRepo = slRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.userRepo = userRepo;
        this.labwareTypeRepo = labwareTypeRepo;
        this.labwareRepo = labwareRepo;
        this.slotRepo = slotRepo;
        this.bioStateRepo = bioStateRepo;
    }

    @Test
    @Transactional
    public void testFindMaxPlannedSection() {
        assertThat(planActionRepo.findMaxPlannedSectionForTissueId(-1)).isEmpty();
        assertThat(planActionRepo.findMaxPlannedSectionFromSlotId(-1)).isEmpty();
        Donor donor = new Donor(null, "DONOR", LifeStage.adult, entityCreator.getHuman());
        donorRepo.save(donor);
        Tissue tissue = new Tissue(null, "TISSUE1", "1", any(slRepo), donor,
                any(mediumRepo), any(fixativeRepo), entityCreator.getTissueCellClass(), any(hmdmcRepo), null, null);
        BioState bioState = any(bioStateRepo);
        tissueRepo.save(tissue);
        final Sample sample = new Sample(null, 3, tissue, bioState);

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

        planActionRepo.save(new PlanAction(null, plan.getId(), slot1, slot1, sample, 3, null, null));
        planActionRepo.save(new PlanAction(null, plan.getId(), slot2, slot2, sample, 18, null, null));
        planActionRepo.save(new PlanAction(null, plan.getId(), slot3, slot3, sample, 4, null, null));
        assertThat(planActionRepo.findMaxPlannedSectionForTissueId(tissue.getId())).hasValue(18);
        assertThat(planActionRepo.findMaxPlannedSectionFromSlotId(slot1.getId())).hasValue(3);
        assertThat(planActionRepo.findMaxPlannedSectionFromSlotId(slot2.getId())).hasValue(18);
    }

    @Test
    @Transactional
    public void testFindAllByDestinationLabwareId() {
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"),null);
        Labware sourceLabware = entityCreator.createBlock("STAN-000", sample);
        Slot sourceSlot = sourceLabware.getFirstSlot();
        LabwareType lt = entityCreator.createLabwareType("2x2", 2, 2);
        Labware labware = entityCreator.createLabware("STAN-001", lt);
        User user = entityCreator.createUser("dr6");
        OperationType opType = entityCreator.createOpType("Paint", null);
        List<Slot> slots = labware.getSlots();
        PlanOperation plan = entityCreator.createPlan(opType, user, sourceSlot, slots.get(0), sourceSlot, slots.get(1));
        List<PlanAction> actual = planActionRepo.findAllByDestinationLabwareId(labware.getId());
        assertThat(actual).isNotEmpty();
        assertThat(actual).hasSameElementsAs(plan.getPlanActions());

        assertThat(planActionRepo.findAllByDestinationLabwareId(-1)).isEmpty();
    }

    private <T> T any(CrudRepository<T, ?> repo) {
        return repo.findAll().iterator().next();
    }
}
