package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.RoiRepo;
import uk.ac.sanger.sccp.stan.request.LabwareRoi;
import uk.ac.sanger.sccp.stan.request.LabwareRoi.RoiResult;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link RoiServiceImp} */
class TestRoiService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private RoiRepo mockRoiRepo;

    @InjectMocks
    private RoiServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testLabwareRois_none() {
        List<String> barcodes = List.of("STAN-1");
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(List.of());
        assertThat(service.labwareRois(barcodes)).isEmpty();
        verify(mockLwRepo).findByBarcodeIn(barcodes);
        verifyNoInteractions(mockRoiRepo);
    }

    @Test
    void testLabwareRois() {
        Sample[] samples = EntityFactory.makeSamples(2);
        int[] sampleIds = Arrays.stream(samples).mapToInt(Sample::getId).toArray();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = {
                EntityFactory.makeLabware(lt, samples),
                EntityFactory.makeEmptyLabware(lt),
                EntityFactory.makeEmptyLabware(lt)
        };
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(Arrays.asList(lws));
        for (int i = 1; i < lws.length; ++i) {
            Slot slot = lws[i].getFirstSlot();
            slot.addSample(samples[0]);
            slot.addSample(samples[1]);
        }
        int[] slotIds = Arrays.stream(lws)
                .flatMap(lw -> lw.getSlots().stream())
                .mapToInt(Slot::getId)
                .toArray();

        List<Roi> rois = List.of(
                new Roi(slotIds[0], sampleIds[0], 10, "Alpha"),
                new Roi(slotIds[1], sampleIds[1], 10, "Beta"),
                new Roi(slotIds[2], sampleIds[0], 11, "Gamma"),
                new Roi(slotIds[2], sampleIds[1], 11, "Delta")
        );
        when(mockRoiRepo.findAllBySlotIdIn(any())).thenReturn(rois);
        List<String> barcodes = List.of("STAN-1", "STAN-2");
        List<LabwareRoi> lwRois = service.labwareRois(barcodes);

        verify(mockLwRepo).findByBarcodeIn(barcodes);
        verify(mockRoiRepo).findAllBySlotIdIn(Arrays.stream(slotIds).boxed().collect(toSet()));

        assertThat(lwRois).hasSameSizeAs(lws);
        UCMap<LabwareRoi> lwRoiMap = UCMap.from(lwRois, LabwareRoi::getBarcode);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        assertThat(lwRoiMap.get(lws[0].getBarcode()).getRois()).containsExactlyInAnyOrder(
                new RoiResult(slotIds[0], A1, sampleIds[0], 10, "Alpha"),
                new RoiResult(slotIds[1], A2, sampleIds[1], 10, "Beta")
        );
        assertThat(lwRoiMap.get(lws[1].getBarcode()).getRois()).containsExactlyInAnyOrder(
                new RoiResult(slotIds[2], A1, sampleIds[0], 11, "Gamma"),
                new RoiResult(slotIds[2], A1, sampleIds[1], 11, "Delta")
        );
        assertThat(lwRoiMap.get(lws[2].getBarcode()).getRois()).isEmpty();
    }

    @Test
    void testToRoiResult() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
        Map<Integer, Slot> slotIdMap = lw.getSlots().stream().collect(inMap(Slot::getId));
        final Address A2 = new Address(1,2);
        Slot slot = lw.getSlot(A2);
        Roi roi = new Roi(slot.getId(), 1000, 2000, "Alpha");
        RoiResult rr = service.toRoiResult(roi, slotIdMap);
        assertEquals(new RoiResult(slot.getId(), A2, 1000, 2000, "Alpha"), rr);
    }
}