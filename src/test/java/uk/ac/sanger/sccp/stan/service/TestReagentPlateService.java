package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.ReagentPlateRepo;
import uk.ac.sanger.sccp.stan.repo.ReagentSlotRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ReagentPlateServiceImp}
 * @author dr6
 */
public class TestReagentPlateService {
    private ReagentPlateRepo mockReagentPlateRepo;
    private ReagentSlotRepo mockReagentSlotRepo;
    private Validator<String> mockBarcodeValidator;
    private ReagentPlateServiceImp service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        mockReagentPlateRepo = mock(ReagentPlateRepo.class);
        mockReagentSlotRepo = mock(ReagentSlotRepo.class);
        mockBarcodeValidator = mock(Validator.class);
        service = spy(new ReagentPlateServiceImp(mockReagentPlateRepo, mockReagentSlotRepo, mockBarcodeValidator));
    }

    @Test
    public void testCreateReagentPlate_valid() {
        String barcode = "123";
        String plateType = ReagentPlate.TYPE_FRESH_FROZEN;
        ReagentPlate plate = new ReagentPlate(barcode, plateType);
        when(mockReagentPlateRepo.findByBarcode(barcode)).thenReturn(Optional.empty());
        when(mockReagentPlateRepo.save(any())).thenReturn(plate);
        List<ReagentSlot> rslots = List.of(new ReagentSlot(null, new Address(1,2)));
        doReturn(rslots).when(service).createSlots(any());

        assertSame(plate, service.createReagentPlate(barcode, plateType));

        verify(mockBarcodeValidator).checkArgument(barcode);
        assertSame(rslots, plate.getSlots());
        verify(service).createSlots(plate);
        verify(mockReagentPlateRepo).save(new ReagentPlate(barcode, plateType));
    }

    @Test
    public void testCreateReagentPlate_invalid() {
        String barcode = "123";
        IllegalArgumentException ex = new IllegalArgumentException("Bad barcode");
        doThrow(ex).when(mockBarcodeValidator).checkArgument(barcode);

        assertSame(ex, assertThrows(IllegalArgumentException.class, () -> service.createReagentPlate(barcode, "bananas")));

        verifyNoInteractions(mockReagentPlateRepo);
        verify(service, never()).createSlots(any());
    }

    @Test
    public void testCreateSlots() {
        ReagentPlate plate = new ReagentPlate(500, "123", ReagentPlate.TYPE_FFPE, null);
        ReagentPlateLayout layout = plate.getPlateLayout();
        assertEquals(8, layout.getNumRows());
        assertEquals(12, layout.getNumColumns());
        List<ReagentSlot> savedSlots = List.of(
                new ReagentSlot(10, 500, new Address(1,1), false),
                new ReagentSlot(11, 500, new Address(1,2), false)
        );
        when(mockReagentSlotRepo.saveAll(any())).thenReturn(savedSlots);

        assertEquals(savedSlots, service.createSlots(plate));

        //noinspection unchecked
        ArgumentCaptor<List<ReagentSlot>> slotsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockReagentSlotRepo).saveAll(slotsCaptor.capture());
        List<ReagentSlot> rslots = slotsCaptor.getValue();
        assertThat(rslots).hasSize(96);
        Iterator<ReagentSlot> iter = rslots.iterator();
        for (int row = 1; row <= layout.getNumRows(); ++row) {
            for (int col = 1; col <= layout.getNumColumns(); ++col) {
                ReagentSlot rslot = iter.next();
                assertAddress(row, col, rslot.getAddress());
                assertEquals(plate.getId(), rslot.getPlateId());
                assertFalse(rslot.isUsed());
                assertNull(rslot.getId());
            }
        }
    }

    @Test
    public void testCreateReagentPlate_full() {
        when(mockReagentPlateRepo.save(any())).then(invocation -> {
            ReagentPlate plate = invocation.getArgument(0);
            plate.setId(100);
            return plate;
        });
        when(mockReagentSlotRepo.saveAll(any())).then(invocation -> {
            Iterable<ReagentSlot> inSlots = invocation.getArgument(0);
            List<ReagentSlot> rslots = new ArrayList<>(96);
            int idCounter = 101;
            for (ReagentSlot rslot : inSlots) {
                rslot.setId(idCounter);
                ++idCounter;
                rslots.add(rslot);
            }
            return rslots;
        });

        final String barcode = "123";

        ReagentPlate plate = service.createReagentPlate(barcode, ReagentPlate.TYPE_FFPE);

        assertEquals(barcode, plate.getBarcode());
        final Integer plateId = plate.getId();
        assertEquals(100, plateId);
        List<ReagentSlot> rslots = plate.getSlots();
        int numRows = plate.getPlateLayout().getNumRows();
        int numCols = plate.getPlateLayout().getNumColumns();
        assertEquals(8, numRows);
        assertEquals(12, numCols);
        assertThat(rslots).hasSize(numRows * numCols);
        var iter = rslots.iterator();
        int idCounter = 101;
        for (int row = 1; row <= numRows; ++row) {
            for (int col = 1; col <= numCols; ++col) {
                ReagentSlot rslot = iter.next();
                assertEquals(idCounter, rslot.getId());
                ++idCounter;
                assertAddress(row, col, rslot.getAddress());
                assertEquals(rslot.getPlateId(), plateId);
            }
        }
    }

    @Test
    public void testLoadPlates() {
        ReagentPlate plate1 = new ReagentPlate("001", ReagentPlate.TYPE_FFPE);
        ReagentPlate plate2 = new ReagentPlate("002", ReagentPlate.TYPE_FRESH_FROZEN);
        List<String> barcodes = List.of("001", "002", "003");
        when(mockReagentPlateRepo.findAllByBarcodeIn(any())).thenReturn(List.of(plate1, plate2));
        UCMap<ReagentPlate> map = service.loadPlates(barcodes);
        verify(mockReagentPlateRepo).findAllByBarcodeIn(barcodes);
        assertThat(map).hasSize(2);
        assertSame(plate1, map.get(plate1.getBarcode()));
        assertSame(plate2, map.get(plate2.getBarcode()));
    }

    static void assertAddress(int row, int col, Address address) {
        assertEquals(row, address.getRow());
        assertEquals(col, address.getColumn());
    }
}
