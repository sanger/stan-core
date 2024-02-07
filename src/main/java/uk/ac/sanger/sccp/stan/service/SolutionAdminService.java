package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
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
                                @Qualifier("solutionValidator") Validator<String> solutionValidator,
                                Transactor transactor, AdminNotifyService notifyService) {
        super(solutionRepo, "Solution", "Name", solutionValidator, transactor, notifyService);
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
