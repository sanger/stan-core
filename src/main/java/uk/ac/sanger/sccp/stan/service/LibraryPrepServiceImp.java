package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author dr6
 */
@Service
public class LibraryPrepServiceImp implements LibraryPrepService {
    private final SlotCopyService slotCopyService;
    private final ReagentTransferService reagentTransferService;
    private final OpWithSlotMeasurementsService opWithSlotMeasurementsService;
    private final StoreService storeService;
    private final LibraryPrepValidationService valService;
    private final Transactor transactor;

    @Autowired
    public LibraryPrepServiceImp(SlotCopyService slotCopyService, ReagentTransferService reagentTransferService,
                                 OpWithSlotMeasurementsService opWithSlotMeasurementsService, StoreService storeService,
                                 LibraryPrepValidationService valService,
                                 Transactor transactor) {
        this.slotCopyService = slotCopyService;
        this.reagentTransferService = reagentTransferService;
        this.opWithSlotMeasurementsService = opWithSlotMeasurementsService;
        this.storeService = storeService;
        this.valService = valService;
        this.transactor = transactor;
    }

    @Override
    public OperationResult perform(User user, LibraryPrepRequest request) throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }
        RequestData data = new RequestData(request, user, problems);

        OperationResult result = transactor.transact("LibraryPrep",
                () -> performInsideTransaction(data));
        if (!result.getOperations().isEmpty() && !data.barcodesToUnstore.isEmpty()) {
            storeService.discardStorage(user, data.barcodesToUnstore);
        }
        return result;
    }

    /**
     * This should be called inside a transaction.
     * Validates the request, and either executes it, or throws a validation exception.
     * Further information loaded as part of the validation is stored in the {@code data} parameter.
     * @param data the data about the request
     * @return the destination labware and operations recorded
     * @exception ValidationException if validation fails
     */
    public OperationResult performInsideTransaction(RequestData data) throws ValidationException {
        valService.validate(data);
        if (!data.problems.isEmpty()) {
            throw new ValidationException(data.problems);
        }
        return record(data);
    }

    /**
     * Records the requested operations. This is called after successful validation.
     * @param data the data about the request, including the information loaded during validation.
     * @return the destination labware and operations recorded
     */
    public OperationResult record(RequestData data) {
        OperationResult scResult = slotCopyService.record(data.user, data.slotCopyData, data.barcodesToUnstore);
        data.destination = scResult.getLabware().getFirst();
        OperationResult rtResult = reagentTransferService.record(data.user, data.reagentOpType, data.work, data.request.getReagentTransfers(),
                data.reagentPlates, data.destination, data.reagentPlateType);
        data.destination = rtResult.getLabware().getFirst();
        OperationResult ampResult = opWithSlotMeasurementsService.execute(data.user, data.destination, data.ampOpType,
                data.work, data.comments, data.sanitisedMeasurements);
        data.destination = ampResult.getLabware().getFirst();
        List<Operation> ops = Stream.of(scResult, rtResult, ampResult)
                .flatMap(r -> r.getOperations().stream())
                .toList();
        return new OperationResult(ops, List.of(data.destination));
    }

    /**
     * Structure to contain information about the request.
     * New data is stored in this object during validation and execution of the request.
     */
    public static class RequestData {
        final LibraryPrepRequest request;
        final User user;
        final Collection<String> problems;
        SlotCopyValidationService.Data slotCopyData;
        String reagentPlateType;
        OperationType reagentOpType, ampOpType;
        List<Comment> comments;
        Work work;
        Labware destination;
        UCMap<ReagentPlate> reagentPlates;
        LabwareType destLabwareType;
        Set<String> barcodesToUnstore;
        List<SlotMeasurementRequest> sanitisedMeasurements;

        public RequestData(LibraryPrepRequest request, User user, Collection<String> problems) {
            this.request = request;
            this.user = user;
            this.problems = problems;
            this.barcodesToUnstore = new HashSet<>();
        }
    }
}
