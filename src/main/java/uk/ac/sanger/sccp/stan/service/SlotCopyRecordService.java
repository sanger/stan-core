package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecord;
import uk.ac.sanger.sccp.stan.request.SlotCopySave;

import javax.persistence.EntityNotFoundException;

/** Loads and saves {@link SlotCopyRecord}s */
public interface SlotCopyRecordService {
    /** Converts the given slot copy info to a record saved in the database */
    SlotCopyRecord save(SlotCopySave request) throws ValidationException;

    /** Looks up the indicated slot copy data from the database */
    SlotCopySave load(String opname, String workNumber, String lpNumber) throws EntityNotFoundException;
}
