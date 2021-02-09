package uk.ac.sanger.sccp.stan.service.extract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Service for performing extraction.
 * @author dr6
 */
@Service
public class ExtractServiceImp implements ExtractService {
    private final LabwareRepo labwareRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final OperationTypeRepo opTypeRepo;

    @Autowired
    public ExtractServiceImp(LabwareRepo labwareRepo, LabwareTypeRepo lwTypeRepo, OperationTypeRepo opTypeRepo) {
        this.labwareRepo = labwareRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.opTypeRepo = opTypeRepo;
    }

    @Override
    public OperationResult extract(User user, ExtractRequest request) {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        if (request.getBarcodes()==null || request.getBarcodes().isEmpty()) {
            throw new IllegalArgumentException("No barcodes specified.");
        }
        if (request.getLabwareType()==null || request.getLabwareType().isEmpty()) {
            throw new IllegalArgumentException("No labware type specified.");
        }
        List<Labware> labware = labwareRepo.getByBarcodeIn(request.getBarcodes());
        LabwareType labwareType = lwTypeRepo.getByName(request.getLabwareType());
        OperationType opType = opTypeRepo.getByName("Extract");
        validateLabware(labware);
        // Create new labware
        // Update old labware
        // Create operations
        // NB "RNA" tissue state
        return null;
    }

    public void validateLabware(Collection<Labware> labware) {
        checkForRepeatedLabware(labware);
        checkLabwareState(labware);
        checkLabwareContent(labware);
    }

    public void checkLabwareContent(Collection<Labware> labware) {
        List<String> barcodesOfMultiSlotLabware = new ArrayList<>();
        List<String> barcodesOfMultiSampleLabware = new ArrayList<>();
        List<String> barcodesOfEmptyLabware = new ArrayList<>();
        for (Labware lw : labware) {
            int slotCount = 0;
            Set<Integer> sampleIds = new HashSet<>();
            for (Slot slot : lw.getSlots()) {
                if (slot.getSamples().isEmpty()) {
                    continue;
                }
                ++slotCount;
                for (Sample sample : slot.getSamples()) {
                    sampleIds.add(sample.getId());
                }
            }
            if (slotCount==0) {
                barcodesOfEmptyLabware.add(lw.getBarcode());
            }
            if (sampleIds.size() > 1) {
                barcodesOfMultiSampleLabware.add(lw.getBarcode());
            }
            if (slotCount > 1) {
                barcodesOfMultiSlotLabware.add(lw.getBarcode());
            }
        }

        if (!barcodesOfEmptyLabware.isEmpty()) {
            throw new IllegalArgumentException("Labware is empty: "+barcodesOfEmptyLabware);
        }
        if (!barcodesOfMultiSampleLabware.isEmpty()) {
            throw new IllegalArgumentException("Labware contains multiple samples: "+barcodesOfMultiSampleLabware);
        }
        if (!barcodesOfMultiSlotLabware.isEmpty()) {
            throw new IllegalArgumentException("Labware contains samples in multiple slots: "+barcodesOfMultiSlotLabware);
        }
    }

    public void checkLabwareState(Collection<Labware> labware) {
        List<String> discardedBarcodes = new ArrayList<>();
        List<String> destroyedBarcodes = new ArrayList<>();
        List<String> releasedBarcodes = new ArrayList<>();

        for (Labware lw : labware) {
            if (lw.isDestroyed()) {
                destroyedBarcodes.add(lw.getBarcode());
            } else if (lw.isReleased()) {
                releasedBarcodes.add(lw.getBarcode());
            } else if (lw.isDiscarded()) {
                discardedBarcodes.add(lw.getBarcode());
            }
        }
        if (discardedBarcodes.isEmpty() && destroyedBarcodes.isEmpty() && releasedBarcodes.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>(3);
        if (!destroyedBarcodes.isEmpty()) {
            errors.add("Labware already destroyed: "+destroyedBarcodes);
        }
        if (!releasedBarcodes.isEmpty()) {
            errors.add("Labware already released: "+releasedBarcodes);
        }
        if (!discardedBarcodes.isEmpty()) {
            errors.add("Labware already discarded: "+discardedBarcodes);
        }
        throw new IllegalArgumentException(String.join(" ", errors));
    }

    public void checkForRepeatedLabware(Collection<Labware> labware) {
        Set<Integer> lwIds = new HashSet<>();
        Set<String> repeatedBarcodes = new LinkedHashSet<>();
        for (Labware lw : labware) {
            if (!lwIds.add(lw.getId())) {
                repeatedBarcodes.add(lw.getBarcode());
            }
        }
        if (!repeatedBarcodes.isEmpty()) {
            throw new IllegalArgumentException("Labware specified multiple times: "+repeatedBarcodes);
        }
    }


}
