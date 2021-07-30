package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Project;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;

/**
 * Service for dealing with {@link Project}s
 * @author dr6
 */
@Service
public class ProjectService {
    private final ProjectRepo projectRepo;

    @Autowired
    public ProjectService(ProjectRepo projectRepo) {
        this.projectRepo = projectRepo;
    }

    public Iterable<Project> getProjects(boolean includeDisabled) {
        if (includeDisabled) {
            return projectRepo.findAll();
        }
        return projectRepo.findAllByEnabled(true);
    }

    public Project addProject(String projectName) {
        return projectRepo.save(new Project(null, projectName));
    }

    public Project setProjectEnabled(String projectName, boolean enabled) {
        Project project = projectRepo.getByName(projectName);
        project.setEnabled(enabled);
        return projectRepo.save(project);
    }
}
