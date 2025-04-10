package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.TissueType;
import uk.ac.sanger.sccp.stan.request.AddTissueTypeRequest;

/** Service managing tissue types and spatial locations */
public interface TissueTypeService {
    /**
     * Adds a tissue type based on the given request.
     * @param request specification of new tissue type
     * @return the new tissue type
     */
    TissueType performAddTissueType(AddTissueTypeRequest request);

    /**
     * Adds spatial locations to an existing tissue type
     * @param request specification of new spatial locations
     * @return the updated tissue type
     */
    TissueType performAddSpatialLocations(AddTissueTypeRequest request);
}
