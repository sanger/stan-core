package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.ProteinPanel;
import uk.ac.sanger.sccp.stan.repo.ProteinPanelRepo;

import java.util.Optional;

@Service
public class ProteinPanelAdminService extends BaseAdminService<ProteinPanel, ProteinPanelRepo> {
    @Autowired
    public ProteinPanelAdminService(ProteinPanelRepo repo,
                                    @Qualifier("proteinPanelNameValidator") Validator<String> proteinPanelNameValidator,
                                    Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "Protein panel", "Name", proteinPanelNameValidator, transactor, notifyService);
    }

    @Override
    protected ProteinPanel newEntity(String string) {
        return new ProteinPanel(string);
    }

    @Override
    protected Optional<ProteinPanel> findEntity(ProteinPanelRepo repo, String string) {
        return repo.findByName(string);
    }
}
