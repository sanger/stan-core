package uk.ac.sanger.sccp.stan.service.validation;

/**
 * Factory for validation helper.
 */
public interface ValidationHelperFactory {
    /** Gets a new validation helper */
    ValidationHelper getHelper();
}
