package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.iter;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Register service for {@link BlockRegisterRequest}
 * @author dr6
 */
@Service
public class BlockRegisterServiceImp implements IRegisterService<BlockRegisterRequest> {
    private final EntityManager entityManager;
    private final RegisterValidationFactory validationFactory;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final BioRiskRepo bioRiskRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareService labwareService;
    private final OperationService operationService;
    private final WorkService workService;
    private final RegisterClashChecker clashChecker;

    public BlockRegisterServiceImp(EntityManager entityManager,
                                   RegisterValidationFactory validationFactory,
                                   DonorRepo donorRepo, TissueRepo tissueRepo, SampleRepo sampleRepo,
                                   SlotRepo slotRepo, BioRiskRepo bioRiskRepo, OperationTypeRepo opTypeRepo,
                                   LabwareService labwareService, OperationService operationService,
                                   WorkService workService,
                                   RegisterClashChecker clashChecker) {
        this.entityManager = entityManager;
        this.validationFactory = validationFactory;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.bioRiskRepo = bioRiskRepo;
        this.opTypeRepo = opTypeRepo;
        this.labwareService = labwareService;
        this.operationService = operationService;
        this.workService = workService;
        this.clashChecker = clashChecker;
    }

    @Override
    public RegisterResult register(User user, BlockRegisterRequest request) throws ValidationException {
        if (request.getLabware().isEmpty()) {
            return new RegisterResult(); // nothing to do
        }
        List<RegisterClash> clashes = clashChecker.findClashes(request);
        if (!clashes.isEmpty()) {
            return RegisterResult.clashes(clashes);
        }
        RegisterValidation validation = validationFactory.createBlockRegisterValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The register request could not be validated.", problems);
        }
        updateExistingTissues(request, validation);
        return create(request, user, validation);
    }

    /**
     * Updates any existing tissues that now have a collection date
     * @param request specification
     * @param validation validation result to look up tissues
     */
    public void updateExistingTissues(BlockRegisterRequest request, RegisterValidation validation) {
        List<Tissue> toUpdate = new ArrayList<>();
        for (BlockRegisterSample brs : iter(requestSamples(request))) {
            if (brs.isExistingTissue() && brs.getSampleCollectionDate()!=null) {
                Tissue tissue = validation.getTissue(brs.getExternalIdentifier());
                if (tissue!=null && tissue.getCollectionDate()==null) {
                    tissue.setCollectionDate(brs.getSampleCollectionDate());
                    toUpdate.add(tissue);
                }
            }
        }
        if (!toUpdate.isEmpty()) {
            tissueRepo.saveAll(toUpdate);
        }
    }

    /**
     * Creates donors that are already in the database
     * @return map of donor name to donor
     */
    public UCMap<Donor> createDonors(BlockRegisterRequest request, RegisterValidation validation) {
        UCMap<Donor> donors = new UCMap<>();
        for (BlockRegisterSample brs : iter(requestSamples(request))) {
            String donorName = brs.getDonorIdentifier();
            if (!donors.containsKey(donorName)) {
                Donor donor = validation.getDonor(donorName);
                if (donor.getId() == null) {
                    donor = donorRepo.save(donor);
                }
                donors.put(donorName, donor);
            }
        }
        return donors;
    }

    /**
     * Creates tissues that aren't already in the database
     * @return map of external identifier to tissue
     */
    public UCMap<Tissue> createTissues(BlockRegisterRequest request, RegisterValidation validation) {
        UCMap<Donor> donors = createDonors(request, validation);
        UCMap<Tissue> tissueMap = new UCMap<>();
        for (BlockRegisterLabware brl : request.getLabware()) {
            for (BlockRegisterSample brs : brl.getSamples()) {
                final String tissueKey = brs.getExternalIdentifier();
                if (tissueMap.get(tissueKey) != null) {
                    continue;
                }
                Tissue existingTissue = validation.getTissue(tissueKey);
                if (existingTissue != null) {
                    tissueMap.put(tissueKey, existingTissue);
                    continue;
                }
                Donor donor = donors.get(brs.getDonorIdentifier().toUpperCase());
                Hmdmc hmdmc;
                if (nullOrEmpty(brs.getHmdmc())) {
                    hmdmc = null;
                } else {
                    hmdmc = validation.getHmdmc(brs.getHmdmc());
                    if (hmdmc == null) {
                        throw new IllegalArgumentException("Unknown HuMFre number: " + brs.getHmdmc());
                    }
                }
                CellClass cellClass = validation.getCellClass(brs.getCellClass());
                if (hmdmc == null && donor.getSpecies().requiresHmdmc() && cellClass.isHmdmcRequired()) {
                    throw new IllegalArgumentException("No HuMFre number given for tissue " + brs.getExternalIdentifier());
                }
                if (!donor.getSpecies().requiresHmdmc() && hmdmc != null) {
                    throw new IllegalArgumentException("HuMFre number given for non-human tissue " + brs.getExternalIdentifier());
                }
                Tissue tissue = new Tissue(null, brs.getExternalIdentifier(), brs.getReplicateNumber().toLowerCase(),
                        validation.getSpatialLocation(brs.getTissueType(), brs.getSpatialLocation()),
                        donor,
                        validation.getMedium(brl.getMedium()),
                        validation.getFixative(brl.getFixative()), cellClass,
                        hmdmc, brs.getSampleCollectionDate(), null);
                tissueMap.put(tissueKey, tissueRepo.save(tissue));
            }
        }
        return tissueMap;
    }

    /**
     * Creates the labware and operations for the given registration request
     * @param request the registration request
     * @param user the user responsible for the operations
     * @param validation the data from validation
     * @return result containing the new labware
     */
    public RegisterResult create(BlockRegisterRequest request, User user, RegisterValidation validation) {
        UCMap<Tissue> tissues = createTissues(request, validation);
        List<Labware> lwList = new ArrayList<>();
        List<Operation> opList = new ArrayList<>();
        OperationType opType = opTypeRepo.getByName("Register");
        BioState bioState = opType.getNewBioState();
        for (BlockRegisterLabware brl : request.getLabware()) {
            LabwareType labwareType = validation.getLabwareType(brl.getLabwareType());
            Labware lw = labwareService.create(labwareType);
            lwList.add(lw);
            Set<Slot> slotsToUpdate = new HashSet<>();
            List<Action> actions = new ArrayList<>();
            List<SampleBioRisk> sampleBioRisks = new ArrayList<>();
            for (BlockRegisterSample brs : brl.getSamples()) {
                Tissue tissue = tissues.get(brs.getExternalIdentifier());
                Sample sample = sampleRepo.save(Sample.newBlock(null, tissue, bioState, brs.getHighestSection()));
                Set<Address> addressSet = new HashSet<>(brs.getAddresses());
                List<Slot> slots = lw.getSlots().stream()
                        .filter(slot -> addressSet.contains(slot.getAddress()))
                        .toList();
                for (Slot slot : slots) {
                    slot.addSample(sample);
                    slotsToUpdate.add(slot);
                    actions.add(new Action(null, null, slot, slot, sample, sample));
                }
                BioRisk bioRisk = validation.getBioRisk(brs.getBioRiskCode());
                sampleBioRisks.add(new SampleBioRisk(sample, bioRisk));
            }
            entityManager.refresh(lw);
            slotRepo.saveAll(slotsToUpdate);
            Operation op = operationService.createOperation(opType, user, actions, null);
            opList.add(op);
            for (SampleBioRisk sampleBioRisk : sampleBioRisks) {
                bioRiskRepo.recordBioRisk(sampleBioRisk.sample, sampleBioRisk.bioRisk, op.getId());
            }
        }
        if (!opList.isEmpty() && !nullOrEmpty(validation.getWorks())) {
            workService.link(validation.getWorks(), opList);
        }

        return new RegisterResult(lwList);
    }

    /** Stream the samples in the request */
    Stream<BlockRegisterSample> requestSamples(BlockRegisterRequest request) {
        return request.getLabware().stream().flatMap(brl -> brl.getSamples().stream());
    }

    record SampleBioRisk(Sample sample, BioRisk bioRisk) {}
}
