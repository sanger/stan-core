package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.DnapStudy;
import uk.ac.sanger.sccp.stan.repo.DnapStudyRepo;

import java.util.Optional;

/**
 * Service dealing with {@link DnapStudy studies}
 * @author dr6
 */
@Service
public class DnapStudyService extends BaseAdminService<DnapStudy, DnapStudyRepo> {
    @Autowired
    public DnapStudyService(DnapStudyRepo dnapStudyRepo,
                            @Qualifier("dnapStudyNameValidator") Validator<String> nameValidator) {
        super(dnapStudyRepo, "DNAP study", "Name", nameValidator);
    }

    @Override
    protected DnapStudy newEntity(String name) {
        return new DnapStudy(name);
    }

    @Override
    protected Optional<DnapStudy> findEntity(DnapStudyRepo repo, String name) {
        return repo.findByName(name);
    }
}
