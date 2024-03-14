package uk.ac.sanger.sccp.stan.service.label;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Service for creating LabwareLabelData from labware.
 * LabwareLabelData summarises the current or expected contents of labware
 * into fields suitable to be printed on a label.
 * @author dr6
 */
@Service
public class LabwareLabelDataService {
    private static final String FW_LONG = "Fetal waste", FW_SHORT = "F waste";

    private final PlanActionRepo planActionRepo;

    @Autowired
    public LabwareLabelDataService(PlanActionRepo planActionRepo) {
        this.planActionRepo = planActionRepo;
    }

    public LabwareLabelData getLabelData(Labware labware) {
        var slotOrder = slotOrderForLabwareType(labware.getLabwareType());
        List<LabelContent> content = labware.getSlots().stream()
                .sorted(slotOrder)
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
                content = getPlannedContent(planActions, slotOrder);
            }
        }
        if (labware.getLabwareType().showMediumAsStateOnLabel() && content.size()==1 && mediums.size()==1) {
            String mediumName = mediums.iterator().next();
            content = content.stream()
                    .map(d -> d.withStateDesc(mediumName))
                    .collect(toList());
        }
        return toLabelData(labware, content, mediums);
    }

    /**
     * What order should contents of slots be in?
     * @param lt the labware type
     * @return a comparator to sort the slots
     */
    public Comparator<Slot> slotOrderForLabwareType(LabwareType lt) {
        if (lt.columnMajorOrderOnLabel()) {
            return Comparator.comparing(Slot::getAddress, Address.COLUMN_MAJOR);
        }
        return Comparator.comparing(Slot::getAddress);
    }

    public LabwareLabelData toLabelData(Labware labware, List<LabelContent> content, Set<String> mediums) {
        String medium = (mediums.size()==1 ? mediums.iterator().next() : null);
        LocalDateTime created = labware.getCreated();
        if (created==null) {
            created = LocalDateTime.now();
        }
        String dateString = created.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return new LabwareLabelData(labware.getBarcode(), labware.getExternalBarcode(), medium, dateString, content);
    }

    /**
     * Label data where we list the tissue for each row.
     * @param labware the labware the label describes
     * @return label data describing the labware
     */
    public LabwareLabelData getRowBasedLabelData(Labware labware) {
        // 1. Load simple contents into a map.
        // 2. Check contents are suitable.
        // 3. Convert to correct number of label contents.
        Map<Address, List<SimpleContent>> map = addressToSimpleContent(labware);
        if (map.isEmpty()) {
            return toLabelData(labware, List.of(), Set.of());
        }
        Tissue[] tissues = checkRowBasedLayout(labware, map);

        final int numTissues = tissues.length;
        String[] donorNames = new String[numTissues];
        String[] tissueDescs = new String[numTissues];
        String[] reps = new String[numTissues];
        Set<String> mediums = new HashSet<>(numTissues);

        for (int i = 0; i < numTissues; i++) {
            Tissue tissue = tissues[i];
            if (tissue != null) {
                donorNames[i] = tissue.getDonor().getDonorName();
                tissueDescs[i] = getTissueDesc(tissue);
                reps[i] = tissue.getReplicate();
                mediums.add(tissue.getMedium().getName());
            }
        }

        List<LabelContent> content = new ArrayList<>(numTissues);
        final int numCols = labware.getLabwareType().getNumColumns();
        for (int i = 0; i < numTissues; ++i) {
            Tissue tissue = tissues[i];
            if (tissue==null) {
                content.add(new LabelContent(null, null, null));
            } else {
                final int row = i+1;
                Stream<SimpleContent> scStream =  IntStream.range(1, numCols+1)
                        .mapToObj(col -> map.get(new Address(row, col)))
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream);
                Integer[] sectionRange = sectionRange(scStream.iterator());
                content.add(new LabelContent(donorNames[i], tissueDescs[i], reps[i], sectionRange[0], sectionRange[1]));
            }
        }
        return toLabelData(labware, content, mediums);
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

    /**
     * Checks that the given labware contents are consistent with a row-based layout
     * (i.e. that each row contains at most one tissue).
     * Returns an array of tissues, corresponding to the rows in the labware
     * @param labware the labware
     * @param map the simple contents of the labware
     * @return the tissues in each row of the labware
     * @exception IllegalArgumentException if the layout is not consistent with a row-based layout
     */
    public Tissue[] checkRowBasedLayout(Labware labware, Map<Address, List<SimpleContent>> map) {
        Tissue[] tissues = new Tissue[labware.getLabwareType().getNumRows()];
        for (var entry : map.entrySet()) {
            int index = entry.getKey().getRow() - 1;
            for (SimpleContent sc : entry.getValue()) {
                Tissue tissue = sc.tissue;
                if (tissues[index]==null) {
                    tissues[index] = tissue;
                } else if (!tissues[index].equals(tissue)) {
                    throw new IllegalArgumentException("The specified label template is only suitable for " +
                            "labware which has one tissue per row.");
                }
            }
        }
        return tissues;
    }

    /**
     * Checks that the given labware contents are consistent with a divided layout
     * (i.e. that the top half and bottom half each contain at most one tissue).
     * Returns an array of tissues, representing the top and bottom half.
     * @param labware the labware
     * @param map the simple contents of the labware
     * @return the top and bottom tissues of the labware
     * @exception IllegalArgumentException if the layout is not consistent with a divided layout
     */
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

    /**
     * Gets the section range (min and max) from a sequence of SimpleContent.
     * Returns an array <code>{null,null}</code> if there are no sections, or if the given collection
     * is null or empty.
     * @param scs an iterator for SimpleContents
     * @return the min and max of sections in the given collection, in an array
     */
    public Integer[] sectionRange(Collection<SimpleContent> scs) {
        if (scs == null || scs.isEmpty()) {
            return new Integer[] { null, null };
        }
        return sectionRange(scs.iterator());
    }

    /**
     * Gets the section range (min and max) from a sequence of SimpleContent.
     * Returns an array <code>{null,null}</code> if there are no sections.
     * @param scs an iterator for SimpleContents
     * @return the min and max of sections in the given contents, in an array
     */
    public Integer[] sectionRange(Iterator<SimpleContent> scs) {
        Integer minSection = null, maxSection = null;
        while (scs.hasNext()) {
            SimpleContent sc = scs.next();
            if (sc.section != null) {
                if (minSection == null || sc.section < minSection) {
                    minSection = sc.section;
                }
                if (maxSection == null || sc.section > maxSection) {
                    maxSection = sc.section;
                }
            }
        }
        return new Integer[] { minSection, maxSection };

    }

    /**
     * Creates a map from each slot address in the labware to the content in that slot,
     * as a list of SimpleContent.
     * Addresses of empty slots may be omitted.
     * If the labware is empty, the planned actions are looked up to find the expected contents.
     * @param labware the labware to examine
     * @return a map from slot address to list of SimpleContent
     */
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
        String stateDesc = sample.getBioState().getName();
        if (stateDesc.equalsIgnoreCase("Tissue")) {
            return new LabelContent(tissue.getDonor().getDonorName(),
                    getTissueDesc(tissue), tissue.getReplicate(), sample.getSection());
        }
        if (stateDesc.equalsIgnoreCase("Original sample")) {
            stateDesc = "Original";
        } else if (stateDesc.equalsIgnoreCase(FW_LONG)) {
            stateDesc = FW_SHORT;
        }
        return new LabelContent(tissue.getDonor().getDonorName(),
                getTissueDesc(tissue), tissue.getReplicate(), stateDesc);
    }

    public String getTissueDesc(Tissue tissue) {
        SpatialLocation sl = tissue.getSpatialLocation();
        return prefix(tissue.getDonor().getLifeStage()) + sl.getTissueType().getCode() + "-" + sl.getCode();
    }

    public String prefix(LifeStage lifeStage) {
        if (lifeStage!=null) {
            return switch (lifeStage) {
                case fetal -> "F";
                case paediatric -> "P";
                default -> "";
            };
        }
        return "";
    }

    public List<LabelContent> getPlannedContent(List<PlanAction> planActions, Comparator<Slot> slotOrder) {
        return planActions.stream()
                .sorted(Comparator.comparing(PlanAction::getDestination, slotOrder)
                        .thenComparing(PlanAction::getId))
                .map(this::getContent)
                .collect(toList());
    }

    public LabelContent getContent(PlanAction planAction) {
        BioState bs = planAction.getNewBioState();
        if (bs==null) {
            bs = planAction.getSample().getBioState();
        }
        String stateDesc = bs.getName();
        if (stateDesc.equalsIgnoreCase("Tissue")) {
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
        if (stateDesc.equalsIgnoreCase(FW_LONG)) {
            stateDesc = FW_SHORT;
        }
        return new LabelContent(
                planAction.getSample().getTissue().getDonor().getDonorName(),
                getTissueDesc(planAction.getSample().getTissue()),
                planAction.getSample().getTissue().getReplicate(),
                stateDesc
        );
    }

    public record SimpleContent(Tissue tissue, Integer section) {
        public SimpleContent(Sample sample) {
            this(sample.getTissue(), sample.getSection());
        }
    }
}
