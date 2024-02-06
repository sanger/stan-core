package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Service dealing with {@link CostCode}s
 * @author dr6
 */
@Service
public class CostCodeService extends BaseAdminService<CostCode, CostCodeRepo> {
    @Autowired
    public CostCodeService(CostCodeRepo costCodeRepo, UserRepo userRepo,
                           @Qualifier("costCodeValidator") Validator<String> costCodeValidator,
                           EmailService emailService) {
        super(costCodeRepo, userRepo, "CostCode", "Code", costCodeValidator, emailService);
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
