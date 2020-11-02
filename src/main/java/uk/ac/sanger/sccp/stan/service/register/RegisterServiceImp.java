package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.DonorRepo;
import uk.ac.sanger.sccp.stan.repo.TissueRepo;
import uk.ac.sanger.sccp.stan.request.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.*;

/**
 * @author dr6
 */
@Component
public class RegisterServiceImp implements RegisterService {
    private final RegistrationValidationFactory validationFactory;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;

    @Autowired
    public RegisterServiceImp(RegistrationValidationFactory validationFactory,
                              DonorRepo donorRepo, TissueRepo tissueRepo) {
        this.validationFactory = validationFactory;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
    }

    public void register(RegisterRequest request, User user) {
        if (request.getBlocks().isEmpty()) {
            return; // nothing to do
        }
        RegisterValidation validation = validationFactory.createRegistrationValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The register request could not be validated.", problems);
        }
        create(request, validation);
    }

    public void create(RegisterRequest request, RegisterValidation validation) {
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

        for (BlockRegisterRequest block : request.getBlocks()) {
            Tissue tissue = new Tissue(null, block.getExternalIdentifier(), block.getReplicateNumber(),
                    validation.getSpatialLocation(block.getTissueType(), block.getSpatialLocation()),
                    donors.get(block.getDonorIdentifier().toUpperCase()),
                    validation.getMouldSize(block.getMouldSize()),
                    validation.getMedium(block.getMedium()),
                    validation.getHmdmc(block.getHmdmc()));
            tissue = tissueRepo.save(tissue);
            // TODO

        }
    }

}
