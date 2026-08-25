package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.LibraryConRequest;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

@Service
public class LibraryConValidationServiceImp implements LibraryConValidationService {
    private final ValidationHelperFactory helperFactory;
    private final ReagentTransferValidatorService rtValService;
    private final ReagentTransferService rtService;
    private final OpWithSlotMeasurementsService owsmService;

    @Autowired
    public LibraryConValidationServiceImp(ValidationHelperFactory helperFactory,
                                          ReagentTransferValidatorService rtValService,
                                          ReagentTransferService rtService,
                                          OpWithSlotMeasurementsService owsmService) {
        this.helperFactory = helperFactory;
        this.rtValService = rtValService;
        this.rtService = rtService;
        this.owsmService = owsmService;
    }

    @Override
    public void validate(RequestData data) {
        initValidate(data);
        rtValidate(data);
        owsmValidate(data);
    }

    void initValidate(RequestData data) {
        ValidationHelper helper = helperFactory.getHelper();
        String barcode = data.request.getLabwareBarcode();
        if (nullOrEmpty(barcode)) {
            data.problems.add("No labware barcode supplied.");
        } else {
            UCMap<Labware> lwMap = helper.checkLabware(List.of(barcode));
            if (!lwMap.isEmpty()) {
                data.labware = lwMap.values().iterator().next();
            }
        }
        data.work = helper.checkWork(data.request.getWorkNumber());
        data.problems.addAll(helper.getProblems());
    }

    /**
     * Validates the reagent transfer part of the request and updates the data.
     * @param data data related to the request
     */
    void rtValidate(RequestData data) {
        if (data.labware == null) {
            return;
        }
        LibraryConRequest request = data.request;
        final List<ReagentTransfer> reagentTransfers = request.getReagentTransfers();
        data.reagentOpType = rtService.loadOpType(data.problems, "Dual index plate");
        data.reagentPlates = rtService.loadReagentPlates(reagentTransfers);
        data.reagentPlateType = rtService.checkPlateType(data.problems, data.reagentPlates.values(), request.getReagentPlateType());
        rtValService.validateTransfers(data.problems, reagentTransfers, data.reagentPlates, data.labware.layout());
    }

    /**
     * Validates the Amplification part of the request and updates the data.
     * @param data data related to the request
     */
    void owsmValidate(RequestData data) {
        if (data.labware==null) {
            return;
        }
        Set<Address> filledAddresses = data.labware.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .map(Slot::getAddress)
                .collect(toSet());
        owsmService.validateAddresses(data.problems, data.labware.layout(), filledAddresses, data.request.getSlotMeasurements());
        data.ampOpType = owsmService.loadOpType(data.problems, "Amplification");
        data.comments = owsmService.validateComments(data.problems, data.request.getSlotMeasurements());
        data.sanitisedMeasurements = owsmService.sanitiseMeasurements(data.problems, data.ampOpType, data.request.getSlotMeasurements());
        owsmService.checkForDupeMeasurements(data.problems, data.sanitisedMeasurements);
    }
}
