package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.SolutionSample;
import uk.ac.sanger.sccp.stan.repo.SolutionSampleRepo;

import java.util.Optional;

/**
 * Service for admin of {@link SolutionSample}
 * @author dr6
 */
@Service
public class SolutionSampleAdminService extends BaseAdminService<SolutionSample, SolutionSampleRepo> {
    @Autowired
    public SolutionSampleAdminService(SolutionSampleRepo solutionSampleRepo,
                                      @Qualifier("solutionSampleValidator") Validator<String> solutionSampleValidator) {
        super(solutionSampleRepo, "Solution sample", "Name", solutionSampleValidator);
    }

    @Override
    protected SolutionSample newEntity(String string) {
        return new SolutionSample(null, string);
    }

    @Override
    protected Optional<SolutionSample> findEntity(SolutionSampleRepo repo, String name) {
        return repo.findByName(name);
    }
}
