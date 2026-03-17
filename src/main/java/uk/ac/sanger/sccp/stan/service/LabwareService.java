package uk.ac.sanger.sccp.stan.service;

import com.google.common.collect.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * Service to help with labware, including creating labware with appropriate slots.
 * @author dr6
 */
@Service
public class LabwareService {
    private final LabwareRepo labwareRepo;
    private final SlotRepo slotRepo;
    private final BarcodeIntRepo barcodeIntRepo;
    private final EntityManager entityManager;
    private final LabelTypeRepo labelTypeRepo;
    private final OperationRepo operationRepo;
    private final OperationTypeRepo operationTypeRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final BioRiskRepo bioRiskRepo;

    @Autowired
    public LabwareService(EntityManager entityManager, LabwareRepo labwareRepo, SlotRepo slotRepo,
                          BarcodeIntRepo barcodeIntRepo, LabelTypeRepo labelTypeRepo, OperationRepo operationRepo,
                          OperationTypeRepo operationTypeRepo, LabwareNoteRepo lwNoteRepo, BioRiskRepo bioRiskRepo) {
        this.labwareRepo = labwareRepo;
        this.slotRepo = slotRepo;
        this.barcodeIntRepo = barcodeIntRepo;
        this.entityManager = entityManager;
        this.labelTypeRepo = labelTypeRepo;
        this.operationRepo = operationRepo;
        this.operationTypeRepo = operationTypeRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.bioRiskRepo = bioRiskRepo;
    }

    /**
     * Creates new empty labware of the given type, with a new stan barcode.
     * @param labwareType the labware type
     * @return the new labware
     */
    public Labware create(LabwareType labwareType) {
        return create(labwareType, null, null);
    }

    /**
     * Creates new empty labware of the given type with the given barcode.
     * @param labwareType the labware type
     * @param barcode the barcode for the labware
     * @param externalBarcode the external barcode, if any
     * @return the new labware
     */
    public Labware create(LabwareType labwareType, String barcode, String externalBarcode) {
        if (barcode!=null) {
            barcode = barcode.toUpperCase();
        }
        if (externalBarcode!=null) {
            externalBarcode = externalBarcode.toUpperCase();
        }
        Labware unsaved = new Labware(null, barcode, labwareType, null);
        unsaved.setExternalBarcode(externalBarcode);
        return create(unsaved);
    }

    /**
     * Creates multiple new labware of the given type.
     * @param labwareType the type of labware to create
     * @param number the number of new labware to create
     * @return a list of newly created labware
     */
    public List<Labware> create(LabwareType labwareType, int number) {
        if (number < 0) {
            throw new IllegalArgumentException("Cannot create a negative number of labware.");
        }
        if (number == 0) {
            return List.of();
        }
        requireNonNull(labwareType, "Labware type is null.");
        List<String> barcodes = barcodeIntRepo.createStanBarcodes(number);
        List<Labware> newLabware = barcodes.stream()
                .map(bc -> new Labware(null, bc, labwareType, null))
                .toList();
        Iterable<Labware> savedLabware = labwareRepo.saveAll(newLabware);

        final int numRows = labwareType.getNumRows();
        final int numColumns = labwareType.getNumColumns();
        final List<Slot> newSlots = stream(savedLabware).flatMap(lw -> {
            assert lw != null;
            return Address.stream(numRows, numColumns).map(address ->
                            new Slot(null, lw.getId(), address, null));
        }).toList();
        slotRepo.saveAll(newSlots);
        return Streams.stream(savedLabware)
                .peek(entityManager::refresh)
                .toList();
    }

