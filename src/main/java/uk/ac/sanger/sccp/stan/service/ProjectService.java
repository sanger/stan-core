package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.Project;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;

import java.util.Optional;

/**
 * Service for dealing with {@link Project}s
 * @author dr6
 */
@Service
public class ProjectService extends BaseAdminService<Project, ProjectRepo> {
    @Autowired
    public ProjectService(ProjectRepo projectRepo,
                          @Qualifier("projectNameValidator") Validator<String> projectNameValidator,
                          Transactor transactor, AdminNotifyService notifyService) {
        super(projectRepo, "Project", "Name", projectNameValidator, transactor, notifyService);
    }

    @Override
    public String notificationName() {
        return "project";
    }

    @Override
    protected Project newEntity(String name) {
        return new Project(null, name);
    }

    @Override
    protected Optional<Project> findEntity(ProjectRepo repo, String name) {
        return repo.findByName(name);
    }
}
