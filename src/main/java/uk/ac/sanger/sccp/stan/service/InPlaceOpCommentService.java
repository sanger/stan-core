package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.BarcodeAndCommentId;
import uk.ac.sanger.sccp.stan.request.OperationResult;

import java.util.Collection;

/**
 * Service to record some ops in place with comments.
 */
public interface InPlaceOpCommentService {
    /**
     * Validates and records ops with the indicated comments.
     * One operation will be recorded for each unique barcode specified.
     * @param user the user responsible for the operations
     * @param opTypeName the name of the operation
     * @param barcodesAndCommentIds the barcodes and comment ids
     * @return the operations created and the labware
     * @exception ValidationException if the request validation fails
     */
    OperationResult perform(User user, String opTypeName, Collection<BarcodeAndCommentId> barcodesAndCommentIds)
            throws ValidationException;
}
