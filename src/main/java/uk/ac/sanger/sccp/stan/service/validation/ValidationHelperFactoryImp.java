package uk.ac.sanger.sccp.stan.service.validation;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;
import uk.ac.sanger.sccp.stan.service.CommentValidationService;
import uk.ac.sanger.sccp.stan.service.LabwareValidatorFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

/**
 * @author dr6
 */
@Service
public class ValidationHelperFactoryImp implements ValidationHelperFactory {
    private final LabwareValidatorFactory lwValFactory;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;

    public ValidationHelperFactoryImp(LabwareValidatorFactory lwValFactory,
                                      OperationTypeRepo opTypeRepo, LabwareRepo lwRepo,
                                      WorkService workService, CommentValidationService commentValidationService) {
        this.lwValFactory = lwValFactory;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
    }

    @Override
    public ValidationHelper getHelper() {
        return new ValidationHelperImp(lwValFactory, opTypeRepo, lwRepo, workService, commentValidationService);
    }
}
