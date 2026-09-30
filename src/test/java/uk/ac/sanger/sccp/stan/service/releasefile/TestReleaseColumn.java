package uk.ac.sanger.sccp.stan.service.releasefile;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Test {@link ReleaseColumn} */
class TestReleaseColumn {
    @Test
    void testDateFormat() {
        LocalDate date = LocalDate.of(2022, 12, 9);
        ReleaseEntry entry = new ReleaseEntry(null, null, null);
        assertNull(ReleaseColumn.Date_sectioned.get(entry));
        entry.setSectionDate(date);
        assertEquals("09-12-2022", ReleaseColumn.Date_sectioned.get(entry));
    }

    @Test
    void testDatetimeFormat() {
        LocalDateTime datetime = LocalDateTime.of(2022, 12, 9, 12, 34, 56);
        ReleaseEntry entry = new ReleaseEntry(null, null, null);
        assertNull(ReleaseColumn.Probe_hybridisation_start.get(entry));
        entry.setHybridStart(datetime);
        assertEquals("09-12-2022 12:34:56", ReleaseColumn.Probe_hybridisation_start.get(entry));
    }
}
