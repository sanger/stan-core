package uk.ac.sanger.sccp.stan;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Create entity objects for tests
 * @author dr6
 */
public class EntityFactory {
    private static User user;
    private static TissueType tissueType;
    private static SpatialLocation spatialLocation;
    private static LabelType labelType;
    private static LabwareType tubeType;
    private static Donor donor;
    private static Tissue tissue;
    private static Sample sample;
    private static Labware tube;
    private static Hmdmc hmdmc;
    private static MouldSize mouldSize;
    private static Medium medium;
    private static int idCounter = 10_000;

    public static User getUser() {
        if (user==null) {
            user = new User(10, "user");
        }
        return user;
    }

    public static TissueType getTissueType() {
        if (tissueType==null) {
            tissueType = new TissueType(20, "Arm", "ARM");
        }
        return tissueType;
    }

    public static SpatialLocation getSpatialLocation() {
        if (spatialLocation==null) {
            spatialLocation = new SpatialLocation(30, "Unknown", 0, getTissueType());
        }
        return spatialLocation;
    }

    public static LabelType getLabelType() {
        if (labelType==null) {
            labelType = new LabelType(40, "Thin");
        }
        return labelType;
    }

    public static LabwareType getTubeType() {
        if (tubeType==null) {
            tubeType = new LabwareType(50, "Tube", 1, 1, getLabelType());
        }
        return tubeType;
    }

    public static LabwareType makeLabwareType(int numRows, int numColumns) {
        return new LabwareType(++idCounter, numRows+"x"+numColumns, numRows, numColumns, getLabelType());
    }

    public static Hmdmc getHmdmc() {
        if (hmdmc==null) {
            hmdmc = new Hmdmc(60, "20/000");
        }
        return hmdmc;
    }

    public static Donor getDonor() {
        if (donor==null) {
            donor = new Donor(70, "dirk", LifeStage.adult);
        }
        return donor;
    }

    public static Tissue getTissue() {
        if (tissue==null) {
            tissue = new Tissue(80, "TISSUE1", 1, getSpatialLocation(), getDonor(),
                    null, null, getHmdmc());
        }
        return tissue;
    }

    public static Sample getSample() {
        if (sample==null) {
            sample = new Sample(90, 1, getTissue());
        }
        return sample;
    }

    public static MouldSize getMouldSize() {
        if (mouldSize==null) {
            mouldSize = new MouldSize(150, "Minimould");
        }
        return mouldSize;
    }

    public static Medium getMedium() {
        if (medium==null) {
            medium = new Medium(160, "Butter");
        }
        return medium;
    }

    public static Labware getTube() {
        if (tube==null) {
            int lwId = 100;
            int slotId = 1001;
            Slot slot = new Slot(slotId, lwId, new Address(1,1), new ArrayList<>(List.of(getSample())),
                    null, null);
            tube = new Labware(lwId, "STAN-00"+lwId, getTubeType(), new ArrayList<>(List.of(slot)));
        }
        return tube;
    }

    public static Labware makeEmptyLabware(LabwareType lt) {
        int lwId = ++idCounter;
        final int[] slotId = { 10*lwId };
        List<Slot> slots = Address.stream(lt.getNumRows(), lt.getNumColumns())
                .map(ad -> new Slot(++slotId[0], lwId, ad, new ArrayList<>(), null, null))
                .collect(toList());
        return new Labware(lwId, "STAN-"+lwId, lt, slots);
    }
}
