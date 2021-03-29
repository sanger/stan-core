package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.LabelType;
import uk.ac.sanger.sccp.stan.model.Printer;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link PrinterRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestPrinterRepo {
    @Autowired
    PrinterRepo printerRepo;
    @Autowired
    LabelTypeRepo labelTypeRepo;

    @Test
    @Transactional
    public void testGetByName() {
        Printer printer = printerRepo.getByName("cgaptestbc");
        assertNotNull(printer);
        List<String> labelTypeNames = printer.getLabelTypes().stream().map(LabelType::getName).collect(toList());
        assertEquals(List.of("tiny", "plate"), labelTypeNames);

        assertThat(assertThrows(EntityNotFoundException.class, () -> printerRepo.getByName("Bananas")))
                .hasMessage("Printer not found: \"Bananas\"");
    }

    @Test
    @Transactional
    public void testFindAllByLabelTypes() {
        LabelType plate = labelTypeRepo.getByName("plate");
        LabelType tiny = labelTypeRepo.getByName("tiny");
        List<Printer> thinPrinters = printerRepo.findAllByLabelTypes(plate);
        assertThat(thinPrinters).hasSize(1);
        assertEquals("cgaptestbc", thinPrinters.get(0).getName());
        printerRepo.save(new Printer(null, "test", List.of(tiny), Printer.Service.sprint));
        List<Printer> tinyPrinters = printerRepo.findAllByLabelTypes(tiny);
        assertThat(tinyPrinters).hasSize(2);
        assertThat(tinyPrinters.stream().map(Printer::getName)).containsExactlyInAnyOrder("cgaptestbc", "test");
    }
}
