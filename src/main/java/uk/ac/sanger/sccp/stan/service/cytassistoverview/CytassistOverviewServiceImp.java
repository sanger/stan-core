package uk.ac.sanger.sccp.stan.service.cytassistoverview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.CytassistOverview;
import uk.ac.sanger.sccp.stan.repo.CytassistOverviewRepo;

import javax.persistence.EntityManager;
import java.util.List;

/**
 * @author dr6
 */
@Service
public class CytassistOverviewServiceImp implements CytassistOverviewService {
    private final CytassistOverviewDataCompiler dataCompiler;
    private final CytassistOverviewRepo coRepo;
    private final EntityManager entityManager;
    private final Transactor transactor;

    @Autowired
    public CytassistOverviewServiceImp(CytassistOverviewDataCompiler dataCompiler,
                                       CytassistOverviewRepo coRepo,
                                       EntityManager entityManager, Transactor transactor) {
        this.dataCompiler = dataCompiler;
        this.coRepo = coRepo;
        this.entityManager = entityManager;
        this.transactor = transactor;
    }

    @Override
    public void update() {
        transactor.transact("cytassist overview update", () -> {
            List<CytassistOverview> data = dataCompiler.execute();
            coRepo.deleteAllInBatch();
            entityManager.flush();
            coRepo.saveAll(data);
            return null;
        });
    }

    /**
     * Updates the cytassist overview table, triggered on a schedule.
     */
    @Scheduled(cron = "${spring.cytassist.schedule:-}", zone = "GMT")
    public void scheduledUpdate() {
        update();
    }
}
