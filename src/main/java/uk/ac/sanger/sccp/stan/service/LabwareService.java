package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Service to help with labware, including creating labware with appropriate slots.
 * @author dr6
 */
@Service
public class LabwareService {
    private final LabwareRepo labwareRepo;
    private final SlotRepo slotRepo;
    private final BarcodeSeedRepo barcodeSeedRepo;
    private final EntityManager entityManager;

    @Autowired
    public LabwareService(EntityManager entityManager, LabwareRepo labwareRepo, SlotRepo slotRepo,
                          BarcodeSeedRepo barcodeSeedRepo) {
        this.labwareRepo = labwareRepo;
        this.slotRepo = slotRepo;
        this.barcodeSeedRepo = barcodeSeedRepo;
        this.entityManager = entityManager;
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
        Labware unsaved = new Labware(null, barcode, labwareType, null);
        unsaved.setExternalBarcode(externalBarcode);
        return create(unsaved);
    }

    /**
     * Creates new empty labware with slots from the given unsaved labware object.
     * If the given labware does not specify a barcode, one will be created.
     * @param unsaved an unsaved labware object
     * @return the new labware, complete with its slots
     */
    public Labware create(Labware unsaved) {
        if (unsaved.getBarcode()==null) {
            unsaved.setBarcode(barcodeSeedRepo.createStanBarcode());
        }
        Labware labware = labwareRepo.save(unsaved);
        LabwareType labwareType = unsaved.getLabwareType();
        final int numRows = labwareType.getNumRows();
        final int numColumns = labwareType.getNumColumns();
        for (int row = 1; row <= numRows; ++row) {
            for (int col = 1; col <= numColumns; ++col) {
                Slot slot = new Slot(null, labware.getId(), new Address(row, col), new ArrayList<>(), null, null);
                slotRepo.save(slot);
            }
        }
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
}
