package uk.ac.sanger.sccp.utils.tsv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test {@link TsvWriter}
 * @author dr6
 */
public class TestTsvWriter {
    private ByteArrayOutputStream outputStream;
    private TsvWriter tsvWriter;

    @BeforeEach
    void setup() {
        outputStream = new ByteArrayOutputStream();
        tsvWriter = new TsvWriter(outputStream);
    }

    private String getOutput() {
        return outputStream.toString();
    }

    @Test
    public void testWriteTsvData() throws IOException {
        final String COL1 = "Alpha";
        final String COL2 = "Beta";
        final String COL3 = "Gamma";
        List<Map<String, String>> entries = List.of(
                Map.of(COL1, "Apples", COL2, "Bananas", COL3, "Grapes"),
                Map.of(COL1, "Tab\t", COL2, "Quote\"", COL3, "\"Quote\tTab\""),
                Map.of()
        );
        TsvData<?, ?> tsvData = new TsvFile<>("file.tsv",
                entries, List.of(new Column(COL1), new Column(COL2), new Column(COL3)));

        tsvWriter.write(tsvData);

        String expectedOutput = "Alpha\tBeta\tGamma\n" +
                "Apples\tBananas\tGrapes\n" +
                "\"Tab\t\"\t\"Quote\"\"\"\t\"\"\"Quote\tTab\"\"\"\n" +
                "\t\t\n";
        assertEquals(expectedOutput, getOutput());
    }

    @Test
    public void testClose() throws IOException {
        OutputStream mockOut = mock(OutputStream.class);
        //noinspection EmptyTryBlock
        try (TsvWriter t = new TsvWriter(mockOut)) {
            // nothing
        }
        verify(mockOut).close();
    }

    private static class Column implements TsvColumn<Map<String, String>> {
        String name;

        Column(String name) {
            this.name = name;
        }

        @Override
        public String get(Map<String, String> entry) {
            return entry.get(this.name);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
