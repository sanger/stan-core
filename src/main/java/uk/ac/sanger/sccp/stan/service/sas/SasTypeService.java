package uk.ac.sanger.sccp.stan.service.sas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.SasType;
import uk.ac.sanger.sccp.stan.repo.SasTypeRepo;
import uk.ac.sanger.sccp.stan.service.BaseAdminService;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.Optional;

/**
 * Service dealing with {@link SasType SAS types}
 * @author dr6
 */
@Service
public class SasTypeService extends BaseAdminService<SasType, SasTypeRepo> {
    @Autowired
    public SasTypeService(SasTypeRepo sasTypeRepo,
                          @Qualifier("sasTypeNameValidator") Validator<String> nameValidator) {
        super(sasTypeRepo, "SasType", "Name", nameValidator);
    }

    @Override
    protected SasType newEntity(String name) {
        return new SasType(null, name);
    }

    @Override
    protected Optional<SasType> findEntity(SasTypeRepo repo, String name) {
        return repo.findByName(name);
    }
}
