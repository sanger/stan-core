package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.OmeroProject;
import uk.ac.sanger.sccp.stan.repo.OmeroProjectRepo;

import java.util.Optional;

/**
 * Admin service for {@link OmeroProject}
 * @author dr6
 */
@Service
public class OmeroProjectAdminService extends BaseAdminService<OmeroProject, OmeroProjectRepo> {
    @Autowired
    public OmeroProjectAdminService(OmeroProjectRepo repo,
                                    @Qualifier("omeroProjectNameValidator") Validator<String> omeroProjectValidator,
                                    Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "Omero project", "Name", omeroProjectValidator, transactor, notifyService);
    }

    @Override
    protected OmeroProject newEntity(String name) {
        return new OmeroProject(name);
    }

    @Override
    protected Optional<OmeroProject> findEntity(OmeroProjectRepo repo, String name) {
        return repo.findByName(name);
    }
}
