package uk.ac.sanger.sccp.stan.service.label;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link LabwareLabelData}
 * @author dr6
 */
public class TestLabwareLabelData {
    @Test
    public void testFields() {
        LabwareLabelData data = new LabwareLabelData(
                "STAN-123", "123456", "Butter", "2021-03-17",
                List.of(
                        new LabelContent("DONOR1", null, "TISSUE1", "1", (String) null),
                        new LabelContent("DONOR2", null, "TISSUE2", "2a", 3)
                )
        );
        Map<String, String> fields = data.getFields();
        Map<String, String> expected = new HashMap<>(11);
        expected.put("barcode", "STAN-123");
        expected.put("external", "123456");
        expected.put("medium", "Butter");
        expected.put("date", "2021-03-17");
        expected.put("donor[0]", "DONOR1");
        expected.put("tissue[0]", "TISSUE1");
        expected.put("replicate[0]", "R:1");
        expected.put("donor[1]", "DONOR2");
        expected.put("tissue[1]", "TISSUE2");
        expected.put("replicate[1]", "R:2a");
        expected.put("state[1]", "S003");
        assertThat(fields).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
