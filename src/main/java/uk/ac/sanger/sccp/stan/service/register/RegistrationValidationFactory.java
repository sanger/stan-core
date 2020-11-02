package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;

/**
 * @author dr6
 */
@Component
public class RegistrationValidationFactory {
    private DonorRepo donorRepo;
    private HmdmcRepo hmdmcRepo;
    private TissueTypeRepo ttRepo;
    private LabwareTypeRepo ltRepo;
    private MouldSizeRepo mouldSizeRepo;
    private MediumRepo mediumRepo;
    private TissueRepo tissueRepo;

    @Autowired
    public RegistrationValidationFactory(DonorRepo donorRepo, HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo,
                                         LabwareTypeRepo ltRepo, MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                                         TissueRepo tissueRepo) {
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
    }

    public RegisterValidation createRegistrationValidation(RegisterRequest request) {
        return new RegisterValidationImp(request, donorRepo, hmdmcRepo, ttRepo, ltRepo, mouldSizeRepo, mediumRepo, tissueRepo);
    }
}
