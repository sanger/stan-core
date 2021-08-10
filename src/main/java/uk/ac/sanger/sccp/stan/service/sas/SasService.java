package uk.ac.sanger.sccp.stan.service.sas;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;

import java.util.Collection;

/**
 * Service for managing {@link SasNumber SAS numbers}.
 * @author dr6
 */
public interface SasService {
    /**
     * Creates a new sas number. Records a create event for the sas number linked to the specified user
     * @param user the user responsible for creating the sas number
     * @param prefix the prefix ({@code SAS} or {@code R&D}) for the SAS number
     * @param sasTypeName the name of a SAS type for the SAS number
     * @param projectName the name of the project for the SAS number
     * @param costCode the code of the cost code for the SAS number
     * @return the new SAS number
     */
    SasNumber createSasNumber(User user, String prefix, String sasTypeName, String projectName, String costCode);

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

    /**
     * Updates the existing sas number linking them to the given operations and samples in slots in the ops' actions
     * @param sasNumber the string identifying an existing sas number
     * @param operations the operations to link
     * @return the updated and saved sas number
     * @exception javax.persistence.EntityNotFoundException if the sas number does not exist
     * @exception IllegalArgumentException if the sas number is not active
     */
    SasNumber link(String sasNumber, Collection<Operation> operations);

    /**
     * Updates the existing sas number linking them to the given operations and samples in slots in the ops' actions
     * @param sas the sas number
     * @param operations the operations to link
     * @return the updated and saved sas number
     * @exception IllegalArgumentException if the sas number is not active
     */
    SasNumber link(SasNumber sas, Collection<Operation> operations);

    /**
     * Gets the specified sas number.
     * Errors if the sas number cannot be used.
     * @param sasNumber the string representing an existing sas number
     * @return the active sas number corresponding to the argument
     * @see SasNumber#isUsable
     * @exception javax.persistence.EntityNotFoundException if the sas number is unrecognised
     * @exception IllegalArgumentException if the sas number is not usable
     * @exception NullPointerException if the given string is null
     */
    SasNumber getUsableSas(String sasNumber);
}
