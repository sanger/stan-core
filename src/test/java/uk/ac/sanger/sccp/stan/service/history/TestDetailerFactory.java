package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Test {@link DetailerFactory} */
class TestDetailerFactory {
    DetailerFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DetailerFactory();
    }

    @Test
    void testMeasurementDetailer() {
        assertThat(factory.measurementDetailer(new HistoryEntry(), Map.of(), List.of())).isInstanceOf(MeasurementDetailer.class);
    }

    @Test
    void testCommentDetailer() {
        assertThat(factory.commentDetailer(new HistoryEntry(), Map.of(), List.of())).isInstanceOf(CommentDetailer.class);
    }

    @Test
    void testRoiDetailer() {
        assertThat(factory.roiDetailer(new HistoryEntry(), Map.of(), List.of())).isInstanceOf(RoiDetailer.class);
    }
}