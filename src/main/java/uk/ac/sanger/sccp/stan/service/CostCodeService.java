package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;

/**
 * Service dealing with {@link CostCode}s
 * @author dr6
 */
@Service
public class CostCodeService {
    private final CostCodeRepo costCodeRepo;

    @Autowired
    public CostCodeService(CostCodeRepo costCodeRepo) {
        this.costCodeRepo = costCodeRepo;
    }

    public Iterable<CostCode> getCostCodes(boolean includeDisabled) {
        if (includeDisabled) {
            return costCodeRepo.findAll();
        }
        return costCodeRepo.findAllByEnabled(true);
    }

    public CostCode addCostCode(String code) {
        return costCodeRepo.save(new CostCode(null, code));
    }

    public CostCode setCostCodeEnabled(String code, boolean enabled) {
        CostCode cc = costCodeRepo.getByCode(code);
        cc.setEnabled(enabled);
        return costCodeRepo.save(cc);
    }
}
