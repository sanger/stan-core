package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;

import java.util.Optional;

/**
 * Service dealing with {@link CostCode}s
 * @author dr6
 */
@Service
public class CostCodeService extends BaseAdminService<CostCode, CostCodeRepo> {
    @Autowired
    public CostCodeService(CostCodeRepo costCodeRepo,
                           @Qualifier("costCodeValidator") Validator<String> costCodeValidator,
                           Transactor transactor, AdminNotifyService notifyService) {
        super(costCodeRepo, "CostCode", "Code", costCodeValidator, transactor, notifyService);
    }

    @Override
    public String notificationName() {
        return "costcode";
    }

    @Override
    protected CostCode newEntity(String code) {
        return new CostCode(null, code.toUpperCase());
    }

    @Override
    protected Optional<CostCode> findEntity(CostCodeRepo repo, String code) {
        return repo.findByCode(code);
    }
}
