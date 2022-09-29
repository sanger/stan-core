package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;

/**
 * Service for handling reagent plates {@link ReagentPlate}s
 * @author dr6
 */
public interface ReagentPlateService {
    /**
     * Looks up the given barcode for a reagent plate. Returns the plate if one is found.
     * Otherwise, creates a new plate if the barcode is valid.
     * @param barcode a new or existing reagent plate barcode
     * @param plateType the plate type for a new plate
     * @return the reagent plate found or created
     * @exception IllegalArgumentException if the barcode is invalid
     */
    ReagentPlate createReagentPlate(String barcode, String plateType);

    /**
     * Loads reagent plates from the given barcodes. Unrecognised barcodes are omitted without error.
     * @param barcodes barcodes of plates
     * @return a map of plate barcode to plate
     */
    UCMap<ReagentPlate> loadPlates(Collection<String> barcodes);
}
