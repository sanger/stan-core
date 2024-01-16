package uk.ac.sanger.sccp.stan.model;

/**
 * Different types of measurement value
 * @author dr6
 */
public enum MeasurementValueType {
    /** An integer **/
    INT,
    /** A duration in seconds **/
    TIME,
    /** A positive or negative rational number with decimal places */
    DECIMAL,
}
