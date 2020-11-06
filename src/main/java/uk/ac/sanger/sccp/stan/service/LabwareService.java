package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.ArrayList;

/**
 * Service to create labware (complete with the appropriate slots).
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
     * Create new empty labware of the given type, with a new stan barcode.
     * @param labwareType the labware type
     * @return the new labware
     */
    public Labware create(LabwareType labwareType) {
        return create(labwareType, barcodeSeedRepo.createStanBarcode());
    }

    /**
     * Create new empty labware of the given type with the given barcode.
     * @param labwareType the labware type
     * @param barcode the barcode for the labware
     * @return the new labware
     */
    public Labware create(LabwareType labwareType, String barcode) {
        Labware labware = labwareRepo.save(new Labware(null, barcode, labwareType, null));
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
}
