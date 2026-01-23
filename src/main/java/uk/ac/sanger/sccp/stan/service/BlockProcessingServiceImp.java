package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.block.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import static java.util.Objects.requireNonNull;

/**
 * @author dr6
 */
@Service
public class BlockProcessingServiceImp implements BlockProcessingService {

    private final BlockValidatorFactory blockValidatorFactory;
    private final BlockMakerFactory blockMakerFactory;
    private final StoreService storeService;

    private final Transactor transactor;

    @Autowired
    public BlockProcessingServiceImp(BlockValidatorFactory blockValidatorFactory, BlockMakerFactory blockMakerFactory,
                                     StoreService storeService, Transactor transactor) {
        this.blockValidatorFactory = blockValidatorFactory;
        this.blockMakerFactory = blockMakerFactory;
        this.storeService = storeService;
        this.transactor = transactor;
    }

    @Override
    public OperationResult perform(User user, TissueBlockRequest request) throws ValidationException {
        OperationResult opres = transactor.transact("Block processing", () -> performInsideTransaction(user, request));
        if (!request.getDiscardSourceBarcodes().isEmpty()) {
            storeService.discardStorage(user, request.getDiscardSourceBarcodes());
        }
        return opres;
    }

    public OperationResult performInsideTransaction(User user, TissueBlockRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");

        BlockValidator val = blockValidatorFactory.createBlockValidator(request);
        val.validate();
        val.raiseError();
        BlockMaker maker = blockMakerFactory.createBlockMaker(request, val.getLwData(), val.getMedium(),
                val.getBioState(), val.getWork(), val.getOpType(), user);
        return maker.record();
    }
}