    /**
     * Creates new empty labware with slots from the given unsaved labware object.
     * If the given labware does not specify a barcode, one will be created.
     * @param unsaved an unsaved labware object
     * @return the new labware, complete with its slots
     */
    public Labware create(Labware unsaved) {
        if (unsaved.getBarcode()==null) {
            unsaved.setBarcode(barcodeIntRepo.createStanBarcode());
        }
        Labware labware = labwareRepo.save(unsaved);
        LabwareType labwareType = unsaved.getLabwareType();
        final int numRows = labwareType.getNumRows();
        final int numColumns = labwareType.getNumColumns();
        List<Slot> newSlots = Address.stream(numRows, numColumns)
                .map(address -> new Slot(null, labware.getId(), address, null))
                .collect(toList());
        slotRepo.saveAll(newSlots);
        entityManager.refresh(labware);
        return labware;
    }

    /**
     * Gets all the labware containing any of the specified samples.
     * Currently this is done by getting every slot containing any of the given samples,
     * and then loading all the labware indicated by the slots.
     * @param samples the samples to find labware for
     * @return all the labware that contains any of the given samples
     */
    public List<Labware> findBySample(Collection<Sample> samples) {
        List<Slot> slots = slotRepo.findDistinctBySamplesIn(samples);
        if (slots.isEmpty()) {
            return List.of();
        }
        Set<Integer> labwareIds = slots.stream()
                .map(Slot::getLabwareId)
                .collect(toSet());
        return labwareRepo.findAllByIdIn(labwareIds);
    }

    /**
     * Calculates what LabelType to use when given a piece of labware
     * Initially implemented to enable 4 Slot slides to print on slide labels when they contain less than 4 samples
     * @param lw a piece of labware
     * @return a LabelType
     */
    public LabelType calculateLabelType(Labware lw) {
        if (lw.getLabwareType().getName().equalsIgnoreCase("4 Slot Slide")) {
            int sampleCount = 0;
            for (Slot slot : lw.getSlots()) {
                sampleCount += slot.getSamples().size();
            }
            if (sampleCount < 4 ) {
                return labelTypeRepo.getByName("Slide");
            }
        }
        return lw.getLabwareType().getLabelType();
    }

    /**
     * Returns all the operations of the specified type into the specified labware.
     * @param labwareBarcode the barcode of the labware
     * @param opName the name of operation type to look for
     * @return A list of operations whose type and destination match the type and labware given
     */
    public List<Operation> getLabwareOperations(String labwareBarcode, String opName) {
        final Set<String> problems = new LinkedHashSet<>();
        Labware labware = labwareRepo.findByBarcode(labwareBarcode).orElse(null);
        if (labware == null) {
            problems.add(String.format("Could not find labware with barcode %s.", repr(labwareBarcode)));
        }
        OperationType operationType = operationTypeRepo.findByName(opName).orElse(null);
        if (operationType == null) {
            problems.add(String.format("%s operation type not found in database.", repr(opName)));
        }
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return operationRepo.findAllByOperationTypeAndDestinationLabwareIdIn(operationType, List.of(labware.getId()));
    }

    public SlideCosting getLabwareCosting(String barcode) {
        Labware lw = labwareRepo.getByBarcode(barcode);
        final List<LabwareNote> notes = lwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), "costing");
        return notes.stream()
                .max(Comparator.comparing(LabwareNote::getId))
                .map(note -> SlideCosting.valueOf(note.getValue()))
                .orElse(null);
    }

    /**
     * Loads bio risk codes for samples in the specified labware
     * @param barcode labware barcode
     * @return bio risk codes
     */
    public List<SampleBioRisk> getSampleBioRisks(String barcode) {
        Labware lw = labwareRepo.getByBarcode(barcode);
        Set<Integer> sampleIds = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(Sample::getId)
                .collect(toSet());
        Map<Integer, BioRisk> riskMap = bioRiskRepo.loadBioRisksForSampleIds(sampleIds);
        return riskMap.entrySet().stream().map(e -> new SampleBioRisk(e.getKey(), e.getValue().getCode())).toList();
    }

    /** A linked sample and bio risk */
    public record SampleBioRisk(int sampleId, String bioRiskCode) {}
}
