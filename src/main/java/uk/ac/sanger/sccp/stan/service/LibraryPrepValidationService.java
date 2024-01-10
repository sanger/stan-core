package uk.ac.sanger.sccp.stan.service;

public interface LibraryPrepValidationService {
    /**
     * Performs validations for all the operations that will be performed for library prep.
     * Problems are recorded in the data's {@code problems} field.
     * @param data the request data
     */
    void validate(LibraryPrepServiceImp.RequestData data);
}
