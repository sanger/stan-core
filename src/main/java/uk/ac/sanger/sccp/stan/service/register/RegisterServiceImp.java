package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.*;

import javax.persistence.EntityNotFoundException;
import java.util.*;

/**
 * @author dr6
 */
@Component
public class RegisterServiceImp implements RegisterService {
    private final RegistrationValidationFactory validationFactory;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final LabwareRepo labwareRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareService labwareService;
    private final OperationService operationService;

    @Autowired
    public RegisterServiceImp(RegistrationValidationFactory validationFactory,
                              DonorRepo donorRepo, TissueRepo tissueRepo,
                              SampleRepo sampleRepo, SlotRepo slotRepo, LabwareRepo labwareRepo,
                              OperationTypeRepo opTypeRepo,
                              LabwareService labwareService, OperationService operationService) {
        this.validationFactory = validationFactory;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.labwareRepo = labwareRepo;
        this.opTypeRepo = opTypeRepo;
        this.labwareService = labwareService;
        this.operationService = operationService;
    }

    @Override
    public RegisterResult register(RegisterRequest request, User user) {
        if (request.getBlocks().isEmpty()) {
            return new RegisterResult(); // nothing to do
        }
        RegisterValidation validation = validationFactory.createRegistrationValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The register request could not be validated.", problems);
        }
        return create(request, user, validation);
    }

    public RegisterResult create(RegisterRequest request, User user, RegisterValidation validation) {
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

        List<Tissue> tissueList = new ArrayList<>(request.getBlocks().size());

        List<Labware> labwareList = new ArrayList<>(request.getBlocks().size());

        for (BlockRegisterRequest block : request.getBlocks()) {
            Tissue tissue = new Tissue(null, block.getExternalIdentifier(), block.getReplicateNumber(),
                    validation.getSpatialLocation(block.getTissueType(), block.getSpatialLocation()),
                    donors.get(block.getDonorIdentifier().toUpperCase()),
                    validation.getMouldSize(block.getMouldSize()),
                    validation.getMedium(block.getMedium()),
                    validation.getHmdmc(block.getHmdmc()));
            tissue = tissueRepo.save(tissue);
            tissueList.add(tissue);
            Sample sample = sampleRepo.save(new Sample(null, null, tissue));
            LabwareType labwareType = validation.getLabwareType(block.getLabwareType());
            Labware labware = labwareService.create(labwareType);
            Slot slot = labware.getSlots().get(0);
            slot.getSamples().add(sample);
            slot.setBlockSampleId(sample.getId());
            slot.setBlockHighestSection(block.getHighestSection());
            slot = slotRepo.save(slot);
            labware = labwareRepo.findById(labware.getId()).orElseThrow(EntityNotFoundException::new);
            labwareList.add(labware);
            OperationType operationType = opTypeRepo.findByName("Register").orElseThrow(EntityNotFoundException::new);
            operationService.createOperation(operationType, user, slot, slot, sample);
        }

        return new RegisterResult(labwareList, tissueList);
    }

}
