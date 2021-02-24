package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * @author dr6
 */
@Service
public class RegisterServiceImp implements RegisterService {
    private final EntityManager entityManager;
    private final RegisterValidationFactory validationFactory;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final OperationTypeRepo opTypeRepo;
    private final BioStateRepo bioStateRepo;
    private final LabwareService labwareService;
    private final OperationService operationService;

    @Autowired
    public RegisterServiceImp(EntityManager entityManager, RegisterValidationFactory validationFactory,
                              DonorRepo donorRepo, TissueRepo tissueRepo,
                              SampleRepo sampleRepo, SlotRepo slotRepo,
                              OperationTypeRepo opTypeRepo, BioStateRepo bioStateRepo,
                              LabwareService labwareService, OperationService operationService) {
        this.entityManager = entityManager;
        this.validationFactory = validationFactory;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.opTypeRepo = opTypeRepo;
        this.bioStateRepo = bioStateRepo;
        this.labwareService = labwareService;
        this.operationService = operationService;
    }

    @Override
    public RegisterResult register(RegisterRequest request, User user) {
        if (request.getBlocks().isEmpty()) {
            return new RegisterResult(); // nothing to do
        }
        RegisterValidation validation = validationFactory.createRegisterValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The register request could not be validated.", problems);
        }
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

    public RegisterResult create(RegisterRequest request, User user, RegisterValidation validation) {
        Map<String, Donor> donors = createDonors(request, validation);

        List<Tissue> tissueList = new ArrayList<>(request.getBlocks().size());

        List<Labware> labwareList = new ArrayList<>(request.getBlocks().size());
        BioState bioState = bioStateRepo.getByName("Tissue");

        for (BlockRegisterRequest block : request.getBlocks()) {
            Donor donor = donors.get(block.getDonorIdentifier().toUpperCase());
            Hmdmc hmdmc;
            if (block.getHmdmc()!=null && !block.getHmdmc().isEmpty()) {
                hmdmc = validation.getHmdmc(block.getHmdmc());
                if (hmdmc==null) {
                    throw new IllegalArgumentException("Unknown HMDMC number: "+block.getHmdmc());
                }
            } else {
                hmdmc = null;
            }
            if (donor.getSpecies().requiresHmdmc() && hmdmc==null) {
                throw new IllegalArgumentException("No HMDMC number given for tissue "+block.getExternalIdentifier());
            }
            if (!donor.getSpecies().requiresHmdmc() && hmdmc!=null) {
                throw new IllegalArgumentException("HMDMC number given for non-human tissue "+block.getExternalIdentifier());
            }
            Tissue tissue = new Tissue(null, block.getExternalIdentifier(), block.getReplicateNumber(),
                    validation.getSpatialLocation(block.getTissueType(), block.getSpatialLocation()),
                    donors.get(block.getDonorIdentifier().toUpperCase()),
                    validation.getMouldSize(block.getMouldSize()),
                    validation.getMedium(block.getMedium()),
                    validation.getFixative(block.getFixative()),
                    validation.getHmdmc(block.getHmdmc()));
            tissue = tissueRepo.save(tissue);
            tissueList.add(tissue);
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
            OperationType operationType = opTypeRepo.getByName("Register");
            operationService.createOperationInPlace(operationType, user, slot, sample);
        }

        return new RegisterResult(labwareList, tissueList);
    }

}
