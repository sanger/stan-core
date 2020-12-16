package uk.ac.sanger.sccp.stan.service.label;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link LabwareLabelDataService}
 * @author dr6
 */
public class TestLabwareLabelDataService {
    private PlanActionRepo mockPlanActionRepo;
    private LabwareLabelDataService service;

    @BeforeEach
    void setup() {
        mockPlanActionRepo = mock(PlanActionRepo.class);
        service = new LabwareLabelDataService(mockPlanActionRepo);
    }

    @Test
    public void testLabwareData() {
        TissueType ttype = new TissueType(null, "Skellington", "SKE");
        SpatialLocation sl = new SpatialLocation(null, "SL4", 4, ttype);
        Tissue tissue = EntityFactory.makeTissue(EntityFactory.getDonor(), sl);
        Sample sample1 = new Sample(null, null, tissue);
        Sample sample2 = new Sample(null, 5, tissue);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1, 2));
        lw.getSlots().get(1).getSamples().addAll(List.of(sample1, sample2));

        LabwareLabelData actual = service.getLabelData(lw);

        List<LabelContent> expectedContents = Stream.of(sample1, sample2)
                .map(sam -> new LabelContent(sam.getTissue().getDonor().getDonorName(),
                        tissueString(sam.getTissue()), sam.getTissue().getReplicate(), sam.getSection()))
                .collect(toList());
        LabwareLabelData expected = new LabwareLabelData(lw.getBarcode(), tissue.getMedium().getName(), expectedContents);
        assertEquals(expected, actual);

        Labware emptyLabware = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        when(mockPlanActionRepo.findAllByDestinationLabwareId(emptyLabware.getId())).thenReturn(List.of());
        assertEquals(new LabwareLabelData(emptyLabware.getBarcode(), null, List.of()), service.getLabelData(emptyLabware));
    }

    @Test
    public void testLabwareDataPlannedContents() {
        Donor donor1 = new Donor(null, "DONOR1", LifeStage.adult);
        Donor donor2 = new Donor(null, "DONOR2", LifeStage.fetal);
        TissueType ttype1 = new TissueType(null, "Skellington", "SKE");
        SpatialLocation sl1 = new SpatialLocation(null, "SL4", 4, ttype1);
        TissueType ttype2 = new TissueType(null, "Bananas", "BNN");
        SpatialLocation sl2 = new SpatialLocation(null, "SL7", 7, ttype2);
        Tissue tissue1 = EntityFactory.makeTissue(donor1, sl1);
        Tissue tissue2 = EntityFactory.makeTissue(donor2, sl2);
        Sample sample1 = new Sample(null, null, tissue1);
        Sample sample2 = new Sample(null, 5, tissue2);
        Labware labware = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1, 4));
        List<Slot> slots = labware.getSlots();
        PlanOperation plan = new PlanOperation();
        final int planId = 400;
        plan.setId(planId);
        List<PlanAction> planActions = List.of(
                new PlanAction(404, planId, slots.get(3), slots.get(3), sample2, 14, null),
                new PlanAction(403, planId, slots.get(2), slots.get(2), sample2, null, null),
                new PlanAction(401, planId, slots.get(0), slots.get(0), sample1, null, null),
                new PlanAction(402, planId, slots.get(1), slots.get(1), sample1, 7, null)
        );
        when(mockPlanActionRepo.findAllByDestinationLabwareId(labware.getId())).thenReturn(planActions);

        LabwareLabelData actual = service.getLabelData(labware);
        List<LabelContent> expectedContents = List.of(
                new LabelContent(donor1.getDonorName(), tissueString(tissue1), tissue1.getReplicate(), null),
                new LabelContent(donor1.getDonorName(), tissueString(tissue1), tissue1.getReplicate(), 7),
                new LabelContent(donor2.getDonorName(), tissueString(tissue2), tissue2.getReplicate(), 5),
                new LabelContent(donor2.getDonorName(), tissueString(tissue2), tissue2.getReplicate(), 14)
        );
        assertEquals(new LabwareLabelData(labware.getBarcode(), tissue1.getMedium().getName(), expectedContents), actual);
    }

    @ParameterizedTest
    @EnumSource(LifeStage.class)
    public void testGetTissueDesc(LifeStage lifeStage) {
        Donor donor = new Donor(null, "DONOR", lifeStage);
        TissueType tt = new TissueType(null, "Xylophone", "XYL");
        SpatialLocation sl = new SpatialLocation(null, "SL-6", 6, tt);
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        assertEquals(tissueString(tissue), service.getTissueDesc(tissue));
    }

    private String tissueString(Tissue tissue) {
        String prefix;
        switch (tissue.getDonor().getLifeStage()) {
            case paediatric:
                prefix = "P";
                break;
            case fetal:
                prefix = "F";
                break;
            default:
                prefix = "";
                break;
        }
        return prefix + tissue.getTissueType().getCode() + "-" + tissue.getSpatialLocation().getCode();
    }

}
