package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.model.taglayout.TagHeading;
import uk.ac.sanger.sccp.stan.model.taglayout.TagLayout;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link TagLayoutRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Sql("/testdata/tag_layout_test.sql")
public class TestTagLayoutRepo {
    @Autowired
    TagLayoutRepo tagLayoutRepo;

    @Test
    @Transactional
    public void testRead() {
        TagLayout tl = tagLayoutRepo.getByName("TAG layout 1");
        assertEquals("tag layout 1", tl.getName());
        var headings = tl.getHeadings();
        assertThat(headings).hasSize(3);
        assertTagHeading(headings.get(0), "Alpha", 1, 1, "AlphaA1", 1, 2, "AlphaA2");
        assertTagHeading(headings.get(1), "Beta", 1, 2, "BetaA2", 2, 1, "BetaB1");
        assertTagHeading(headings.get(2), "Gamma", 1, 1, "GammaA1", 1, 2, "GammaA2");
    }

    private void assertTagHeading(TagHeading heading, String name, Object... args) {
        assertEquals(name, heading.getName());
        Map<Address, String> expectedValues = new HashMap<>(args.length/3);
        for (int i = 0; i < args.length; i += 3) {
            expectedValues.put(new Address((Integer) args[i], (Integer) args[i+1]), (String) args[i+2]);
        }
        assertThat(heading.getEntries()).containsExactlyEntriesOf(expectedValues);
    }

    @ParameterizedTest
    @Transactional
    @ValueSource(booleans={false,true})
    public void testGetMapByIdIn(boolean success) {
        if (!success) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> tagLayoutRepo.getMapByIdIn(List.of(1,2,404))))
                    .hasMessage("Unknown tag layout ID: [404]");
            return;
        }
        Map<Integer, TagLayout> map = tagLayoutRepo.getMapByIdIn(List.of(1,2));
        assertThat(map).containsOnlyKeys(1,2);
        TagLayout ly1 = map.get(1);
        assertEquals("tag layout 1", ly1.getName());
        var headings = ly1.getHeadings();
        assertThat(headings).hasSize(3);
        assertTagHeading(headings.get(0), "Alpha", 1, 1, "AlphaA1", 1, 2, "AlphaA2");
        assertTagHeading(headings.get(1), "Beta", 1, 2, "BetaA2", 2, 1, "BetaB1");
        assertTagHeading(headings.get(2), "Gamma", 1, 1, "GammaA1", 1, 2, "GammaA2");
        TagLayout ly2 = map.get(2);
        assertEquals("tag layout 2", ly2.getName());
        headings = ly2.getHeadings();
        assertThat(headings).hasSize(1);
        assertTagHeading(headings.get(0), "Alpha", 1, 1, "OtherA1");
    }

    @ParameterizedTest
    @MethodSource("loadLayoutIdsForReagentPlateTypeArgs")
    @Transactional
    public void testLoadLayoutIdsForReagentPlateTypes(String plateType, Integer expectedLayoutId) {
        assertEquals(expectedLayoutId, tagLayoutRepo.layoutIdForReagentPlateType(plateType));
    }

    static Stream<Arguments> loadLayoutIdsForReagentPlateTypeArgs() {
        return Arrays.stream(new Object[][] {
                {ReagentPlate.REAGENT_PLATE_TYPES.get(1), 2 },
                {ReagentPlate.REAGENT_PLATE_TYPES.get(0), 1 },
                { "Bananas", null },
        }).map(Arguments::of);
    }
}
