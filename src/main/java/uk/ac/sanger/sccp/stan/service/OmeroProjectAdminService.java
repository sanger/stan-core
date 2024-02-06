package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.OmeroProject;
import uk.ac.sanger.sccp.stan.repo.OmeroProjectRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Admin service for {@link OmeroProject}
 * @author dr6
 */
@Service
public class OmeroProjectAdminService extends BaseAdminService<OmeroProject, OmeroProjectRepo> {
    @Autowired
    public OmeroProjectAdminService(OmeroProjectRepo repo, UserRepo userRepo,
                                    @Qualifier("omeroProjectNameValidator") Validator<String> omeroProjectValidator,
                                    EmailService emailService) {
        super(repo, userRepo, "Omero project", "Name", omeroProjectValidator, emailService);
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
