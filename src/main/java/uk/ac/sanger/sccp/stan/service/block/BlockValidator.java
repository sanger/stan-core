package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

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

    /** Gets the appropriate bio state for the samples created in the request. */
    BioState getNewBioState();

    /** Gets the appropriate medium for the samples created in the request. */
    Medium getMedium();

    /** Gets the appropriate operation type for the request. */
    OperationType getOpType();

    /** What changes have to be made to the source labware? */
    UCMap<? extends SourceChange> getSourceChanges();

    /** Gets any problems found. */
    Collection<String> getProblems();

    /**
     * Throws a ValidationException if there are any problems.
     * @exception ValidationException if there are any problems
     */
    void raiseError();

    /** A change to make in the source labware */
    interface SourceChange {
        /** Should the labware be discarded */
        boolean discard();
        /** Which sample ids should be removed */
        Set<Integer> getSampleIdsToRemove();
    }
}
