package uk.ac.sanger.sccp.stan.service.extract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ExtractResultQueryService}
 * @author dr6
 */
public class TestExtractResultQueryService {
    private LabwareRepo mockLwRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationRepo mockOpRepo;
    private ResultOpRepo mockResultOpRepo;
    private MeasurementRepo mockMeasurementRepo;
    private OperationType extractOpType, resultOpType;

    private ExtractResultQueryService service;

    @BeforeEach
    void setup() {
        mockLwRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockResultOpRepo = mock(ResultOpRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new ExtractResultQueryService(mockLwRepo, mockOpTypeRepo, mockOpRepo, mockResultOpRepo, mockMeasurementRepo));
    }

    private void setupOpTypes() {
        extractOpType = EntityFactory.makeOperationType("Extract", null, OperationTypeFlag.DISCARD_SOURCE);
        resultOpType = EntityFactory.makeOperationType("Record result", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        when(mockOpTypeRepo.getByName(extractOpType.getName())).thenReturn(extractOpType);
        when(mockOpTypeRepo.getByName(resultOpType.getName())).thenReturn(resultOpType);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testGetExtractResult(boolean resultExists) {
        Labware lw = EntityFactory.getTube();
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        ResultOp ro = resultExists ? new ResultOp(4, PassFail.pass, 10, 12, 13, 14) : null;
        doReturn(ro).when(service).selectExtractResult(any());
        doReturn("700").when(service).getConcentration(any(), any());

        ExtractResult xr = service.getExtractResult(lw.getBarcode());
        verify(service).selectExtractResult(lw);
        if (resultExists) {
            assertEquals(new ExtractResult(lw, PassFail.pass, "700"), xr);
            verify(service).getConcentration(10, lw);
        } else {
            assertEquals(new ExtractResult(lw, null, null), xr);
            verify(service, never()).getConcentration(any(), any());
        }
    }

    @Test
    public void testSelectExtractResult_noOps() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(List.of());

        assertNull(service.selectExtractResult(lw));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, List.of(lw.getId()));
        verifyNoInteractions(mockResultOpRepo);
    }

    @Test
    public void testSelectExtractResult_noMatchingResultOps() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        List<Operation> ops = List.of(
                new Operation(10, resultOpType, null, null, null),
                new Operation(11, resultOpType, null, null, null)
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        List<ResultOp> ros = List.of(
                new ResultOp(1, PassFail.pass, 10, 20, -1, 20),
                new ResultOp(2, PassFail.fail, 11, 30, -2, 30)
        );
        when(mockResultOpRepo.findAllByOperationIdIn(any())).thenReturn(ros);

        assertNull(service.selectExtractResult(lw));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, List.of(lw.getId()));
        verify(mockResultOpRepo).findAllByOperationIdIn(List.of(10,11));
        verifyNoMoreInteractions(mockOpRepo);
    }

    @Test
    public void testSelectExtractResult_noExtractOps() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        Integer slotId = lw.getFirstSlot().getId();
        List<Operation> ops = List.of(
                new Operation(10, resultOpType, null, null, null),
                new Operation(11, resultOpType, null, null, null)
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        List<ResultOp> ros = List.of(
                new ResultOp(1, PassFail.pass, 10, 20, slotId, 5),
                new ResultOp(2, PassFail.fail, 11, 30, slotId, 6)
        );
        when(mockResultOpRepo.findAllByOperationIdIn(any())).thenReturn(ros);

        OperationType otherOpType = EntityFactory.makeOperationType("Bananas", null);

        List<Operation> referredOps = List.of(
                new Operation(5, otherOpType, null, null, null),
                new Operation(6, otherOpType, null, null, null)
        );
        when(mockOpRepo.findAllById(any())).thenReturn(referredOps);

        assertNull(service.selectExtractResult(lw));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, List.of(lw.getId()));
        verify(mockResultOpRepo).findAllByOperationIdIn(List.of(10,11));
        verify(mockOpRepo).findAllById(Set.of(5,6));
        verifyNoMoreInteractions(mockOpRepo);
    }

    private static LocalDateTime time(int offset) {
        return LocalDateTime.of(2021, 10, offset, 12, 0);
    }

    @Test
    public void testSelectExtractResult_successful() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        Integer slotId = lw.getFirstSlot().getId();
        List<Operation> ops = List.of(
                new Operation(10, resultOpType, time(1), null, null),
                new Operation(11, resultOpType, time(2), null, null),
                new Operation(12, resultOpType, time(3), null, null),
                new Operation(13, resultOpType, time(4), null, null),
                new Operation(14, resultOpType, time(5), null, null),
                new Operation(15, resultOpType, time(6), null, null)
        );
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        List<ResultOp> ros = List.of(
                new ResultOp(1, PassFail.pass, 10, 20, slotId, 5),
                new ResultOp(2, PassFail.fail, 11, 30, slotId, 6),
                new ResultOp(3, PassFail.pass, 12, 30, slotId, 7),
                new ResultOp(4, PassFail.pass, 13, 30, slotId, 8),
                new ResultOp(5, PassFail.fail, 14, 30, -1, 9)
        );
        when(mockResultOpRepo.findAllByOperationIdIn(any())).thenReturn(ros);

        OperationType otherOpType = EntityFactory.makeOperationType("Bananas", null);

        List<Operation> referredOps = List.of(
                new Operation(5, extractOpType, null, null, null),
                new Operation(6, extractOpType, null, null, null),
                new Operation(7, otherOpType, null, null, null)
        );
        when(mockOpRepo.findAllById(any())).thenReturn(referredOps);

        assertSame(ros.get(1), service.selectExtractResult(lw));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, List.of(lw.getId()));
        verify(mockResultOpRepo).findAllByOperationIdIn(List.of(10,11,12,13,14,15));
        verify(mockOpRepo).findAllById(Set.of(5,6,7,8));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testGetConcentration(boolean found) {
        Labware lw = EntityFactory.getTube();
        Integer opId = 40;
        Integer slotId = lw.getFirstSlot().getId();
        Integer notSlotId = slotId+1;
        List<Measurement> measurements = List.of(
                new Measurement(1, "Bananas", "Custard", 10, opId, slotId),
                new Measurement(2, "Concentration", "30.0", 10, opId, found ? slotId : notSlotId),
                new Measurement(3, "Concentration", "40.0", 10, opId, notSlotId)
        );
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).thenReturn(measurements);

        if (found) {
            assertEquals("30.0", service.getConcentration(opId, lw));
        } else {
            assertNull(service.getConcentration(opId, lw));
        }
        verify(mockMeasurementRepo).findAllByOperationIdIn(List.of(opId));
    }
}
