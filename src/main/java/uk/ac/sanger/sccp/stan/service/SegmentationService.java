package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest;

/**
 * Service for dealing with segmentation requests
 * @author dr6
 */
public interface SegmentationService {
    /**
     * Validates and records a segmentation request
     * @param user the user responsible for the request
     * @param request the details of the request
     * @return the operations and labware
     * @exception ValidationException if the request fails validation
     */
    OperationResult perform(User user, SegmentationRequest request) throws ValidationException;
}
