package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.Collection;
import java.util.List;

/** Utility for loading data and validating a {@link TissueBlockRequest}. */
public interface BlockValidator {
    /**
     * Validates the request.
     * Loads the data for the request into various fields.
     */
    void validate();

    /** Gets the collated data for the request. */
    List<BlockLabwareData> getLwData();

    /** Gets the work (if any) indicated in the request. */
    Work getWork();

    /** Gets the appropriate bio state for the request. */
    BioState getBioState();

    /** Gets the appropriate medium for the request. */
    Medium getMedium();

    /** Gets the appropriate operation type for the request. */
    OperationType getOpType();

    /** Gets any problems found. */
    Collection<String> getProblems();

    /**
     * Throws a ValidationException if there are any problems.
     * @exception ValidationException if there are any problems
     */
    void raiseError();
}
