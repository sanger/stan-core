package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.DestroyRequest;
import uk.ac.sanger.sccp.stan.request.DestroyResult;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.*;

/**
 * Service for helping with {@link Destruction destructions}.
 * @author dr6
 */
@Service
public class DestructionServiceImp implements DestructionService {
    private final Transactor transactor;

    private final LabwareRepo labwareRepo;
    private final DestructionRepo destructionRepo;
    private final DestructionReasonRepo destructionReasonRepo;

    private final LabwareValidatorFactory labwareValidatorFactory;
    private final StoreService storeService;

    @Autowired
    public DestructionServiceImp(Transactor transactor,
                                 LabwareRepo labwareRepo,
                                 DestructionRepo destructionRepo, DestructionReasonRepo destructionReasonRepo,
                                 LabwareValidatorFactory labwareValidatorFactory, StoreService storeService) {
        this.transactor = transactor;
        this.labwareRepo = labwareRepo;
        this.destructionRepo = destructionRepo;
        this.destructionReasonRepo = destructionReasonRepo;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.storeService = storeService;
    }

    @Override
    public DestroyResult destroyAndUnstore(User user, DestroyRequest request) {
        DestroyResult result = transactDestroy(user, request);
        storeService.discardStorage(user, request.getBarcodes());
        return result;
    }

    /**
     * Updates labware and records destructions.
     * @param user the user responsible for the destruction
     * @param request the specification of the destruction
     * @return the result of the destructions
     */
    public DestroyResult destroy(User user, DestroyRequest request) {
        if (request.getBarcodes()==null || request.getBarcodes().isEmpty()) {
            throw new IllegalArgumentException("No barcodes supplied.");
        }
        if (request.getReasonId()==null) {
            throw new IllegalArgumentException("No reason id supplied.");
        }
        DestructionReason reason = destructionReasonRepo.getById(request.getReasonId());
        if (!reason.isEnabled()) {
            throw new IllegalArgumentException("Specified destruction reason is not enabled.");
        }
        Iterable<Labware> labware = loadAndValidateLabware(request.getBarcodes());
        labware = destroyLabware(labware);
        List<Destruction> destructions = recordDestructions(user, reason, labware);
        return new DestroyResult(destructions);
    }

    /**
     * Performs the destroy functionality inside a transaction.
     * Note the the <code>@Transactional</code> annotation does not work for
     * method calls inside the same class.
     * @param user the user responsible for the destruction
     * @param request the specification of the destruction
     * @return the result of the destructions
     */
    public DestroyResult transactDestroy(User user, DestroyRequest request) {
        return transactor.transact("Destruction transaction", () -> destroy(user, request));
    }

    /**
     * Loads and validates the labware from the given barcodes, using a {@link LabwareValidator}.
     * @param barcodes the labware barcodes
     * @return the loaded labware
     * @exception IllegalArgumentException labware validation problem
     */
    public List<Labware> loadAndValidateLabware(Collection<String> barcodes) {
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        validator.setUniqueRequired(true);
        List<Labware> labware = validator.loadLabware(labwareRepo, barcodes);
        validator.validateSources();
        validator.throwError(IllegalArgumentException::new);
        return labware;
    }

    /**
     * Updates the given labware as destroyed, and returns the saved, updated labware
     * @param labware the labware to destroy
     * @return the saved, updated labware
     */
    public Iterable<Labware> destroyLabware(Iterable<Labware> labware) {
        for (Labware lw : labware) {
            lw.setDestroyed(true);
        }
        return labwareRepo.saveAll(labware);
    }

    /**
     * Records destructions for the given labware
     * @param user the user responsible
     * @param reason the destruction reason
     * @param labware the being destroyed
     * @return the destructions created
     */
    public List<Destruction> recordDestructions(User user, DestructionReason reason, Iterable<Labware> labware) {
        List<Destruction> destructions = new ArrayList<>();
        for (Labware lw : labware) {
            Destruction destruction = destructionRepo.save(new Destruction(null, lw, user, null, reason));
            destructions.add(destruction);
        }
        return destructions;
    }
}
