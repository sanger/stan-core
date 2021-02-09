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
    private MouldSizeRepo mouldSizeRepo;
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
    private EntityManager entityManager;

    public User createUser(String username) {
        return userRepo.save(new User(null, username));
    }

    public Donor createDonor(String donorName, LifeStage lifeStage) {
        return donorRepo.save(new Donor(null, donorName, lifeStage));
    }

    public Tissue createTissue(Donor donor, String externalName) {
        return tissueRepo.save(new Tissue(null, externalName, 1, getAny(slRepo), donor, getAny(mouldSizeRepo),
                getAny(mediumRepo), getAny(fixativeRepo), getAny(hmdmcRepo)));
    }

    public Sample createSample(Tissue tissue, Integer section) {
        return sampleRepo.save(new Sample(null, section, tissue, getAny(bioStateRepo)));
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

    public LabwareType createLabwareType(String name, int rows, int columns) {
        return ltRepo.save(new LabwareType(null, name, rows, columns, getAny(labelTypeRepo), false));
    }

    public OperationType createOpType(String opTypeName, OperationTypeFlag... opTypeFlags) {
        int flags = 0;
        for (OperationTypeFlag flag : opTypeFlags) {
            flags |= flag.bit();
        }
        return opTypeRepo.save(new OperationType(null, opTypeName, flags));
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

    public Printer createPrinter(String name, LabelType labelType) {
        Printer printer = new Printer(null, name, labelType, Printer.Service.sprint);
        return printerRepo.save(printer);
    }

    public Printer createPrinter(String name) {
        return createPrinter(name, getAny(labelTypeRepo));
    }

    public ReleaseRecipient createReleaseRecipient(String username) {
        ReleaseRecipient rec = new ReleaseRecipient(null, username);
        return releaseRecipientRepo.save(rec);
    }

    public ReleaseDestination createReleaseDestination(String name) {
        ReleaseDestination dest = new ReleaseDestination(null, name);
        return releaseDestinationRepo.save(dest);
    }

    public <E> E getAny(CrudRepository<E, ?> repo) {
        return repo.findAll().iterator().next();
    }
}
