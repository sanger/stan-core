package uk.ac.sanger.sccp.stan.model;

/** Flags indicating different possible types of behaviour for operations of a given type */
public enum OperationTypeFlag {
    /** Op happens in-place (source labware is the same as destination) */
    IN_PLACE,
    /** The source of the op must be a tissue block */
    SOURCE_IS_BLOCK,
    /** The source labware will be discarded by the op */
    DISCARD_SOURCE,
    /** The op records a stain on the labware */
    STAIN,
    /** The op records a result */
    RESULT,
    /** An analysis op */
    ANALYSIS,
    /** Op transfers reagents from reagent plates into labware */
    REAGENT_TRANSFER,
    /** The source labware will be marked <i>used</i> by the op */
    MARK_SOURCE_USED,
    /** The op records the details of probes used on labware */
    PROBES,
    /** The destination of the op can be an active piece of labware (i.e. not empty) */
    ACTIVE_DEST,

    // Current limit: 32 flags
    ;

    public int bit() {
        return (1 << this.ordinal());
    }
}
