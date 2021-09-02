package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.stubbing.OngoingStubbing;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.Equipment;
import uk.ac.sanger.sccp.stan.repo.EquipmentRepo;

import javax.persistence.EntityExistsException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link EquipmentAdminService}
 * @author dr6
 */
public class TestEquipmentAdminService {
    private EquipmentRepo mockEquipmentRepo;

    private EquipmentAdminService service;

    @BeforeEach
    void setup() {
        mockEquipmentRepo = mock(EquipmentRepo.class);
        service = new EquipmentAdminService(mockEquipmentRepo,
                new StringValidator("Equipment name", 2, 8, StringValidator.CharacterType.ALPHA),
                new StringValidator("Equipment category", 2, 8, StringValidator.CharacterType.ALPHA));
    }

    @ParameterizedTest
    @CsvSource(value={
            ",true",
            "scanner,true",
            ",false",
            "scanner,false",
    })
    public void testGetEquipment(String category, boolean includeDisabled) {
        List<Equipment> eqs = List.of(new Equipment(100, "Bananas", "scanner", true));
        OngoingStubbing<Iterable<Equipment>> scenario;
        if (category==null && includeDisabled) {
            scenario = when(mockEquipmentRepo.findAll());
        } else if (category==null) {
            scenario = when(mockEquipmentRepo.findAllByEnabled(true));
        } else if (includeDisabled) {
            scenario = when(mockEquipmentRepo.findAllByCategory(category));
        } else {
            scenario = when(mockEquipmentRepo.findAllByCategoryAndEnabled(category, true));
        }
        scenario.thenReturn(eqs);

        assertSame(eqs, service.getEquipment(category, includeDisabled));
    }

    @ParameterizedTest
    @CsvSource(value={
            ",Bananas,Category not supplied.",
            "scanner,,Name not supplied.",
            "sc@nner,Bananas,Equipment name \"sc@nner\" contains invalid characters \"@\".",
            "scanner,B@nanas,Equipment category \"B@nanas\" contains invalid characters \"@\".",
            "scanner,Bananas,",
            "'   SCANNER     ', '       Bananas    ',",
    })
    public void testAddEquipment(String category, String name, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            when(mockEquipmentRepo.save(any())).then(Matchers.returnArgument());
            Equipment equipment = service.addEquipment(category, name);
            assertNotNull(equipment);
            verify(mockEquipmentRepo).save(equipment);
            assertEquals(category.trim().toLowerCase(), equipment.getCategory());
            assertEquals(name.trim(), equipment.getName());
            assertTrue(equipment.isEnabled());
            return;
        }
        when(mockEquipmentRepo.findByCategoryAndName(any(), any())).thenReturn(Optional.empty());
        assertThat(assertThrows(IllegalArgumentException.class, () -> service.addEquipment(category, name)))
                .hasMessage(expectedErrorMessage);
        verify(mockEquipmentRepo, never()).save(any());
    }

    @Test
    public void testAddEquipmentDupe() {
        Equipment equipment = new Equipment(100, "Bananas", "scanner", true);
        when(mockEquipmentRepo.findByCategoryAndName("scanner", "Bananas")).thenReturn(Optional.of(equipment));
        assertThat(assertThrows(EntityExistsException.class, () -> service.addEquipment("scanner", "Bananas")))
                .hasMessage("Equipment already exists: (category=\"scanner\", name=\"Bananas\")");
        verify(mockEquipmentRepo).findByCategoryAndName("scanner", "Bananas");
    }

    @ParameterizedTest
    @CsvSource(value={"true,true","true,false", "false,true", "false,false"})
    public void testSetEquipmentEnabled(boolean wasEnabled, boolean enabled) {
        Equipment equipment = new Equipment(10, "scanner", "Bananas", wasEnabled);
        when(mockEquipmentRepo.getById(equipment.getId())).thenReturn(equipment);
        when(mockEquipmentRepo.save(any())).then(Matchers.returnArgument());

        assertSame(equipment, service.setEquipmentEnabled(equipment.getId(), enabled));

        assertEquals(enabled, equipment.isEnabled());
        if (wasEnabled==enabled) {
            verify(mockEquipmentRepo, never()).save(any());
        } else {
            verify(mockEquipmentRepo).save(equipment);
        }
    }
}
