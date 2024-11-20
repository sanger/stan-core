package uk.ac.sanger.sccp.stan.service.label.print;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.LabwareNoteService;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.label.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.UCMap.toUCMap;

/**
 * Service to perform and record labware label printing
 * @author dr6
 */
@Service
public class LabelPrintService {
    private final LabwareLabelDataService labwareLabelDataService;
    private final PrintClientFactory printClientFactory;
    private final LabwareRepo labwareRepo;
    private final PrinterRepo printerRepo;
    private final LabwarePrintRepo labwarePrintRepo;
    private final LabelTypeRepo labelTypeRepo;
    private final LabwareService labwareService;
    private final LabwareNoteService noteService;
    private final WorkService workService;

    @Autowired
    public LabelPrintService(LabwareLabelDataService labwareLabelDataService, PrintClientFactory printClientFactory,
                             LabwareRepo labwareRepo, PrinterRepo printerRepo, LabwarePrintRepo labwarePrintRepo,
                             LabelTypeRepo labelTypeRepo, LabwareService labwareService, LabwareNoteService noteService, WorkService workService) {
        this.labwareLabelDataService = labwareLabelDataService;
        this.printClientFactory = printClientFactory;
        this.labwareRepo = labwareRepo;
        this.printerRepo = printerRepo;
        this.labwarePrintRepo = labwarePrintRepo;
        this.labelTypeRepo = labelTypeRepo;
        this.labwareService = labwareService;
        this.noteService = noteService;
        this.workService = workService;
    }

    public void printLabwareBarcodes(User user, String printerName, List<String> barcodes) throws IOException {
        if (barcodes.isEmpty()) {
            throw new IllegalArgumentException("No labware barcodes supplied to print.");
        }
        List<Labware> labware = labwareRepo.getByBarcodeIn(barcodes);
        printLabware(user, printerName, labware);
    }

    public void printLabware(User user, String printerName, List<Labware> labware) throws IOException {
        if (labware.isEmpty()) {
            throw new IllegalArgumentException("No labware supplied to print.");
        }
        Printer printer = printerRepo.getByName(printerName);
        Set<LabelType> labelTypes = labware.stream()
                .map(labwareService::calculateLabelType)
                .collect(toSet());
        if (labelTypes.contains(null)) {
            throw new IllegalArgumentException("Cannot print label for labware without a label type.");
        }
        if (labelTypes.size() > 1) {
            throw new IllegalArgumentException("Cannot perform a print request incorporating multiple different label types.");
        }
        LabelType labelType = labelTypes.iterator().next();
        List<LabwareLabelData> labelData;
        if (labelType.getName().equalsIgnoreCase("strip")) {
            // NB if we try and label empty strip tubes from planned actions, it won't work
            labelData = stripLabwareLabelData(labware);
        } else {
            final Function<Labware, LabwareLabelData> labelFunction;
            if (labelType.getName().equalsIgnoreCase("adh")) {
                labelFunction = labwareLabelDataService::getRowBasedLabelData;
            } else {
                labelFunction = labwareLabelDataService::getLabelData;
            }
            labelData = labware.stream()
                    .map(labelFunction)
                    .toList();
        }
        LabelPrintRequest request = new LabelPrintRequest(labelType, labelData);
        print(printer, request);
        recordPrint(printer, user, labware);
    }

    /**
     * Loads the strip label data for the given labware.
     * Strip tube labware has multiple labels for each item of labware.
     * @param labware the labware being labelled
     * @return the label data for all the labware
     */
    @NotNull
    List<LabwareLabelData> stripLabwareLabelData(List<Labware> labware) {
        UCMap<String> lpNumbers = noteService.findNoteValuesForLabware(labware, "lp number").entrySet().stream()
                .filter(e -> !nullOrEmpty(e.getValue()))
                .collect(toUCMap(Map.Entry::getKey, e -> e.getValue().iterator().next()));
        Map<SlotIdSampleId, Set<Work>> slotWorks = workService.loadWorksForSlotsIn(labware);
        Map<SlotIdSampleId, String> slotWorkNumbers = slotWorks.entrySet().stream()
                .filter(e -> !nullOrEmpty(e.getValue()))
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().iterator().next().getWorkNumber()));
        return labware.stream()
                .flatMap(lw -> labwareLabelDataService.getSplitLabelData(lw, slotWorkNumbers, lpNumbers.get(lw.getBarcode())).stream())
                .toList();
    }

    public void print(Printer printer, LabelPrintRequest request) throws IOException {
        PrintClient<? super LabelPrintRequest> printClient = printClientFactory.getClient(printer.getService());
        printClient.print(printer.getName(), request);
    }

    public Iterable<LabwarePrint> recordPrint(final Printer printer, final User user, final List<Labware> labware) {
        List<LabwarePrint> labwarePrints = labware.stream()
                .map(lw -> new LabwarePrint(printer, lw, user))
                .collect(toList());
        return labwarePrintRepo.saveAll(labwarePrints);
    }

    /**
     * Finds all matching printers. Either all printers with the indicated label type (if one is specified),
     * or all printers.
     * @param labelTypeName the name of a label type (or null to get all printers)
     * @return matching printers
     * @exception EntityNotFoundException an invalid label type name is given
     */
    public Iterable<Printer> findPrinters(String labelTypeName) {
        if (labelTypeName==null) {
            return printerRepo.findAll();
        }
        LabelType labelType = labelTypeRepo.getByName(labelTypeName);
        return printerRepo.findAllByLabelTypes(labelType);
    }
}
