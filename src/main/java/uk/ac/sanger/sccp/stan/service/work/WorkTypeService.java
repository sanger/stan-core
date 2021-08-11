package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.WorkType;
import uk.ac.sanger.sccp.stan.repo.WorkTypeRepo;
import uk.ac.sanger.sccp.stan.service.BaseAdminService;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.Optional;

/**
 * Service dealing with {@link WorkType}
 * @author dr6
 */
@Service
public class WorkTypeService extends BaseAdminService<WorkType, WorkTypeRepo> {
    @Autowired
    public WorkTypeService(WorkTypeRepo workTypeRepo,
                           @Qualifier("workTypeNameValidator") Validator<String> nameValidator) {
        super(workTypeRepo, "WorkType", "Name", nameValidator);
    }

    @Override
    protected WorkType newEntity(String name) {
        return new WorkType(null, name);
    }

    @Override
    protected Optional<WorkType> findEntity(WorkTypeRepo repo, String name) {
        return repo.findByName(name);
    }
}
