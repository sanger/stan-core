package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for creating persisted entities for tests
 * @author dr6
 */
@TestComponent
public class EntityCreator {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private DonorRepo donorRepo;
    @Autowired
    private TissueRepo tissueRepo;
    @Autowired
    private SpatialLocationRepo slRepo;
    @Autowired
    private MediumRepo mediumRepo;
    @Autowired
    private FixativeRepo fixativeRepo;
    @Autowired
    private HmdmcRepo hmdmcRepo;
    @Autowired
    private SampleRepo sampleRepo;
    @Autowired
    private LabwareRepo labwareRepo;
    @Autowired
    private LabwareTypeRepo ltRepo;
    @Autowired
    private SlotRepo slotRepo;
    @Autowired
    private PlanOperationRepo planOpRepo;
    @Autowired
    private PlanActionRepo planActionRepo;
    @Autowired
    private OperationTypeRepo opTypeRepo;
    @Autowired
    private LabelTypeRepo labelTypeRepo;
    @Autowired
    private PrinterRepo printerRepo;
    @Autowired
    private ReleaseDestinationRepo releaseDestinationRepo;
    @Autowired
    private ReleaseRecipientRepo releaseRecipientRepo;
    @Autowired
    private BioStateRepo bioStateRepo;
    @Autowired
    private SnapshotRepo snapshotRepo;
    @Autowired
    private SnapshotElementRepo snapshotElementRepo;
    @Autowired
    private SpeciesRepo speciesRepo;
    @Autowired
    private ProjectRepo projectRepo;
    @Autowired
    private ProgramRepo programRepo;
    @Autowired
    private CostCodeRepo costCodeRepo;
    @Autowired
    private CellClassRepo cellClassRepo;
    @Autowired
    private WorkTypeRepo workTypeRepo;
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private BioRiskRepo bioRiskRepo;

    @Autowired
    private EntityManager entityManager;

    public User createUser(String username, User.Role role) {
        return userRepo.save(new User(null, username, role));
    }

    public User createUser(String username) {
        return createUser(username, User.Role.admin);
    }

    public Donor createDonor(String donorName) {
        return createDonor(donorName, LifeStage.adult, getHuman());
    }

    public Donor createDonor(String donorName, LifeStage lifeStage, Species species) {
        return donorRepo.save(new Donor(null, donorName, lifeStage, species));
    }

    public Tissue createTissue(Donor donor, String externalName) {
        return createTissue(donor, externalName, "1");
    }

    public Tissue createTissue(Donor donor, String externalName, String replicate) {
        return createTissue(donor, null, externalName, replicate);
    }

    public Tissue createTissue(Donor donor, SpatialLocation sl, String externalName, String replicate) {
        if (donor==null) {
            donor = createDonor("DONOR1");
        }
        if (sl==null) {
            sl = getAny(slRepo);
        }
        return tissueRepo.save(new Tissue(null, externalName, replicate, sl, donor,
                getAny(mediumRepo), getAny(fixativeRepo), getTissueCellClass(), getAny(hmdmcRepo), null, null));

    }

    public Sample createSample(Tissue tissue, Integer section) {
        BioState bs = bioStateRepo.getByName("Tissue");
        return createSample(tissue, section, bs);
    }

    public Sample createSample(Tissue tissue, Integer section, BioState bioState) {
        if (tissue==null) {
            tissue = createTissue(null, "EXT1");
        }
        if (bioState==null) {
            bioState = bioStateRepo.getByName("Tissue");
        }
        return sampleRepo.save(new Sample(null, section, tissue, bioState));
    }

    public Labware createTube(String barcode) {
        LabwareType lt = ltRepo.getByName("Tube");
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Slot slot = slotRepo.save(new Slot(null, lw.getId(), new Address(1,1), new ArrayList<>(), null, null));
        lw.getSlots().add(slot);
        return lw;
    }

    public Labware createBlock(String barcode, Sample sample) {
        LabwareType lt = ltRepo.getByName("Proviasette");
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Slot slot = slotRepo.save(new Slot(null, lw.getId(), new Address(1,1), new ArrayList<>(List.of(sample)), sample.getId(), 0));
        lw.getSlots().add(slot);
        return lw;
    }

    public Labware createLabware(String barcode, LabwareType lt, Sample... samples) {
        if (samples.length > 1 && lt.getNumRows()==1 && lt.getNumColumns()==1) {
            return createLabware(barcode, lt, new Sample[][] { samples });
        }
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Iterator<Sample> sampleIter = Arrays.asList(samples).iterator();
        List<Slot> slots = Address.stream(lt.getNumRows(), lt.getNumColumns())
                .map(ad -> {
                    Sample sample = sampleIter.hasNext() ? sampleIter.next() : null;
                    return new Slot(null, lw.getId(), ad, (sample==null ? List.of() : List.of(sample)),
                            null, null);
                })
                .collect(Collectors.toList());
        slotRepo.saveAll(slots);
        entityManager.refresh(lw);
        return lw;
    }

