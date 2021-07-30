package uk.ac.sanger.sccp.stan.service.sas;

import uk.ac.sanger.sccp.stan.model.SasNumber;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.model.User;

/**
 * Service for managing {@link SasNumber SAS numbers}.
 * @author dr6
 */
public interface SasService {
    /**
     * Creates a new sas number. Records a create event for the sas number linked to the specified user
     * @param user the user responsible for creating the sas number
     * @param prefix the prefix ({@code SAS} or {@code R&D}) for the SAS number
     * @param projectName the name of the project for the SAS number
     * @param costCode the code of the cost code for the SAS number
     * @return the new SAS number
     */
    SasNumber createSasNumber(User user, String prefix, String projectName, String costCode);

    /**
     * Updates the status of the sas number. Records a sas event for the change.
     * The comment id is required for changes that need a reason (i.e. pause and fail); and must be null
     * for changes that do not need a reason.
     * @param user the user responsible for the change
     * @param sasNum the sas number to be updated
     * @param newStatus the new status
     * @param commentId the id of the comment giving a reason for the change
     * @return the updated SAS number
     */
    SasNumber updateStatus(User user, String sasNum, Status newStatus, Integer commentId);
}
