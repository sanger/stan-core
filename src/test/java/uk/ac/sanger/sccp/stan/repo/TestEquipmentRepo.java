package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Equipment;

import javax.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link EquipmentRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestEquipmentRepo {
    @Autowired
    EquipmentRepo equipmentRepo;

    @Test
    @Transactional
    public void testFindMethods() {
        List<Equipment> newEquipments = List.of(
                new Equipment("Alabama", "scanner"),
                new Equipment("California", "spatula"),
                new Equipment(null, "Delaware", "scanner", false),
                new Equipment(null, "Florida", "spatula", false)
        );
        Equipment[] eq = StreamSupport.stream(equipmentRepo.saveAll(newEquipments).spliterator(), false)
                .sorted(Comparator.comparing(Equipment::getName))
                .toArray(Equipment[]::new);

        assertThat(equipmentRepo.findAll()).containsExactlyInAnyOrder(eq);
        assertThat(equipmentRepo.findAllByCategory("scanner")).containsExactlyInAnyOrder(eq[0], eq[2]);
        assertThat(equipmentRepo.findAllByEnabled(true)).containsExactlyInAnyOrder(eq[0], eq[1]);
        assertThat(equipmentRepo.findAllByCategoryAndEnabled("scanner", true)).containsExactly(eq[0]);

        assertThat(equipmentRepo.findByCategoryAndName("scanner", "Florida")).isEmpty();
        assertThat(equipmentRepo.findByCategoryAndName("scanner", "Alabama")).contains(eq[0]);

        assertEquals(eq[0], equipmentRepo.getById(eq[0].getId()));
    }
}
