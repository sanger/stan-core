package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.SlotRegion;
import uk.ac.sanger.sccp.stan.repo.SlotRegionRepo;

import java.util.Optional;

/**
 * Admin for {@link SlotRegion}
 * @author dr6
 */
@Service
public class SlotRegionAdminService extends BaseAdminService<SlotRegion, SlotRegionRepo> {
    @Autowired
    public SlotRegionAdminService(SlotRegionRepo repo,
                                  @Qualifier("slotRegionNameValidator") Validator<String> nameValidator,
                                  Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "SlotRegion", "name", nameValidator, transactor, notifyService);
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
