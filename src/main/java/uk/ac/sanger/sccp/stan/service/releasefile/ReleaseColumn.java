package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.util.function.Function;

public enum ReleaseColumn implements TsvColumn<ReleaseEntry> {
    Barcode(ReleaseEntry::getBarcode);

    ReleaseColumn(Function<ReleaseEntry, String> function) {
        this.function = function;
    }

    private final Function<ReleaseEntry, String> function;

    @Override
    public String get(ReleaseEntry entry) {
        return function.apply(entry);
    }

    @Override
    public String toString() {
        return this.name().replace('_',' ');
    }
}
