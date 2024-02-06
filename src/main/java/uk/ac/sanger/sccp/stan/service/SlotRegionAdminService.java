package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.SlotRegion;
import uk.ac.sanger.sccp.stan.repo.SlotRegionRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Admin for {@link SlotRegion}
 * @author dr6
 */
@Service
public class SlotRegionAdminService extends BaseAdminService<SlotRegion, SlotRegionRepo> {
    @Autowired
    public SlotRegionAdminService(SlotRegionRepo repo, UserRepo userRepo,
                                  @Qualifier("slotRegionNameValidator") Validator<String> nameValidator,
                                  EmailService emailService) {
        super(repo, userRepo, "SlotRegion", "name", nameValidator, emailService);
    }

    @Override
    protected SlotRegion newEntity(String name) {
        return new SlotRegion(null, name);
    }

    @Override
    protected Optional<SlotRegion> findEntity(SlotRegionRepo repo, String name) {
        return repo.findByName(name);
    }
}
