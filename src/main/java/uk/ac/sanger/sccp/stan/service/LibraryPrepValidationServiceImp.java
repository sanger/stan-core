package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.request.LibraryPrepRequest;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.service.LibraryPrepServiceImp.RequestData;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class LibraryPrepValidationServiceImp implements LibraryPrepValidationService {
    private final SlotCopyValidationService scValService;
    private final ReagentTransferValidatorService rtValService;
    private final ReagentTransferService rtService;
    private final OpWithSlotMeasurementsService owsmService;

    @Autowired
    public LibraryPrepValidationServiceImp(SlotCopyValidationService scValService,
                                           ReagentTransferValidatorService rtValService,
                                           ReagentTransferService rtService,
                                           OpWithSlotMeasurementsService owsmService) {
        this.scValService = scValService;
        this.rtValService = rtValService;
        this.rtService = rtService;
        this.owsmService = owsmService;
    }

    @Override
    public void validate(RequestData data) {
        scValidate(data);
        rtValidate(data);
        owsmValidate(data);
    }

    /**
     * Validates the slotcopy part of the request and updates the data.
     * @param data data related to the request
     */
    public void scValidate(RequestData data) {
        var request = data.request;

        SlotCopyRequest scRequest = new SlotCopyRequest("Transfer", request.getWorkNumber(), null, request.getSources(),
                List.of(request.getDestination()));
        data.slotCopyData = scValService.validateRequest(data.user, scRequest);
        if (!nullOrEmpty(data.slotCopyData.destLabware)) {
            data.destination = data.slotCopyData.destLabware.values().iterator().next();
            data.destLabwareType = data.destination.getLabwareType();
        } else {
            data.destLabwareType = data.slotCopyData.lwTypes.get(request.getDestination().getLabwareType());
        }
        data.work = data.slotCopyData.work;
        data.problems.addAll(data.slotCopyData.problems);
    }

    /**
     * Validates the reagent transfer part of the request and updates the data.
     * @param data data related to the request
     */
    public void rtValidate(RequestData data) {
        LibraryPrepRequest request = data.request;
        final List<ReagentTransfer> reagentTransfers = request.getReagentTransfers();
        data.reagentOpType = rtService.loadOpType(data.problems, "Dual index plate");
        data.reagentPlates = rtService.loadReagentPlates(reagentTransfers);
        data.reagentPlateType = rtService.checkPlateType(data.problems, data.reagentPlates.values(), request.getReagentPlateType());
        rtValService.validateTransfers(data.problems, reagentTransfers, data.reagentPlates, data.destLabwareType);
    }

    /**
     * Validates the Amplification part of the request and updates the data.
     * @param data data related to the request
     */
    public void owsmValidate(RequestData data) {
        if (data.destLabwareType==null) {
            return;
        }
        Set<Address> filledAddresses = data.request.getDestination().getContents().stream()
                .map(SlotCopyContent::getDestinationAddress)
                .filter(Objects::nonNull)
                .collect(toSet());
        owsmService.validateAddresses(data.problems, data.destLabwareType, filledAddresses, data.request.getSlotMeasurements());
        data.ampOpType = owsmService.loadOpType(data.problems, "Amplification");
        data.comments = owsmService.validateComments(data.problems, data.request.getSlotMeasurements());
        data.sanitisedMeasurements = owsmService.sanitiseMeasurements(data.problems, data.ampOpType, data.request.getSlotMeasurements());
        owsmService.checkForDupeMeasurements(data.problems, data.sanitisedMeasurements);
    }
}
