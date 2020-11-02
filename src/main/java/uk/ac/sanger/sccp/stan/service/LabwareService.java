package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityNotFoundException;
import java.util.List;

/**
 * @author dr6
 */
@Component
public class LabwareService {
    private final LabwareRepo labwareRepo;
    private final SlotRepo slotRepo;
    private final BarcodeSeedRepo barcodeSeedRepo;

    @Autowired
    public LabwareService(LabwareRepo labwareRepo, SlotRepo slotRepo, BarcodeSeedRepo barcodeSeedRepo) {
        this.labwareRepo = labwareRepo;
        this.slotRepo = slotRepo;
        this.barcodeSeedRepo = barcodeSeedRepo;
    }

    public Labware create(LabwareType labwareType) {
        return create(labwareType, barcodeSeedRepo.createStanBarcode());
    }

    public Labware create(LabwareType labwareType, String barcode) {
        Labware labware = labwareRepo.save(new Labware(null, barcode, labwareType, null));
        final int numRows = labwareType.getNumRows();
        final int numColumns = labwareType.getNumColumns();
        for (int row = 1; row <= numRows; ++row) {
            for (int col = 1; col <= numColumns; ++col) {
                Slot slot = new Slot(null, labware.getId(), new Address(row, col), List.of(), null, null);
                slotRepo.save(slot);
            }
        }
        return labwareRepo.findById(labware.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
