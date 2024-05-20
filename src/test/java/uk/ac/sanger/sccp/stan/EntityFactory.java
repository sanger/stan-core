package uk.ac.sanger.sccp.stan;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Create entity objects for tests
 * @author dr6
 */
public class EntityFactory {
    private static User user;
    private static TissueType tissueType;
    private static SpatialLocation spatialLocation;
    private static BioState bioState;
    private static LabelType labelType;
    private static LabwareType tubeType;
    private static Donor donor;
    private static Tissue tissue;
    private static Sample sample;
    private static Labware tube;
    private static Hmdmc hmdmc;
    private static Medium medium;
    private static Fixative fixative;
    private static Printer printer;
    private static Species human;
    private static SlotRegion slotRegion;

    private static int idCounter = 10_000;

    public static User getUser() {
        if (user==null) {
            user = new User(10, "user", User.Role.admin);
        }
        return user;
    }

    public static TissueType getTissueType() {
        if (tissueType==null) {
            tissueType = new TissueType(20, "Arm", "ARM");
        }
        return tissueType;
    }

    public static BioState getBioState() {
        if (bioState==null) {
            bioState = new BioState(30, "Tissue");
        }
        return bioState;
    }

    public static Species getHuman() {
        if (human==null) {
            human = new Species(1, "Human");
        }
        return human;
    }

