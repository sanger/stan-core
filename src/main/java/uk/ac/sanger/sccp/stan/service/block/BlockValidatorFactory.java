package uk.ac.sanger.sccp.stan.service.block;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

/**
 * Component for creating block request validators
 * @author dr6
 */
@Component
public class BlockValidatorFactory {
    private final LabwareValidatorFactory lwValFactory;
    private final Validator<String> prebarcodeValidator;
    private final Validator<String> replicateValidator;

    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareTypeRepo ltRepo;
    private final BioStateRepo bsRepo;
    private final TissueRepo tissueRepo;
    private final MediumRepo mediumRepo;
    private final CommentValidationService commentValidationService;
    private final WorkService workService;

    @Autowired
    public BlockValidatorFactory(LabwareValidatorFactory lwValFactory,
                                 @Qualifier("tubePrebarcodeValidator") Validator<String> prebarcodeValidator,
                                 @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                 LabwareRepo lwRepo, OperationTypeRepo opTypeRepo,
                                 LabwareTypeRepo ltRepo, BioStateRepo bsRepo, TissueRepo tissueRepo, MediumRepo mediumRepo,
                                 CommentValidationService commentValidationService, WorkService workService) {
        this.lwValFactory = lwValFactory;
        this.prebarcodeValidator = prebarcodeValidator;
        this.replicateValidator = replicateValidator;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.ltRepo = ltRepo;
        this.bsRepo = bsRepo;
        this.tissueRepo = tissueRepo;
        this.mediumRepo = mediumRepo;
        this.commentValidationService = commentValidationService;
        this.workService = workService;
    }

    /** Creates a validator for the given block request. */
    public BlockValidator createBlockValidator(TissueBlockRequest request) {
        return new BlockValidatorImp(lwValFactory, prebarcodeValidator, replicateValidator,
                lwRepo, opTypeRepo, ltRepo, bsRepo, tissueRepo, mediumRepo,
                commentValidationService, workService, request);
    }
}
