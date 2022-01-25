package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.Validator;

/**
 * Factory for {@link RegisterValidation}
 * @author dr6
 */
@Component
public class RegisterValidationFactory {
    private final DonorRepo donorRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo ttRepo;
    private final LabwareTypeRepo ltRepo;
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final TissueRepo tissueRepo;
    private final SpeciesRepo speciesRepo;
    private final LabwareRepo labwareRepo;
    private final BioStateRepo bioStateRepo;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;
    private final Validator<String> externalBarcodeValidation;
    private final Validator<String> visiumLpSlideBarcodeValidation;
    private final Validator<String> replicateValidator;
    private final TissueFieldChecker tissueFieldChecker;

    @Autowired
    public RegisterValidationFactory(DonorRepo donorRepo, HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo,
                                     LabwareTypeRepo ltRepo, MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                                     FixativeRepo fixativeRepo, TissueRepo tissueRepo, SpeciesRepo speciesRepo,
                                     LabwareRepo labwareRepo, BioStateRepo bioStateRepo,
                                     @Qualifier("donorNameValidator") Validator<String> donorNameValidation,
                                     @Qualifier("externalNameValidator") Validator<String> externalNameValidation,
                                     @Qualifier("externalBarcodeValidator") Validator<String> externalBarcodeValidation,
                                     @Qualifier("visiumLPBarcodeValidator") Validator<String> visiumLpSlideBarcodeValidation,
                                     @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                     TissueFieldChecker tissueFieldChecker) {
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.tissueRepo = tissueRepo;
        this.speciesRepo = speciesRepo;
        this.labwareRepo = labwareRepo;
        this.bioStateRepo = bioStateRepo;
        this.donorNameValidation = donorNameValidation;
        this.externalNameValidation = externalNameValidation;
        this.externalBarcodeValidation = externalBarcodeValidation;
        this.visiumLpSlideBarcodeValidation = visiumLpSlideBarcodeValidation;
        this.replicateValidator = replicateValidator;
        this.tissueFieldChecker = tissueFieldChecker;
    }

    public RegisterValidation createRegisterValidation(RegisterRequest request) {
        return new RegisterValidationImp(request, donorRepo, hmdmcRepo, ttRepo, ltRepo, mouldSizeRepo, mediumRepo,
                fixativeRepo, tissueRepo, speciesRepo, donorNameValidation, externalNameValidation, replicateValidator,
                tissueFieldChecker);
    }

    public SectionRegisterValidation createSectionRegisterValidation(SectionRegisterRequest request) {
        return new SectionRegisterValidation(request, donorRepo, speciesRepo, ltRepo, labwareRepo,
                mouldSizeRepo, hmdmcRepo, ttRepo, fixativeRepo, mediumRepo, tissueRepo, bioStateRepo,
                externalBarcodeValidation, donorNameValidation, externalNameValidation, replicateValidator,
                visiumLpSlideBarcodeValidation);
    }
}