    public static SlotRegion getSlotRegion() {
        if (slotRegion==null) {
            slotRegion = new SlotRegion(55, "Middle");
        }
        return slotRegion;
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
            tubeType = new LabwareType(50, "Tube", 1, 1, getLabelType(), false);
        }
        return tubeType;
    }

    public static LabwareType makeLabwareType(int numRows, int numColumns, String name) {
        return new LabwareType(++idCounter, name, numRows, numColumns, getLabelType(), false);
    }

    public static LabwareType makeLabwareType(int numRows, int numColumns) {
        return makeLabwareType(numRows, numColumns, numRows+"x"+numColumns);
    }

    public static Hmdmc getHmdmc() {
        if (hmdmc==null) {
            hmdmc = new Hmdmc(60, "20/000");
        }
        return hmdmc;
    }

    public static Donor getDonor() {
        if (donor==null) {
            donor = new Donor(70, "dirk", LifeStage.adult, getHuman());
        }
        return donor;
    }

    public static Tissue getTissue() {
        if (tissue==null) {
            tissue = new Tissue(80, "TISSUE1", "1", getSpatialLocation(), getDonor(),
                    getMedium(), getFixative(), getHmdmc(), null, null);
        }
        return tissue;
    }

    public static Sample getSample() {
        if (sample==null) {
            sample = new Sample(90, 1, getTissue(), getBioState());
        }
        return sample;
    }

    public static Sample[] makeSamples(int n) {
        final Tissue tissue = getTissue();
        final BioState bs = getBioState();
        return IntStream.range(0, n)
                .mapToObj(i -> new Sample(++idCounter, null, tissue, bs))
                .toArray(Sample[]::new);
    }

    public static Medium getMedium() {
        if (medium==null) {
            medium = new Medium(160, "Butter");
        }
        return medium;
    }

    public static Fixative getFixative() {
        if (fixative==null) {
            fixative = new Fixative(170, "Formalin");
        }
        return fixative;
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

    public static Printer getPrinter() {
        if (printer==null) {
            int id = ++idCounter;
            printer = new Printer(id, "printer"+id, List.of(getLabelType()), Printer.Service.sprint);
        }
        return printer;
    }

    public static Labware makeEmptyLabware(LabwareType lt) {
        return makeEmptyLabware(lt, null);
    }

    public static Labware makeEmptyLabware(LabwareType lt, String barcode) {
        int lwId = ++idCounter;
        final int[] slotId = { 10*lwId };
        List<Slot> slots = Address.stream(lt.getNumRows(), lt.getNumColumns())
                .map(ad -> new Slot(++slotId[0], lwId, ad, new ArrayList<>(), null, null))
                .collect(toList());
        if (barcode==null) {
            barcode = "STAN-"+lwId;
        }
        return new Labware(lwId, barcode, lt, slots);
    }

    public static Labware makeLabware(LabwareType lt, Sample... samples) {
        Labware lw = makeEmptyLabware(lt);
        if (samples.length > 0) {
            Iterator<Slot> slotIterator = lw.getSlots().iterator();
            for (Sample sample : samples) {
                Slot slot = slotIterator.next();
                if (sample!=null) {
                    slot.addSample(sample);
                }
            }
        }
        return lw;
    }

    public static Labware makeBlock(Sample sample) {
        Labware lw = makeLabware(getTubeType(), sample);
        Slot slot = lw.getFirstSlot();
        slot.setBlockSampleId(sample.getId());
        slot.setBlockHighestSection(0);
        return lw;
    }

    public static OperationType makeOperationType(String name, BioState newBioState, OperationTypeFlag... flags) {
        int flagbits = 0;
        for (OperationTypeFlag flag : flags) {
            flagbits |= flag.bit();
        }
        return new OperationType(++idCounter, name, flagbits, newBioState);
    }

    public static Tissue makeTissue(Donor donor, SpatialLocation sl) {
        int id = ++idCounter;
        return new Tissue(id, "TISSUE "+id, String.valueOf(id%7), sl, donor, getMedium(), getFixative(), getHmdmc(), null, null);
    }

    public static ReagentPlate makeReagentPlate(String barcode) {
        int plateId = ++idCounter;
        ReagentPlate rp = new ReagentPlate(barcode, ReagentPlate.TYPE_FFPE);
        rp.setId(plateId);
        ReagentPlateLayout rpLayout = rp.getPlateLayout();
        int[] slotId = {100*plateId};
        List<ReagentSlot> rslots = Address.stream(rpLayout.getNumRows(), rpLayout.getNumColumns())
                .map(ad -> new ReagentSlot(++slotId[0], plateId, ad, false))
                .collect(toList());
        rp.setSlots(rslots);
        return rp;
    }

    public static PlanOperation makePlanForLabware(OperationType opType, List<Labware> sources, List<Labware> destination) {
        return makePlanForLabware(opType, sources, destination, null);
    }

    public static PlanOperation makePlanForLabware(OperationType opType, List<Labware> sources, List<Labware> destinations, User user) {
        return makePlanForSlots(opType, toFirstSlots(sources), toFirstSlots(destinations), user);
    }

    private static <C, A> C makeOpLike(OperationType opType, List<Slot> sources, List<Slot> destinations, User user,
                                           OpLikeMaker<C, A> opLikeMaker, ActionLikeMaker<A> actionLikeMaker) {
        if (user == null) {
            user = getUser();
        }
        int opId = ++idCounter;
        BiFunction<Slot, Slot, A> slotAFunction = (source, dest) -> actionLikeMaker.make(++idCounter, opId, source, dest, source.getSamples().get(0));
        final List<A> actions;
        if (sources.size()==destinations.size()) {
            actions = IntStream.range(0, sources.size())
                    .mapToObj(i -> slotAFunction.apply(sources.get(i), destinations.get(i)))
                    .collect(toList());
        } else if (sources.size()==1) {
            final Slot src = sources.get(0);
            actions = destinations.stream()
                    .map(dest -> slotAFunction.apply(src, dest))
                    .collect(toList());
        } else if (destinations.size()==1) {
            final Slot dest = destinations.get(0);
            actions = sources.stream()
                    .map(src -> slotAFunction.apply(src, dest))
                    .collect(toList());
        } else {
            throw new IllegalArgumentException("Unclear how to construct actions from " + sources.size()
                    + " sources and " + destinations.size() + " destinations.");
        }
        return opLikeMaker.make(opId, opType, now(), actions, user);
    }

    public static PlanOperation makePlanForSlots(OperationType opType, List<Slot> sources, List<Slot> destinations, User user) {
        return makeOpLike(opType, sources, destinations, user, PlanOperation::new, PlanAction::new);
    }

    public static Operation makeOpForLabware(OperationType opType, List<Labware> sources, List<Labware> destinations) {
        return makeOpForLabware(opType, sources, destinations, null);
    }

    public static Operation makeOpForLabware(OperationType opType, List<Labware> sources, List<Labware> destinations, User user) {
        return makeOpForSlots(opType, toFirstSlots(sources), toFirstSlots(destinations), user);
    }

    public static Operation makeOpForSlots(OperationType opType, List<Slot> sources, List<Slot> destinations, User user) {
        return makeOpLike(opType, sources, destinations, user, Operation::new, EntityFactory::makeAction);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static Snapshot makeSnapshot(Labware lw) {
        Integer snapId = ++idCounter;
        final int[] elId = {100 * snapId};
        List<SnapshotElement> elements = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new SnapshotElement(++elId[0], snapId, slot.getId(), sam.getId())))
                .collect(toList());
        return new Snapshot(snapId, lw.getId(), elements);
    }

    public static Work makeWork(String workNumber) {
        Integer workId = ++idCounter;
        return new Work(workId, workNumber, new WorkType(5, "worktype"), null,
                new Project(6, "proj"), new Program(7, "prog"),
                new CostCode(8, "cc"), Work.Status.active);
    }

    public static Work[] makeWorks(String... workNumbers) {
        WorkType wt = new WorkType(5, "worktype");
        Project proj = new Project(6, "proj");
        Program prog = new Program(7, "prog");
        CostCode cc = new CostCode(8, "cc");
        Work.Status status = Work.Status.active;
        return Arrays.stream(workNumbers)
                .map(wn -> new Work(++idCounter, wn, wt, null, proj, prog, cc, status))
                .toArray(Work[]::new);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> objToList(Object obj) {
        if (obj==null) {
            return List.of();
        }
        if (obj instanceof List) {
            return (List<E>) obj;
        }
        return (List<E>) List.of(obj);
    }

    @SuppressWarnings("unchecked")
    public static <E> Collection<E> objToCollection(Object obj) {
        if (obj==null) {
            return List.of();
        }
        if (obj instanceof Collection) {
            return (Collection<E>) obj;
        }
        return (Collection<E>) List.of(obj);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> nullableObjToList(Object obj) {
        if (obj instanceof List) {
            return (List<E>) obj;
        }
        return (List<E>) Collections.singletonList(obj);
    }

    private static List<Slot> toFirstSlots(Collection<Labware> labware) {
        return labware.stream().map(Labware::getFirstSlot).collect(toList());
    }

    private static Action makeAction(int id, int opId, Slot source, Slot dest, Sample sample) {
        return new Action(id, opId, source, dest, sample, sample);
    }

    @FunctionalInterface
    private interface OpLikeMaker<C, A> {
        C make(int id, OperationType opType, LocalDateTime timestamp, List<A> actions, User user);
    }

    @FunctionalInterface
    private interface ActionLikeMaker<A> {
        A make(int id, int opId, Slot source, Slot dest, Sample sample);
    }
}
