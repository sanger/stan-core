package uk.ac.sanger.sccp.stan.service.history;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.model.taglayout.TagLayout;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;

/**
 * Tests {@link ReagentActionDetailService}
 * @author dr6
 */
public class TestReagentActionDetailService {
    @Mock private Ancestoriser mockAncestoriser;
    @Mock private ReagentActionRepo mockReagentActionRepo;
    @Mock private ReagentPlateRepo mockReagentPlateRepo;
    @Mock private TagLayoutRepo mockTagLayoutRepo;

    private ReagentActionDetailService radService;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        radService = spy(new ReagentActionDetailService(mockAncestoriser, mockReagentActionRepo, mockReagentPlateRepo,
                mockTagLayoutRepo));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
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
        final int layoutId = 17;
        rp1.setTagLayoutId(layoutId);
        rp1.setPlateType(ReagentPlate.REAGENT_PLATE_TYPES.get(0));
        rp2.setPlateType(ReagentPlate.REAGENT_PLATE_TYPES.get(1));
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);
        final Address A1 = new Address(1, 1);
        final Address A2 = new Address(1, 2);
        final Address B1 = new Address(2, 1);
        final Address B2 = new Address(2, 2);
        TagLayout mockLayout = makeMockLayout();
        when(mockTagLayoutRepo.getMapByIdIn(any())).thenReturn(Map.of(layoutId, mockLayout));

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
        verify(mockTagLayoutRepo).getMapByIdIn(sameElements(Set.of(layoutId), false));

        assertThat(result).hasSize(2);
        assertThat(result.get(1)).containsExactlyInAnyOrder(
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A1, A2, lw1.getId(), mockLayout.getTagData(A1)),
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A2, B2, lw1.getId(), mockLayout.getTagData(A2))
        );
        assertThat(result.get(2)).containsExactly(new ReagentActionDetail(rp2.getBarcode(), rp2.getPlateType(), B1, B1, lw2.getId(), null));
        assertNotEquals(result.get(1).get(0).hashCode(), result.get(1).get(1).hashCode());
    }

    @Test
    public void testLoadAncestralReagentTransfers() {
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        List<ReagentActionDetail> rads = List.of(
                new ReagentActionDetail("REA-1", "A+", A1, A1,
                        10, Map.of()),
                new ReagentActionDetail("REA-2", "B+", A1, A2,
                        11, Map.of())
        );
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeLabware(lt, sample, sample);
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample);
        SlotSample ss1 = new SlotSample(lw.getSlot(A1), sample);
        SlotSample ss2 = new SlotSample(lw.getSlot(A2), sample);
        List<SlotSample> inputSS = List.of(ss1, ss2);
        SlotSample ss3 = new SlotSample(lw1.getSlot(A1), sample);
        SlotSample ss4 = new SlotSample(lw1.getSlot(A2), sample);
        Ancestry ancestry = new Ancestry();
        ancestry.put(ss1, Set.of(ss3));
        ancestry.put(ss2, Set.of(ss4));
        when(mockAncestoriser.findAncestry(inputSS)).thenReturn(ancestry);
        doReturn(Map.of(lw.getSlot(A1).getId(), List.of(new ReagentActionDetail("REA-1", "A+", A1, A1,
                10, Map.of()), new ReagentActionDetail("REA-2", "B+", A1, A2,
                        11, Map.of()))))
                .when(radService).loadReagentTransfersForSlotIds(any());

        Map<Integer, List<ReagentActionDetail>> result = radService.loadAncestralReagentTransfers(inputSS);
        assertThat(result).hasSize(1);
        assertEquals(rads, result.get(lw.getSlot(A1).getId()));

        verify(mockAncestoriser).findAncestry(inputSS);
        verify(radService).loadReagentTransfersForSlotIds(Set.of(lw.getSlot(A1).getId(), lw.getSlot(A2).getId()));

    }

    @NotNull
    private TagLayout makeMockLayout() {
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        TagLayout mockLayout = mock(TagLayout.class);
        Map<String, String> tagDataA1 = Map.of("Alpha", "ABC");
        Map<String, String> tagDataA2 = Map.of("Alpha", "XYZ");
        when(mockLayout.getTagData(A1)).thenReturn(tagDataA1);
        when(mockLayout.getTagData(A2)).thenReturn(tagDataA2);
        return mockLayout;
    }

    @Test
    public void testLoadReagentTransfersForSlots() {
        ReagentPlate rp1 = EntityFactory.makeReagentPlate("123");
        ReagentPlate rp2 = EntityFactory.makeReagentPlate("456");
        rp1.setPlateType(ReagentPlate.REAGENT_PLATE_TYPES.get(1));
        rp2.setPlateType(ReagentPlate.REAGENT_PLATE_TYPES.get(0));
        final int layoutId = 18;
        rp1.setTagLayoutId(layoutId);
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sample, sample, sample, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);
        TagLayout mockLayout = makeMockLayout();
        when(mockTagLayoutRepo.getMapByIdIn(any())).thenReturn(Map.of(layoutId, mockLayout));

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
        verify(mockTagLayoutRepo).getMapByIdIn(sameElements(Set.of(layoutId), false));
        assertThat(result).hasSize(2);
        assertEquals(List.of(new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A1, A2, lw1.getId(), mockLayout.getTagData(A1))), result.get(slot1.getId()));
        assertEquals(List.of(
                new ReagentActionDetail(rp1.getBarcode(), rp1.getPlateType(), A2, B2, lw1.getId(), mockLayout.getTagData(A2)),
                new ReagentActionDetail(rp2.getBarcode(), rp2.getPlateType(), B1, B2, lw1.getId(), null)
        ), result.get(slot2.getId()));
        assertNull(result.get(slot3.getId()));
    }
}
