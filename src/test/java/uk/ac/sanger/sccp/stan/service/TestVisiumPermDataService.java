package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.request.ControlType;
import uk.ac.sanger.sccp.stan.request.VisiumPermData;
import uk.ac.sanger.sccp.stan.request.VisiumPermData.AddressPermData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.service.VisiumPermDataService.*;

/**
 * Tests {@link VisiumPermDataService}
 */
public class TestVisiumPermDataService {
    private LabwareRepo mockLwRepo;
    private MeasurementRepo mockMeasurementRepo;
    private VisiumPermDataService service;

    @BeforeEach
    void setUp() {
        mockLwRepo = mock(LabwareRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        service = new VisiumPermDataService(mockLwRepo, mockMeasurementRepo);
    }

    @Test
    public void testLoad() {
        LabwareType lt = EntityFactory.makeLabwareType(3,2);
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(lt, sample, sample, sample);
        Integer sampleId = 50;
        Integer opId = 100;
        final Address A1 = new Address(1,1);
        List<Measurement> measurements = List.of(
                new Measurement(100, PERM_TIME, "120", sampleId, opId, lw.getSlot(A1).getId())
        );
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        when(mockMeasurementRepo.findAllBySlotIdIn(any())).thenReturn(measurements);

        VisiumPermData pd = service.load(lw.getBarcode());
        assertSame(lw, pd.getLabware());
        assertThat(pd.getAddressPermData()).containsExactly(new AddressPermData(A1, 120));
    }

    @Test
    public void testCompilePermData() {
        LabwareType lt = EntityFactory.makeLabwareType(3,2);
        Sample sample = EntityFactory.getSample();
        Integer sam1id = sample.getId();
        Integer sam2id = sam1id+1;
        Labware lw = EntityFactory.makeLabware(lt, sample, sample, sample);
        final Address A1 = new Address(1,1), A2 = new Address(1,2),
                B1 = new Address(2,1);
        List<Measurement> measurements = List.of(
                new Measurement(100, PERM_TIME, "120", sam1id, 80, lw.getSlot(A1).getId()),
                new Measurement(101, PERM_TIME, "120", sam2id, 80, lw.getSlot(A1).getId()),
                new Measurement(102, CONTROL, "positive", sam1id, 80, lw.getSlot(A2).getId()),
                new Measurement(103, CONTROL, "positive", sam1id, 80, lw.getSlot(A2).getId()),
                new Measurement(104, PERM_TIME, "240", sam1id, 80, lw.getSlot(B1).getId()),
                new Measurement(105, PERM_TIME, "240", sam2id, 80, lw.getSlot(B1).getId()),
                new Measurement(110, PERM_TIME, "300", sam1id, 81, lw.getSlot(A1).getId()),
                new Measurement(111, PERM_TIME, "300", sam2id, 81, lw.getSlot(A1).getId()),
                new Measurement(120, SELECTED_TIME, "120", sam1id, 82, lw.getSlot(A1).getId()),
                new Measurement(121, SELECTED_TIME, "120", sam2id, 82, lw.getSlot(A1).getId()),
                new Measurement(130, SELECTED_TIME, "300", sam1id, 83, lw.getSlot(A1).getId()),
                new Measurement(131, SELECTED_TIME, "300", sam2id, 83, lw.getSlot(A1).getId())
        );
        assertThat(service.compilePermData(lw, measurements)).containsExactlyInAnyOrder(
                new AddressPermData(A1, 120),
                new AddressPermData(A2, ControlType.positive),
                new AddressPermData(B1, 240),
                new AddressPermData(A1, 300, null, true)
        );
    }
}
