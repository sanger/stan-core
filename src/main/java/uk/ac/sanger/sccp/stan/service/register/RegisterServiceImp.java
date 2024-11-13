package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * @author dr6
 */
@Service
public class RegisterServiceImp implements IRegisterService<RegisterRequest> {
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

    @Autowired
    public RegisterServiceImp(EntityManager entityManager, RegisterValidationFactory validationFactory,
                              DonorRepo donorRepo, TissueRepo tissueRepo, SampleRepo sampleRepo, SlotRepo slotRepo,
                              BioRiskRepo bioRiskRepo, OperationTypeRepo opTypeRepo,
                              LabwareService labwareService, OperationService operationService, WorkService workService,
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
    public RegisterResult register(User user, RegisterRequest request) {
        if (request.getBlocks().isEmpty()) {
            return new RegisterResult(); // nothing to do
        }
        List<RegisterClash> clashes = clashChecker.findClashes(request);
        if (!clashes.isEmpty()) {
            return RegisterResult.clashes(clashes);
        }
        RegisterValidation validation = validationFactory.createRegisterValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The register request could not be validated.", problems);
        }
        updateExistingTissues(request, validation);
        return create(request, user, validation);
    }

    public Map<String, Donor> createDonors(RegisterRequest request, RegisterValidation validation) {
        Map<String, Donor> donors = new HashMap<>();
        for (BlockRegisterRequest block : request.getBlocks()) {
            String donorName = block.getDonorIdentifier().toUpperCase();
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

    public Map<String, Tissue> createTissues(RegisterRequest request, RegisterValidation validation) {
        Map<String, Donor> donors = createDonors(request, validation);
        Map<String, Tissue> tissueMap = new HashMap<>(request.getBlocks().size());
        for (BlockRegisterRequest block : request.getBlocks()) {
            final String tissueKey =  block.getExternalIdentifier().toUpperCase();
            Tissue existingTissue = validation.getTissue(tissueKey);
            if (tissueMap.get(tissueKey)!=null) {
                continue;
            }
            if (existingTissue!=null) {
                tissueMap.put(tissueKey, existingTissue);
                continue;
            }
            Donor donor = donors.get(block.getDonorIdentifier().toUpperCase());
            Hmdmc hmdmc;
            if (block.getHmdmc()==null || block.getHmdmc().isEmpty()) {
                hmdmc = null;
            } else {
                hmdmc = validation.getHmdmc(block.getHmdmc());
                if (hmdmc==null) {
                    throw new IllegalArgumentException("Unknown HuMFre number: "+block.getHmdmc());
                }
            }
            if (donor.getSpecies().requiresHmdmc() && hmdmc==null) {
                throw new IllegalArgumentException("No HuMFre number given for tissue "+block.getExternalIdentifier());
            }
            if (!donor.getSpecies().requiresHmdmc() && hmdmc!=null) {
                throw new IllegalArgumentException("HuMFre number given for non-human tissue "+block.getExternalIdentifier());
            }
            Tissue tissue = new Tissue(null, block.getExternalIdentifier(), block.getReplicateNumber().toLowerCase(),
                    validation.getSpatialLocation(block.getTissueType(), block.getSpatialLocation()),
                    donor,
                    validation.getMedium(block.getMedium()),
                    validation.getFixative(block.getFixative()),
                    hmdmc, block.getSampleCollectionDate(), null);
            tissueMap.put(tissueKey, tissueRepo.save(tissue));
        }
        return tissueMap;
    }

    /**
     * Updates any existing tissues that now have a collection date
     * @param request specification
     * @param validation validation result to look up tissues
     */
    public void updateExistingTissues(RegisterRequest request, RegisterValidation validation) {
        List<Tissue> toUpdate = new ArrayList<>();
        for (BlockRegisterRequest brr : request.getBlocks()) {
            if (brr.isExistingTissue() && brr.getSampleCollectionDate()!=null) {
                Tissue tissue = validation.getTissue(brr.getExternalIdentifier());
                if (tissue!=null && tissue.getCollectionDate()==null) {
                    tissue.setCollectionDate(brr.getSampleCollectionDate());
                    toUpdate.add(tissue);
                }
            }
        }
        if (!toUpdate.isEmpty()) {
            tissueRepo.saveAll(toUpdate);
        }
    }

    public RegisterResult create(RegisterRequest request, User user, RegisterValidation validation) {
        Map<String, Tissue> tissues = createTissues(request, validation);

        List<Labware> labwareList = new ArrayList<>(request.getBlocks().size());
        OperationType opType = opTypeRepo.getByName("Register");
        BioState bioState = opType.getNewBioState();

        List<Operation> ops = new ArrayList<>(request.getBlocks().size());

        for (BlockRegisterRequest block : request.getBlocks()) {
            Tissue tissue = tissues.get(block.getExternalIdentifier().toUpperCase());
            Sample sample = sampleRepo.save(new Sample(null, null, tissue, bioState));
            LabwareType labwareType = validation.getLabwareType(block.getLabwareType());
            Labware labware = labwareService.create(labwareType);
            Slot slot = labware.getFirstSlot();
            slot.getSamples().add(sample);
            slot.setBlockSampleId(sample.getId());
            slot.setBlockHighestSection(block.getHighestSection());
            slot = slotRepo.save(slot);
            entityManager.refresh(labware);
            labwareList.add(labware);
            final Operation op = operationService.createOperationInPlace(opType, user, slot, sample);
            ops.add(op);
            BioRisk bioRisk = validation.getBioRisk(block.getBioRiskCode());
            bioRiskRepo.recordBioRisk(sample, bioRisk, op.getId());
        }

        if (!ops.isEmpty() && validation.getWorks()!=null && !validation.getWorks().isEmpty()) {
            workService.link(validation.getWorks(), ops);
        }

        return new RegisterResult(labwareList);
    }

}
