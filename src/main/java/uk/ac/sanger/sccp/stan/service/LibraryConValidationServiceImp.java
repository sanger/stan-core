package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.LibConData;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.stan.service.ReagentTransferValidatorService.LayoutTransfers;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class LibraryConValidationServiceImp implements LibraryConValidationService {
    private final ValidationHelperFactory helperFactory;
    private final ReagentTransferValidatorService rtValService;
    private final ReagentTransferService rtService;
    private final OpWithSlotMeasurementsService owsmService;
    private final CommentValidationService comValService;

    @Autowired
    public LibraryConValidationServiceImp(ValidationHelperFactory helperFactory,
                                          ReagentTransferValidatorService rtValService,
                                          ReagentTransferService rtService,
                                          OpWithSlotMeasurementsService owsmService,
                                          CommentValidationService comValService) {
        this.helperFactory = helperFactory;
        this.rtValService = rtValService;
        this.rtService = rtService;
        this.owsmService = owsmService;
        this.comValService = comValService;
    }

    @Override
    public void validate(LibConData libConData) {
        initValidate(libConData);
        rtValidate(libConData);
        owsmValidate(libConData);
    }

    /**
     * Initial validation; loads labware and work.
     * @param libConData the combined request data
     */
    void initValidate(LibConData libConData) {
        ValidationHelper helper = helperFactory.getHelper();
        loadLabware(helper, libConData.data);
        loadWork(helper, libConData.data);
        libConData.problems.addAll(helper.getProblems());
    }

    /**
     * Checks and loads labware
     * @param helper util to load and validate labware
     * @param datas combined request data
     */
    void loadLabware(ValidationHelper helper, List<RequestData> datas) {
        Set<String> barcodes = new HashSet<>(datas.size());
        Set<String> repeatedBarcodes = new LinkedHashSet<>();
        boolean anyNull = false;
        for (RequestData data : datas) {
            String barcode = data.request.getLabwareBarcode();
            if (nullOrEmpty(barcode)) {
                anyNull = true;
                continue;
            }
            barcode = barcode.toUpperCase();
            if (!barcodes.add(barcode)) {
                repeatedBarcodes.add(repr(barcode));
            }
        }
        if (anyNull) {
            helper.getProblems().add("Labware barcode missing from request.");
        }
        if (!repeatedBarcodes.isEmpty()) {
            helper.getProblems().add("Labware barcode repeated: " + repeatedBarcodes);
        }
        if (!barcodes.isEmpty()) {
            UCMap<Labware> lwMap = helper.checkLabware(barcodes);
            for (RequestData data : datas) {
                data.labware = lwMap.get(data.request.getLabwareBarcode());
            }
        }
    }

    /**
     * Loads and validates the works
     * @param helper util to load and validate work
     * @param datas combined request data
     */
    void loadWork(ValidationHelper helper, List<RequestData> datas) {
        final Set<String> workNumbers = new HashSet<>(datas.size());
        datas.forEach(d -> workNumbers.add(d.request.getWorkNumber()));
        UCMap<Work> workMap = helper.checkWork(workNumbers);
        datas.forEach(d -> d.work = workMap.get(d.request.getWorkNumber()));
    }

    /**
     * Validates the reagent transfer part of the requests; loads rt specific data
     * @param libConData combined request data
     */
    void rtValidate(LibConData libConData) {
        libConData.reagentOpType = rtService.loadOpType(libConData.problems, "Dual index plate");
        final List<ReagentTransfer> reagentTransfers = libConData.data.stream()
                .flatMap(d -> d.request.getReagentTransfers().stream())
                .toList();
        libConData.reagentPlates = rtService.loadReagentPlates(reagentTransfers);
        List<LayoutTransfers> layoutTransfers = new ArrayList<>(libConData.data.size());
        for (RequestData data : libConData.data) {
            Stream<ReagentPlate> platesStream = data.request.getReagentTransfers().stream()
                    .map(ReagentTransfer::getReagentPlateBarcode)
                    .map(libConData.reagentPlates::get)
                    .filter(Objects::nonNull)
                    .distinct();
//            if (true) {
//                List<ReagentPlate> plateList = platesStream.toList();
//                platesStream = plateList.stream();
//            }
            data.reagentPlateType = rtService.checkPlateType(libConData.problems, platesStream, data.request.getReagentPlateType());
            if (data.labware != null) {
                layoutTransfers.add(new LayoutTransfers(data.labware.layout(), reagentTransfers));
            }
        }
        if (!layoutTransfers.isEmpty()) {
            rtValService.validateTransfers(libConData.problems, libConData.reagentPlates, layoutTransfers);
        }
    }

    /**
     * Loads the comments indicates in the requests
     * @param libConData combined request data
     * @return the comments loaded
     */
    List<Comment> loadComments(LibConData libConData) {
        Stream<Integer> idStream = libConData.data.stream()
                .flatMap(d -> d.request.getSlotMeasurements().stream())
                .flatMap(sm -> sm.getCommentIds().stream());
        return comValService.validateCommentIds(libConData.problems, idStream);
    }

    /**
     * Validates the OpWithSlotMeasurements part of the requests; loads owsm specific data
     * @param libConData combined request data
     */
    void owsmValidate(LibConData libConData) {
        libConData.ampOpType = owsmService.loadOpType(libConData.problems, "Amplification");
        libConData.comments = loadComments(libConData);
        for (RequestData data : libConData.data) {
            if (data.labware==null) {
                continue;
            }
            Set<Address> filledAddresses = data.labware.getSlots().stream()
                    .filter(slot -> !slot.getSamples().isEmpty())
                    .map(Slot::getAddress)
                    .collect(toSet());
            owsmService.validateAddresses(libConData.problems, data.labware.layout(), filledAddresses, data.request.getSlotMeasurements());
            data.sanitisedMeasurements = owsmService.sanitiseMeasurements(libConData.problems, libConData.ampOpType, data.request.getSlotMeasurements());
            owsmService.checkForDupeMeasurements(libConData.problems, data.sanitisedMeasurements);
        }
    }
}
