package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.request.LibraryConRequest;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.LibConData;

/** Service to validate a {@link LibraryConRequest} */
public interface LibraryConValidationService {
    /**
     * Performs validations for all the operations that will be performed for library con.
     * Problems are recorded in the data's {@code problems} field.
     * @param libConData the data about the requests
     */
    void validate(LibConData libConData);
}
