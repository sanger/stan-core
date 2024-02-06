package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Program;
import uk.ac.sanger.sccp.stan.repo.ProgramRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Service for dealing with {@link Program}s
 * @author dr6
 */
@Service
public class ProgramService extends BaseAdminService<Program, ProgramRepo> {
    @Autowired
    public ProgramService(ProgramRepo ProgramRepo, UserRepo userRepo,
                          @Qualifier("programNameValidator") Validator<String> ProgramNameValidator,
                          EmailService emailService) {
        super(ProgramRepo, userRepo, "Program", "Name", ProgramNameValidator, emailService);
    }

    @Override
    protected Program newEntity(String name) {
        return new Program(name);
    }

    @Override
    protected Optional<Program> findEntity(ProgramRepo repo, String name) {
        return repo.findByName(name);
    }
}
