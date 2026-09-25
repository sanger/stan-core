package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

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
    public OperationResult perform(User user, List<LibraryConRequest> requests) throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (nullOrEmpty(requests)) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }
        LibConData libConData = new LibConData(problems, user, requests);
        valService.validate(libConData);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        final List<Operation> opsList = new ArrayList<>();
        final List<Labware> lwList = new ArrayList<>();
        for (RequestData data : libConData.data) {
            record(opsList, lwList, libConData, data);
        }
        return new OperationResult(opsList, lwList);
    }

    /** Records all ops for one request */
    void record(final List<Operation> opsList, final List<Labware> lwList, LibConData libConData, RequestData data) {
        OperationResult rtResult = reagentTransferService.record(libConData.user, libConData.reagentOpType, data.work,
                data.request.getReagentTransfers(), libConData.reagentPlates, data.labware, data.reagentPlateType);
        data.labware = rtResult.getLabware().getFirst();
        OperationResult ampResult = opWithSlotMeasurementsService.execute(libConData.user, data.labware,
                libConData.ampOpType, data.work, libConData.comments, data.sanitisedMeasurements);
        data.labware = ampResult.getLabware().getFirst();
        opsList.addAll(rtResult.getOperations());
        opsList.addAll(ampResult.getOperations());
        lwList.add(data.labware);
    }

    /** Combined data from a batch of requests */
    public static class LibConData {
        final Collection<String> problems;
        final User user;
        List<RequestData> data;
        OperationType reagentOpType, ampOpType;
        UCMap<ReagentPlate> reagentPlates;
        List<Comment> comments;

        public LibConData(Collection<String> problems, User user, List<LibraryConRequest> requests) {
            this.problems = problems;
            this.user = user;
            this.data = requests.stream().map(RequestData::new).toList();
        }
    }

    /** Data in progress for library con */
    public static class RequestData {
        final LibraryConRequest request;
        Labware labware;
        String reagentPlateType;
        Work work;
        List<SlotMeasurementRequest> sanitisedMeasurements;

        public RequestData(LibraryConRequest request) {
            this.request = request;
        }
    }
}
