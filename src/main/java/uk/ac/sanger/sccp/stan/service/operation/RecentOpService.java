package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.Operation;

import javax.persistence.EntityNotFoundException;

/**
 * Service for looking up recent ops
 */
public interface RecentOpService {
    /**
     * Gets the latest op (if any) of the given type, whose destination was the specified barcode
     * @param barcode labware barcode
     * @param opName name of op type
     * @return the operation found, or null
     * @exception EntityNotFoundException if the barcode or op name is not recognised
     */
    Operation findLatestOp(String barcode, String opName);
}
