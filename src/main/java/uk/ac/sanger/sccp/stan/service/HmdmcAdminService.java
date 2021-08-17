package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Hmdmc;
import uk.ac.sanger.sccp.stan.repo.HmdmcRepo;

import java.util.Optional;

/**
 * Service for admin of hmdmcs
 * @author dr6
 */
@Service
public class HmdmcAdminService extends BaseAdminService<Hmdmc, HmdmcRepo> {
    @Autowired
    public HmdmcAdminService(HmdmcRepo repo, @Qualifier("hmdmcValidator") Validator<String> hmdmcValidator) {
        super(repo, "HMDMC", "HMDMC", hmdmcValidator);
    }

    @Override
    protected Hmdmc newEntity(String string) {
        return new Hmdmc(null, string);
    }

    @Override
    protected Optional<Hmdmc> findEntity(HmdmcRepo repo, String string) {
        return repo.findByHmdmc(string);
    }
}
