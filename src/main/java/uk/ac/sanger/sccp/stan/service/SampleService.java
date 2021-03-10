package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.stan.model.Slot;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;

/**
 * @author dr6
 */
@Service
public class SampleService {
    private final PlanActionRepo planActionRepo;
    private final SlotRepo slotRepo;

    @Autowired
    public SampleService(PlanActionRepo planActionRepo, SlotRepo slotRepo) {
        this.planActionRepo = planActionRepo;
        this.slotRepo = slotRepo;
    }

    /**
     * Figures out the next section number from the given block, and increments its section counter.
     * @param  block a slot containing a block
     * @return the next section number for the given block
     */
    public int nextSection(Slot block) {
        assert block.isBlock();
        assert block.getSamples().size()==1;
        final Sample sample = block.getSamples().get(0);
        Integer listedMaxSection = block.getBlockHighestSection();
        int maxSection = (listedMaxSection==null ? 0 : listedMaxSection);
        int maxPlanSection = planActionRepo.findMaxPlannedSectionFromSlotId(block.getId()).orElse(0);
        int nextSection = Math.max(maxSection, maxPlanSection) + 1;
        block.setBlockHighestSection(nextSection);
        block.setBlockSampleId(sample.getId());
        slotRepo.save(block);
        return nextSection;
    }
}
