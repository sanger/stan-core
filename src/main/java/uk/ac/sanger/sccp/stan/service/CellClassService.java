package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.CellClass;
import uk.ac.sanger.sccp.stan.repo.CellClassRepo;

import java.util.Optional;

/**
 * Service dealing with {@link CellClass}es
 * @author dr6
 */
@Service
public class CellClassService extends BaseAdminService<CellClass, CellClassRepo> {
    @Autowired
    public CellClassService(CellClassRepo repo,
                            @Qualifier("cellClassValidator") Validator<String> cellClassValidator,
                            Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "CellClass", "Name", cellClassValidator, transactor, notifyService);
    }

    @Override
    protected CellClass newEntity(String name) {
        return new CellClass(name.trim());
    }

    @Override
    protected Optional<CellClass> findEntity(CellClassRepo repo, String name) {
        return repo.findByName(name);
    }
}
