package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.Operation;

import java.util.List;

/** Service to look up prior op directly on specified labware */
public interface OpLookupService {
    /**
     * Returns operations matching the given fields.
     * @param opName name of operation type
     * @param barcode labware barcode
     * @param run run name (optional)
     * @param workNumber work number (optional)
     * @return the operation found, may be empty
     */
    List<Operation> findOps(String opName, String barcode, String run, String workNumber);
}
