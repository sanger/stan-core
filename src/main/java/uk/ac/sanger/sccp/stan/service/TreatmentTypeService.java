package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.TreatmentType;
import uk.ac.sanger.sccp.stan.repo.TreatmentTypeRepo;

import java.util.Optional;

/**
 * Service for admin of {@link TreatmentType}s
 * @author dr6
 */
@Service
public class TreatmentTypeService extends BaseAdminService<TreatmentType, TreatmentTypeRepo> {
    @Autowired
    public TreatmentTypeService(TreatmentTypeRepo repo,
                                @Qualifier("treatmentTypeNameValidator") Validator<String> nameValidator,
                                Transactor transactor) {
        super(repo, "Treatment type", "Name", nameValidator, transactor, null);
    }

    @Override
    protected TreatmentType newEntity(String name) {
        return new TreatmentType(name);
    }

    @Override
    protected Optional<TreatmentType> findEntity(TreatmentTypeRepo repo, String name) {
        return repo.findByName(name);
    }
}
