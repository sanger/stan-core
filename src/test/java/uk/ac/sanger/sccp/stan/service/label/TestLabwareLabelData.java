package uk.ac.sanger.sccp.stan.service.label;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link LabwareLabelData}
 * @author dr6
 */
public class TestLabwareLabelData {
    @Test
    public void testFields() {
        LabwareLabelData data = new LabwareLabelData(
                "STAN-123", "Butter",
                List.of(
                        new LabelContent("DONOR1", "TISSUE1", 1, null),
                        new LabelContent("DONOR2", "TISSUE2", 2, 3)
                )
        );
        Map<String, String> fields = data.getFields();
        assertThat(fields).isEqualTo(Map.of(
                "barcode", "STAN-123",
                "medium", "Butter",

                "donor[0]", "DONOR1",
                "tissue[0]", "TISSUE1",
                "replicate[0]", "R:1",

                "donor[1]", "DONOR2",
                "tissue[1]", "TISSUE2",
                "replicate[1]", "R:2",
                "section[1]", "S003"
        ));
    }
}
