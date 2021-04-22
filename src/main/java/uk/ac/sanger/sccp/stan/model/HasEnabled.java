package uk.ac.sanger.sccp.stan.model;

/**
 * An entity that can be enabled and disabled
 */
public interface HasEnabled {
    /**
     * Sets whether this entity is enabled
     */
    void setEnabled(boolean enabled);

    /**
     * Gets whether this entity is enabled
     */
    boolean isEnabled();
}
