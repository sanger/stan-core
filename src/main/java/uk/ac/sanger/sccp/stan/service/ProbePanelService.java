package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.model.ProbePanel;
import uk.ac.sanger.sccp.stan.repo.ProbePanelRepo;

import java.util.Optional;

/**
 * Service dealing with {@link CostCode}s
 * @author dr6
 */
@Service
public class ProbePanelService extends BaseAdminService<ProbePanel, ProbePanelRepo> {
    @Autowired
    public ProbePanelService(ProbePanelRepo repo,
                             @Qualifier("probePanelNameValidator") Validator<String> probePanelValidator,
                             Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "ProbePanel", "Name", probePanelValidator, transactor, notifyService);
    }

    @Override
    protected ProbePanel newEntity(String name) {
        return new ProbePanel(name);
    }

    @Override
    protected Optional<ProbePanel> findEntity(ProbePanelRepo repo, String name) {
        return repo.findByName(name);
    }
}