    public Labware createLabware(String barcode, LabwareType lt, Sample[][] samples) {
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Iterator<Sample[]> sampleArrayIter = Arrays.asList(samples).iterator();
        List<Slot> slots = Address.stream(lt.getNumRows(), lt.getNumColumns())
                .map(ad -> {
                    Sample[] sams = sampleArrayIter.hasNext() ? sampleArrayIter.next() : null;
                    List<Sample> samList = (sams==null ? List.of() : Arrays.asList(sams));
                    return new Slot(null, lw.getId(), ad, samList, null, null);
                })
                .collect(Collectors.toList());
        slotRepo.saveAll(slots);
        entityManager.refresh(lw);
        return lw;
    }

    public LabwareType createLabwareType(String name, int rows, int columns) {
        return ltRepo.save(new LabwareType(null, name, rows, columns, getAny(labelTypeRepo), false));
    }

    public BioState createBioState(String name) {
        return bioStateRepo.save(new BioState(null, name));
    }

    public Snapshot createSnapshot(Labware lw) {
        Snapshot snap = snapshotRepo.save(new Snapshot(lw.getId()));
        Iterable<SnapshotElement> elements = snapshotElementRepo.saveAll(lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream().map(sam ->
                    new SnapshotElement(null, snap.getId(), slot.getId(), sam.getId())
                )).collect(Collectors.toList()));
        snap.setElements(elements);
        return snap;
    }

    public OperationType createOpType(String opTypeName, BioState newBioState, OperationTypeFlag... opTypeFlags) {
        int flags = 0;
        for (OperationTypeFlag flag : opTypeFlags) {
            flags |= flag.bit();
        }
        return opTypeRepo.save(new OperationType(null, opTypeName, flags, newBioState));
    }

    public PlanOperation createPlan(OperationType opType, User user, Slot... slots) {
        PlanOperation plan = new PlanOperation(null, opType, null, null, user);
        plan = planOpRepo.save(plan);
        int planId = plan.getId();
        List<PlanAction> planActions = new ArrayList<>(slots.length/2);
        for (int i = 0; i < slots.length; i += 2) {
            Slot src = slots[i];
            Slot dest = slots[i+1];
            PlanAction planAction = new PlanAction(null, planId, src, dest, src.getSamples().get(0), null, null, null);
            planActions.add(planAction);
        }
        planActionRepo.saveAll(planActions);
        entityManager.refresh(plan);
        return plan;
    }

    public Project createProject(String name) {
        return projectRepo.save(new Project(null, name));
    }

    public Program createProgram(String name) {
        return programRepo.save(new Program(name));
    }

    public CostCode createCostCode(String code) {
        return costCodeRepo.save(new CostCode(null, code));
    }

    public WorkType createWorkType(String name) {
        return workTypeRepo.save(new WorkType(null, name));
    }

    public Work createWork(WorkType workType, Project project, Program program, CostCode cc, ReleaseRecipient workRequester) {
        if (project==null) {
            project = createProject("Stargate");
        }
        if (program==null) {
            program = createProgram("Hello");
        }
        if (cc==null) {
            cc = createCostCode("S400");
        }
        if (workType ==null) {
            workType = createWorkType("Drywalling");
        }
        String workNumber = workRepo.createNumber("SGP");
        return workRepo.save(new Work(null, workNumber, workType, workRequester, project, program, cc, Work.Status.active));
    }

    public Work createWorkLike(Work otherWork) {
        return createWork(otherWork.getWorkType(), otherWork.getProject(), otherWork.getProgram(), otherWork.getCostCode(), otherWork.getWorkRequester());
    }

    public Operation simpleOp(OperationType opType, User user, Labware source, Labware dest) {
        Slot slot0 = source.getFirstSlot();
        Slot slot1 = dest.getFirstSlot();
        Sample sam0 = slot0.getSamples().get(0);
        Sample sam1 = slot1.getSamples().get(0);
        Operation op = opRepo.save(new Operation(null, opType, null, List.of(), user));
        Action action = actionRepo.save(new Action(null, op.getId(), slot0, slot1, sam1, sam0));
        op.setActions(List.of(action));
        return op;
    }

    public Printer createPrinter(String name, LabelType labelType) {
        Printer printer = new Printer(null, name, List.of(labelType), Printer.Service.sprint);
        return printerRepo.save(printer);
    }

    public Printer createPrinter(String name) {
        return createPrinter(name, getAny(labelTypeRepo));
    }

    public ReleaseRecipient createReleaseRecipient(String username) {
        ReleaseRecipient rec = new ReleaseRecipient(null, username, null);
        return releaseRecipientRepo.save(rec);
    }

    public ReleaseDestination createReleaseDestination(String name) {
        ReleaseDestination dest = new ReleaseDestination(null, name);
        return releaseDestinationRepo.save(dest);
    }

    public BioRisk createBioRisk(String code) {
        BioRisk br = new BioRisk(code);
        return bioRiskRepo.save(br);
    }

    public CellClass getTissueCellClass() {
        return cellClassRepo.getByName("Tissue");
    }

    public BioState anyBioState() {
        return getAny(bioStateRepo);
    }

    public Species getHuman() {
        return speciesRepo.findByName("Human").orElseThrow();
    }

    public <E> E getAny(CrudRepository<E, ?> repo) {
        return repo.findAll().iterator().next();
    }

    public LabwareType getTubeType() {
        return ltRepo.getByName("Tube");
    }
}
