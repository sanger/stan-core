package uk.ac.sanger.sccp.stan.service;

import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LabwareService}
 * @author dr6
 */
public class LabwareServiceTest {
    private LabwareRepo mockLabwareRepo;
    private SlotRepo mockSlotRepo;
    private BarcodeSeedRepo mockBarcodeSeedRepo;
    private EntityManager mockEntityManager;

    private LabwareService labwareService;
    private int idCounter = 1000;
    private List<Labware> savedLabware;
    private List<Slot> savedSlots;

    @BeforeEach
    void setup() {
        mockLabwareRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockBarcodeSeedRepo = mock(BarcodeSeedRepo.class);
        mockEntityManager = mock(EntityManager.class);

        mockLabwareSave();
        mockSlotSave();
        mockRefresh();

        labwareService = spy(new LabwareService(mockEntityManager, mockLabwareRepo, mockSlotRepo, mockBarcodeSeedRepo));
        savedLabware = new ArrayList<>();
        savedSlots = new ArrayList<>();
    }

    void mockLabwareSave() {
        when(mockLabwareRepo.save(any())).then(invocation -> {
            Labware lw = invocation.getArgument(0);
            assertNull(lw.getId());
            lw.setId(++idCounter);
            savedLabware.add(lw);
            return lw;
        });
    }

    void mockSlotSave() {
        when(mockSlotRepo.save(any())).then(invocation -> {
            Slot slot = invocation.getArgument(0);
            assertNull(slot.getId());
            slot.setId(++idCounter);
            savedSlots.add(slot);
            return slot;
        });
    }

    void mockRefresh() {
        doAnswer(invocation -> {
            Labware lw = invocation.getArgument(0);
            final int lwId = lw.getId();
            lw.setSlots(savedSlots.stream().filter(slot -> slot.getLabwareId()==lwId).collect(toList()));
            return null;
        }).when(mockEntityManager).refresh(any(Labware.class));
    }

    @Test
    public void testCreateNoBarcode() {
        String barcode = "STAN-ABC";
        when(mockBarcodeSeedRepo.createStanBarcode()).thenReturn(barcode);
        Labware lw = EntityFactory.getTube();
        doReturn(lw).when(labwareService).create(any(), anyString());
        LabwareType lt = lw.getLabwareType();
        Labware result = labwareService.create(lt);
        assertSame(lw, result);
        verify(labwareService).create(lt, barcode);
    }

    @Test
    public void testCreateWithBarcode() {
        LabwareType lt = EntityFactory.makeLabwareType(2, 3);
        String barcode = "STAN-ABC";
        Labware lw = labwareService.create(lt, barcode);
        assertNotNull(lw.getId());
        assertEquals(lw.getBarcode(), barcode);
        assertEquals(lw.getLabwareType(), lt);
        assertThat(savedLabware).hasSize(1).contains(lw);
        assertThat(savedSlots).hasSameElementsAs(lw.getSlots());
        //noinspection UnstableApiUsage
        Streams.forEachPair(Address.stream(lt.getNumRows(), lt.getNumColumns()), lw.getSlots().stream(),
                (address, slot) -> {
                    assertEquals(address, slot.getAddress());
                    assertEquals(slot.getLabwareId(), lw.getId());
                    assertNotNull(slot.getId());
                });
    }
}
