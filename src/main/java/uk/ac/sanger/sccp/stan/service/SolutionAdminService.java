package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Solution;
import uk.ac.sanger.sccp.stan.repo.SolutionRepo;

import java.util.Optional;

/**
 * Service for admin of {@link Solution}
 * @author dr6
 */
@Service
public class SolutionAdminService extends BaseAdminService<Solution, SolutionRepo> {
    @Autowired
    public SolutionAdminService(SolutionRepo solutionRepo,
                                @Qualifier("solutionValidator") Validator<String> solutionValidator) {
        super(solutionRepo, "Solution", "Name", solutionValidator);
    }

    @Override
    protected Solution newEntity(String string) {
        return new Solution(null, string);
    }

    @Override
    protected Optional<Solution> findEntity(SolutionRepo repo, String name) {
        return repo.findByName(name);
    }
}
