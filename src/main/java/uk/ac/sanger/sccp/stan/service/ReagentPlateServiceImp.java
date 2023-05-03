package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Service for handling {@link ReagentPlate}s
 * @author dr6
 */
@Service
public class ReagentPlateServiceImp implements ReagentPlateService {
    private final ReagentPlateRepo reagentPlateRepo;
    private final ReagentSlotRepo reagentSlotRepo;
    private final TagLayoutRepo tagLayoutRepo;
    private final Validator<String> reagentPlateBarcodeValidator;

    @Autowired
    public ReagentPlateServiceImp(ReagentPlateRepo reagentPlateRepo, ReagentSlotRepo reagentSlotRepo,
                                  TagLayoutRepo tagLayoutRepo,
                                  @Qualifier("reagentPlateBarcodeValidator") Validator<String> reagentPlateBarcodeValidator) {
        this.reagentPlateRepo = reagentPlateRepo;
        this.reagentSlotRepo = reagentSlotRepo;
        this.tagLayoutRepo = tagLayoutRepo;
        this.reagentPlateBarcodeValidator = reagentPlateBarcodeValidator;
    }

    @Override
    public UCMap<ReagentPlate> loadPlates(Collection<String> barcodes) {
        return UCMap.from(reagentPlateRepo.findAllByBarcodeIn(barcodes), ReagentPlate::getBarcode);
    }

    /**
     * Creates a new reagent plate with the given barcode and the appropriate slots.
     * @param barcode the barcode for the new reagent plate
     * @param plateType the plate type for a new plate
     * @return the new plate
     * @exception IllegalArgumentException if the barcode is invalid
     */
    @Override
    public ReagentPlate createReagentPlate(String barcode, String plateType) {
        reagentPlateBarcodeValidator.checkArgument(barcode);
        Integer tagLayoutId = tagLayoutRepo.layoutIdForReagentPlateType(plateType);
        ReagentPlate plate = reagentPlateRepo.save(new ReagentPlate(barcode, plateType, tagLayoutId));
        plate.setSlots(createSlots(plate));
        return plate;
    }

    /**
     * Creates the slots for a newly created reagent plate
     * @param plate the new plate (without any slots)
     * @return the new slots
     */
    public List<ReagentSlot> createSlots(ReagentPlate plate) {
        final ReagentPlateLayout plateLayout = plate.getPlateLayout();
        final Integer plateId = plate.getId();
        List<ReagentSlot> slots = Address.stream(plateLayout.getNumRows(), plateLayout.getNumColumns())
                .map(address -> new ReagentSlot(plateId, address))
                .collect(toList());
        return BasicUtils.asList(reagentSlotRepo.saveAll(slots));
    }
}
