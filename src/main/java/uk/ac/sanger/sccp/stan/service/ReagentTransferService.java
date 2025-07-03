package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;

/**
 * Service for recording a reagent transfer operation
 * @author dr6
 */
public interface ReagentTransferService {
    /**
     * Validates and performs a reagent transfer request.
     * @param user the user responsible for the request
     * @param request the transfer request
     * @return the operations recorded and labware
     * @exception ValidationException the request validation failed
     */
    OperationResult perform(User user, ReagentTransferRequest request) throws ValidationException;

    /**
     * Loads and checks the operation type
     * @param problems receptacle for problems
     * @param opName the name of the op type
     * @return the operation type loaded, if any
     */
    OperationType loadOpType(Collection<String> problems, String opName);

    /**
     * Loads any reagent plates already in the database matching any of the specified barcodes.
     * Unrecognised or missing barcodes are omitted without error.
     * @param transfers reagent transfers, specifying reagent plate barcodes
     * @return a map of barcode to reagent plates
     */
    UCMap<ReagentPlate> loadReagentPlates(Collection<ReagentTransferRequest.ReagentTransfer> transfers);

    /**
     * Checks the given plate type is suitable.
     * It must be equal to a value from {@link ReagentPlate#REAGENT_PLATE_TYPES}.
     * It must match existing plates.
     * @param problems receptacle for problems
     * @param existingPlates the existing reagent plates (if any) referred to by the request
     * @param plateTypeArg the plate type as given in the request
     */
    String checkPlateType(Collection<String> problems, Collection<ReagentPlate> existingPlates, String plateTypeArg);

    /**
     * Records the specified transfers.
     * @param user the user responsible
     * @param opType the type of operation to record
     * @param work the work number (if any) to link the operation with
     * @param transfers the transfers to make
     * @param reagentPlates the reagent plates that already exist
     * @param lw the destination labware
     * @param plateType the plate type for new plates
     * @return the operations and affected labware
     */
    OperationResult record(User user, OperationType opType, Work work, Collection<ReagentTransferRequest.ReagentTransfer> transfers,
                           UCMap<ReagentPlate> reagentPlates, Labware lw, String plateType);
}
