package uk.ac.sanger.sccp.stan.service.label;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class LabwareLabelDataService {
    private final PlanActionRepo planActionRepo;

    @Autowired
    public LabwareLabelDataService(PlanActionRepo planActionRepo) {
        this.planActionRepo = planActionRepo;
    }

    public LabwareLabelData getLabelData(Labware labware) {
        List<LabelContent> content = labware.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(this::getContent)
                .collect(toList());
        Set<String> mediums = labware.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(sample -> sample.getTissue().getMedium().getName())
                .collect(toSet());
        if (content.isEmpty()) {
            List<PlanAction> planActions = planActionRepo.findAllByDestinationLabwareId(labware.getId());
            if (!planActions.isEmpty()) {
                mediums = planActions.stream()
                        .map(pa -> pa.getSample().getTissue().getMedium().getName())
                        .collect(toSet());
                content = getPlannedContent(planActions);
            }
        }
        String medium = (mediums.size()==1 ? mediums.iterator().next() : null);
        return new LabwareLabelData(labware.getBarcode(), medium, content);
    }

    public LabelContent getContent(Sample sample) {
        Tissue tissue = sample.getTissue();
        return new LabelContent(tissue.getDonor().getDonorName(),
                getTissueDesc(tissue), tissue.getReplicate(), sample.getSection());
    }

    public String getTissueDesc(Tissue tissue) {
        SpatialLocation sl = tissue.getSpatialLocation();
        return prefix(tissue.getDonor().getLifeStage()) + sl.getTissueType().getCode() + "-" + sl.getCode();
    }

    public String prefix(LifeStage lifeStage) {
        switch (lifeStage) {
            case fetal: return "F";
            case paediatric: return "P";
            default: return "";
        }
    }

    public List<LabelContent> getPlannedContent(List<PlanAction> planActions) {
        return planActions.stream()
                .sorted(Comparator.comparing((PlanAction ac) -> ac.getDestination().getAddress())
                        .thenComparing(PlanAction::getId))
                .map(this::getContent)
                .collect(toList());
    }

    public LabelContent getContent(PlanAction planAction) {
        Integer section = planAction.getNewSection();
        if (section==null) {
            section = planAction.getSample().getSection();
        }
        return new LabelContent(
                planAction.getSample().getTissue().getDonor().getDonorName(),
                getTissueDesc(planAction.getSample().getTissue()),
                planAction.getSample().getTissue().getReplicate(),
                section
        );
    }
}
