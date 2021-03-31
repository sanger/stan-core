package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.StringValidator;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

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
    private final TissueFieldChecker tissueFieldChecker;

    @Autowired
    public RegisterValidationFactory(DonorRepo donorRepo, HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo,
                                     LabwareTypeRepo ltRepo, MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                                     FixativeRepo fixativeRepo, TissueRepo tissueRepo, SpeciesRepo speciesRepo,
                                     LabwareRepo labwareRepo, BioStateRepo bioStateRepo, TissueFieldChecker tissueFieldChecker) {
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
        this.tissueFieldChecker = tissueFieldChecker;
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.UNDERSCORE
        ) ;
        this.donorNameValidation = new StringValidator("Donor identifier", 3, 64, charTypes);
        this.externalNameValidation = new StringValidator("External identifier", 3, 64, charTypes);
        this.externalBarcodeValidation = new StringValidator("External barcode", 3, 32, charTypes);
        Pattern pattern = Pattern.compile("[0-9]{7}[0-9A-Z]+-[0-9]+-[0-9]+-[0-9]+", Pattern.CASE_INSENSITIVE);
        this.visiumLpSlideBarcodeValidation = new StringValidator("Visium LP barcode", 14, 32, charTypes, pattern);
    }

    public RegisterValidation createRegisterValidation(RegisterRequest request) {
        return new RegisterValidationImp(request, donorRepo, hmdmcRepo, ttRepo, ltRepo, mouldSizeRepo, mediumRepo,
                fixativeRepo, tissueRepo, speciesRepo, donorNameValidation, externalNameValidation, tissueFieldChecker);
    }

    public SectionRegisterValidation createSectionRegisterValidation(SectionRegisterRequest request) {
        return new SectionRegisterValidation(request, donorRepo, speciesRepo, ltRepo, labwareRepo,
                mouldSizeRepo, hmdmcRepo, ttRepo, fixativeRepo, mediumRepo, tissueRepo, bioStateRepo,
                externalBarcodeValidation, donorNameValidation, externalNameValidation, visiumLpSlideBarcodeValidation);
    }
}
