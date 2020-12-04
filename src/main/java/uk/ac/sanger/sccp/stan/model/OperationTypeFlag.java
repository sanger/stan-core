package uk.ac.sanger.sccp.stan.model;

public enum OperationTypeFlag {
    IN_PLACE,
    SOURCE_IS_BLOCK,
    ;

    public int bit() {
        return (1 << this.ordinal());
    }
}
