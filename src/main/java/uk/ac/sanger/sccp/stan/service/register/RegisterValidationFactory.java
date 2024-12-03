package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

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
    private final Validator<String> xeniumBarcodeValidator;
    private final Validator<String> replicateValidator;
    private final Sanitiser<String> thicknessSanitiser;
    private final TissueFieldChecker tissueFieldChecker;
    private final SlotRegionService slotRegionService;
    private final BioRiskService bioRiskService;
    private final WorkService workService;

    @Autowired
    public RegisterValidationFactory(DonorRepo donorRepo, HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo,
                                     LabwareTypeRepo ltRepo, MediumRepo mediumRepo,
                                     FixativeRepo fixativeRepo, TissueRepo tissueRepo, SpeciesRepo speciesRepo,
                                     LabwareRepo labwareRepo, BioStateRepo bioStateRepo,
                                     @Qualifier("donorNameValidator") Validator<String> donorNameValidation,
                                     @Qualifier("externalNameValidator") Validator<String> externalNameValidation,
                                     @Qualifier("externalBarcodeValidator") Validator<String> externalBarcodeValidation,
                                     @Qualifier("visiumLPBarcodeValidator") Validator<String> visiumLpSlideBarcodeValidation,
                                     @Qualifier("xeniumBarcodeValidator") Validator<String> xeniumBarcodeValidator,
                                     @Qualifier("thicknessSanitiser") Sanitiser<String> thicknessSanitiser,
                                     @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                     TissueFieldChecker tissueFieldChecker,
                                     SlotRegionService slotRegionService, BioRiskService bioRiskService, WorkService workService) {
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
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
        this.xeniumBarcodeValidator = xeniumBarcodeValidator;
        this.replicateValidator = replicateValidator;
        this.thicknessSanitiser = thicknessSanitiser;
        this.tissueFieldChecker = tissueFieldChecker;
        this.slotRegionService = slotRegionService;
        this.workService = workService;
        this.bioRiskService = bioRiskService;
    }

    public RegisterValidation createRegisterValidation(RegisterRequest request) {
        return new RegisterValidationImp(request, donorRepo, hmdmcRepo, ttRepo, ltRepo, mediumRepo,
                fixativeRepo, tissueRepo, speciesRepo, donorNameValidation, externalNameValidation, replicateValidator,
                tissueFieldChecker, bioRiskService, workService);
    }

    public SectionRegisterValidation createSectionRegisterValidation(SectionRegisterRequest request) {
        return new SectionRegisterValidation(request, donorRepo, speciesRepo, ltRepo, labwareRepo,
                hmdmcRepo, ttRepo, fixativeRepo, mediumRepo, tissueRepo, bioStateRepo,
                slotRegionService, bioRiskService, workService,
                externalBarcodeValidation, donorNameValidation, externalNameValidation, replicateValidator,
                visiumLpSlideBarcodeValidation, xeniumBarcodeValidator, thicknessSanitiser);
    }
}
