package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author dr6
 */
@Service
public class LibraryConServiceImp implements LibraryConService {
    private final ReagentTransferService reagentTransferService;
    private final OpWithSlotMeasurementsService opWithSlotMeasurementsService;
    private final LibraryConValidationService valService;

    @Autowired
    public LibraryConServiceImp(ReagentTransferService reagentTransferService,
                                OpWithSlotMeasurementsService opWithSlotMeasurementsService,
                                LibraryConValidationService valService) {
        this.reagentTransferService = reagentTransferService;
        this.opWithSlotMeasurementsService = opWithSlotMeasurementsService;
        this.valService = valService;
    }

    @Override
    public OperationResult perform(User user, LibraryConRequest request) throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }
        RequestData data = new RequestData(request, user, problems);
        valService.validate(data);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return record(data);
    }

    /** Records all ops */
    OperationResult record(RequestData data) {
        OperationResult rtResult = reagentTransferService.record(data.user, data.reagentOpType, data.work,
                data.request.getReagentTransfers(), data.reagentPlates, data.labware, data.reagentPlateType);
        data.labware = rtResult.getLabware().getFirst();
        OperationResult ampResult = opWithSlotMeasurementsService.execute(data.user, data.labware, data.ampOpType,
                data.work, data.comments, data.sanitisedMeasurements);
        data.labware = ampResult.getLabware().getFirst();
        List<Operation> ops = Stream.of(rtResult, ampResult)
                .flatMap(r -> r.getOperations().stream())
                .toList();
        return new OperationResult(ops, List.of(data.labware));
    }

    /** Data in progress for library con */
    public static class RequestData {
        final LibraryConRequest request;
        final User user;
        final Collection<String> problems;
        Labware labware;
        String reagentPlateType;
        OperationType reagentOpType, ampOpType;
        List<Comment> comments;
        Work work;
        UCMap<ReagentPlate> reagentPlates;
        List<SlotMeasurementRequest> sanitisedMeasurements;

        public RequestData(LibraryConRequest request, User user, Collection<String> problems) {
            this.request = request;
            this.user = user;
            this.problems = problems;
        }
    }
}
