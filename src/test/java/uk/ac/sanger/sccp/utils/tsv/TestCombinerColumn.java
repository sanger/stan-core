package uk.ac.sanger.sccp.utils.tsv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.service.releasefile.ReleaseColumn;
import uk.ac.sanger.sccp.stan.service.releasefile.ReleaseEntry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test {@link CombinerColumn} */
class TestCombinerColumn {
    private TsvColumn<ReleaseEntry> innerColumn;
    private TsvColumn<List<ReleaseEntry>> column;
    @BeforeEach
    void setup() {
        innerColumn = ReleaseColumn.Bond_barcode;
        column = new CombinerColumn<>(innerColumn);
    }

    @Test
    void testGet() {
        ReleaseEntry[] entries = IntStream.range(0,4)
                .mapToObj(i -> new ReleaseEntry(null, null, null))
                .toArray(ReleaseEntry[]::new);
        entries[0].setBondBarcode("Bond1");
        entries[1].setBondBarcode("Bond2");
        entries[2].setBondBarcode(null);
        entries[3].setBondBarcode("Bond1");

        assertEquals("Bond1, Bond2", column.get(Arrays.asList(entries)));
    }

    @Test
    void testToString() {
        assertEquals(innerColumn.toString(), column.toString());
    }
}