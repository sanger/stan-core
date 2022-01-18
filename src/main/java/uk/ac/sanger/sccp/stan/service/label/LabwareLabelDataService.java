package uk.ac.sanger.sccp.stan.service.label;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        return toLabelData(labware, content, mediums);
    }

    public LabwareLabelData toLabelData(Labware labware, List<LabelContent> content, Set<String> mediums) {
        String medium = (mediums.size()==1 ? mediums.iterator().next() : null);
        LocalDateTime created = labware.getCreated();
        if (created==null) {
            created = LocalDateTime.now();
        }
        String dateString = created.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return new LabwareLabelData(labware.getBarcode(), medium, dateString, content);
    }

    /**
     * Label data where we list the tissue for the top two rows and bottom two rows separately,
     * and we specify sample numbers for each of the eight positions.
     * @param labware the labware the label describes
     * @return label data describing the labware
     */
    public LabwareLabelData getDividedLabelData(Labware labware) {
        // 1. Check labware type is suitable.
        // 2. Load simple contents into a map.
        // 3. Check contents are suitable.
        // 4. Convert to correct number of label contents.
        LabwareType lt = labware.getLabwareType();
        if (lt.getNumRows()!=4) {
            throw new IllegalArgumentException("The specified label template is only suitable for labware with 4 rows.");
        }
        int numCols = lt.getNumColumns();

        Map<Address, List<SimpleContent>> map = addressToSimpleContent(labware);
        if (map.isEmpty()) {
            return toLabelData(labware, List.of(), Set.of());
        }

        Tissue[] tissues = checkDividedLayout(labware, map);

        String[] donorNames = new String[2];
        String[] tissueDescs = new String[2];
        String[] reps = new String[2];
        Set<String> mediums = new HashSet<>(2);

        for (int i = 0; i < tissues.length; i++) {
            Tissue tissue = tissues[i];
            if (tissue != null) {
                donorNames[i] = tissue.getDonor().getDonorName();
                tissueDescs[i] = getTissueDesc(tissue);
                reps[i] = tissue.getReplicate();
                mediums.add(tissue.getMedium().getName());
            }
        }

        List<LabelContent> content = new ArrayList<>(2*numCols);

        for (int row = 1; row <= 4; ++row) {
            int tissueIndex = (row-1)/2;
            for (int col = 1; col <= numCols; ++col) {
                if (tissues[tissueIndex]==null) {
                    content.add(new LabelContent(null, null, null));
                } else {
                    Integer[] sectionRange = sectionRange(map.get(new Address(row, col)));
                    content.add(new LabelContent(donorNames[tissueIndex], tissueDescs[tissueIndex], reps[tissueIndex],
                            sectionRange[0], sectionRange[1]));
                }
            }
        }
        return toLabelData(labware, content, mediums);
    }

    public Tissue[] checkDividedLayout(Labware labware, Map<Address, List<SimpleContent>> map) {
        Tissue[] tissues = new Tissue[2];
        int regionRows = labware.getLabwareType().getNumRows() / 2;
        for (var entry : map.entrySet()) {
            int row = entry.getKey().getRow();
            int tissueIndex = (row-1) / regionRows;
            for (SimpleContent sc : entry.getValue()) {
                Tissue tissue = sc.tissue;
                if (tissues[tissueIndex]==null) {
                    tissues[tissueIndex] = tissue;
                } else if (!tissues[tissueIndex].equals(tissue)) {
                    throw new IllegalArgumentException("The specified label template is only suitable for " +
                            "labware which has one tissue in the top half and one tissue in the bottom half.");
                }
            }
        }
        return tissues;
    }

    public Integer[] sectionRange(List<SimpleContent> scs) {
        if (scs==null || scs.isEmpty()) {
            return new Integer[] { null, null };
        }
        Integer minSection = null, maxSection = null;
        for (SimpleContent sc : scs) {
            if (sc.section==null) {
                continue;
            }
            if (minSection==null || sc.section < minSection) {
                minSection = sc.section;
            }
            if (maxSection==null || sc.section > maxSection) {
                maxSection = sc.section;
            }
        }
        return new Integer[] { minSection, maxSection };
    }

    public Map<Address, List<SimpleContent>> addressToSimpleContent(Labware labware) {
        Map<Address, List<SimpleContent>> map = new HashMap<>(labware.getSlots().size());
        for (Slot slot : labware.getSlots()) {
            if (!slot.getSamples().isEmpty()) {
                map.put(slot.getAddress(), slot.getSamples().stream().map(SimpleContent::new).collect(toList()));
            }
        }
        if (map.isEmpty()) {
            List<PlanAction> planActions = planActionRepo.findAllByDestinationLabwareId(labware.getId());
            if (planActions.isEmpty()) {
                return map;
            }
            for (PlanAction pa : planActions) {
                Slot slot = pa.getDestination();
                if (slot.getLabwareId().equals(labware.getId())) {
                    Sample sample = pa.getSample();
                    Integer section = pa.getNewSection();
                    if (section==null) {
                        section = sample.getSection();
                    }
                    SimpleContent sc = new SimpleContent(pa.getSample().getTissue(), section);
                    List<SimpleContent> scs = map.computeIfAbsent(slot.getAddress(), k -> new ArrayList<>());
                    if (!scs.contains(sc)) {
                        scs.add(sc);
                    }
                }
            }
        }
        return map;
    }

    public LabelContent getContent(Sample sample) {
        Tissue tissue = sample.getTissue();
        final String stateDesc = sample.getBioState().getName();
        if (stateDesc.equalsIgnoreCase("Tissue")) {
            return new LabelContent(tissue.getDonor().getDonorName(),
                    getTissueDesc(tissue), tissue.getReplicate(), sample.getSection());
        }
        return new LabelContent(tissue.getDonor().getDonorName(),
                getTissueDesc(tissue), tissue.getReplicate(), stateDesc);
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
        BioState bs = planAction.getNewBioState();
        if (bs==null) {
            bs = planAction.getSample().getBioState();
        }
        if (bs.getName().equalsIgnoreCase("Tissue")) {
            Integer section = planAction.getNewSection();
            if (section == null) {
                section = planAction.getSample().getSection();
            }

            return new LabelContent(
                    planAction.getSample().getTissue().getDonor().getDonorName(),
                    getTissueDesc(planAction.getSample().getTissue()),
                    planAction.getSample().getTissue().getReplicate(),
                    section
            );
        }
        return new LabelContent(
                planAction.getSample().getTissue().getDonor().getDonorName(),
                getTissueDesc(planAction.getSample().getTissue()),
                planAction.getSample().getTissue().getReplicate(),
                bs.getName()
        );
    }

    static class SimpleContent {
        Tissue tissue;
        Integer section;

        public SimpleContent(Tissue tissue, Integer section) {
            this.tissue = tissue;
            this.section = section;
        }

        public SimpleContent(Sample sample) {
            this(sample.getTissue(), sample.getSection());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleContent that = (SimpleContent) o;
            return (Objects.equals(this.tissue, that.tissue)
                    && Objects.equals(this.section, that.section));
        }

        @Override
        public int hashCode() {
            return Objects.hash(tissue, section);
        }
    }
}
