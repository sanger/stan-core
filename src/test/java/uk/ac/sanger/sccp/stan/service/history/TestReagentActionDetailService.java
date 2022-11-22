package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.ReagentActionRepo;
import uk.ac.sanger.sccp.stan.repo.ReagentPlateRepo;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ReagentActionDetailService}
 * @author dr6
 */
public class TestReagentActionDetailService {
    private ReagentActionRepo mockReagentActionRepo;
    private ReagentPlateRepo mockReagentPlateRepo;

    private ReagentActionDetailService radService;

    @BeforeEach
    void setup() {
        mockReagentActionRepo = mock(ReagentActionRepo.class);
        mockReagentPlateRepo = mock(ReagentPlateRepo.class);

        radService = new ReagentActionDetailService(mockReagentActionRepo, mockReagentPlateRepo);
    }

    @Test
    public void testLoadReagentTransfers_empty() {
        when(mockReagentActionRepo.findAllByOperationIdIn(any())).thenReturn(List.of());
        Set<Integer> opIds = Set.of(1,2,3);
        assertThat(radService.loadReagentTransfers(opIds)).isEmpty();
        verify(mockReagentActionRepo).findAllByOperationIdIn(opIds);
        verifyNoInteractions(mockReagentPlateRepo);
    }

    @Test
    public void testLoadReagentTransfers() {
        Set<Integer> opIds = Set.of(1,2,3);
        ReagentPlate rp1 = EntityFactory.makeReagentPlate("123");
        ReagentPlate rp2 = EntityFactory.makeReagentPlate("456");
        rp1.setPlateType(ReagentPlate.TYPE_FRESH_FROZEN);
        rp2.setPlateType(ReagentPlate.TYPE_FFPE);
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);

        final Address A1 = new Address(1, 1);
        final Address A2 = new Address(1, 2);
        final Address B1 = new Address(2, 1);
        final Address B2 = new Address(2, 2);
        List<ReagentAction> ras = List.of(
                new ReagentAction(10, 1, rp1.getSlot(A1), lw1.getSlot(A2)),
                new ReagentAction(11, 1, rp1.getSlot(A2), lw1.getSlot(B2)),
                new ReagentAction(12, 2, rp2.getSlot(B1), lw2.getSlot(B1))
        );
        when(mockReagentActionRepo.findAllByOperationIdIn(any())).thenReturn(ras);
        when(mockReagentPlateRepo.findAllById(any())).thenReturn(List.of(rp1, rp2));

        var result = radService.loadReagentTransfers(opIds);

        verify(mockReagentActionRepo).findAllByOperationIdIn(opIds);
        verify(mockReagentPlateRepo).findAllById(Set.of(rp1.getId(), rp2.getId()));

        assertThat(result).hasSize(2);
        assertThat(result.get(1)).containsExactlyInAnyOrder(
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A1, A2, lw1.getId()),
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A2, B2, lw1.getId())
        );
        assertThat(result.get(2)).containsExactly(new ReagentActionDetail(rp2.getBarcode(), rp2.getPlateType(), B1, B1, lw2.getId()));
        assertNotEquals(result.get(1).get(0).hashCode(), result.get(1).get(1).hashCode());
    }

    @Test
    public void testLoadReagentTransfersForSlots() {
        ReagentPlate rp1 = EntityFactory.makeReagentPlate("123");
        ReagentPlate rp2 = EntityFactory.makeReagentPlate("456");
        rp1.setPlateType(ReagentPlate.TYPE_FFPE);
        rp2.setPlateType(ReagentPlate.TYPE_FRESH_FROZEN);
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);

        final Address A1 = new Address(1, 1);
        final Address A2 = new Address(1, 2);
        final Address B1 = new Address(2, 1);
        final Address B2 = new Address(2, 2);

        final Slot slot1 = lw1.getSlot(A2);
        final Slot slot2 = lw1.getSlot(B2);
        final Slot slot3 = lw2.getSlot(B1);
        List<ReagentAction> ras = List.of(
                new ReagentAction(10, 1, rp1.getSlot(A1), slot1),
                new ReagentAction(11, 1, rp1.getSlot(A2), slot2),
                new ReagentAction(12, 2, rp2.getSlot(B1), slot2)
        );
        when(mockReagentActionRepo.findAllByDestinationIdIn(any())).thenReturn(ras);
        when(mockReagentPlateRepo.findAllById(any())).thenReturn(List.of(rp1, rp2));

        Set<Integer> slotIds = Stream.of(slot1, slot2, slot3).map(Slot::getId).collect(toSet());

        var result = radService.loadReagentTransfersForSlotIds(slotIds);

        verify(mockReagentActionRepo).findAllByDestinationIdIn(slotIds);
        verify(mockReagentPlateRepo).findAllById(Set.of(rp1.getId(), rp2.getId()));
        assertThat(result).hasSize(2);
        assertEquals(List.of(new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A1, A2, lw1.getId())), result.get(slot1.getId()));
        assertEquals(List.of(
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A2, B2, lw1.getId()),
                new ReagentActionDetail(rp2.getBarcode(), rp2.getPlateType(), B1, B2, lw1.getId())
        ), result.get(slot2.getId()));
        assertNull(result.get(slot3.getId()));
    }
}
