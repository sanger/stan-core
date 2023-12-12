package uk.ac.sanger.sccp.stan.service.extract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractResult;
import uk.ac.sanger.sccp.stan.request.LabwareFlagged;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ExtractResultQueryService}
 * @author dr6
 */
public class TestExtractResultQueryService {
    private FlagLookupService mockFlagLookupService;
    private LabwareRepo mockLwRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationRepo mockOpRepo;
    private ActionRepo mockActionRepo;
    private ResultOpRepo mockResultOpRepo;
    private MeasurementRepo mockMeasurementRepo;
    private OperationType extractOpType, resultOpType;

    private ExtractResultQueryService service;

    @BeforeEach
    void setup() {
        mockFlagLookupService = mock(FlagLookupService.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockActionRepo = mock(ActionRepo.class);
        mockResultOpRepo = mock(ResultOpRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new ExtractResultQueryService(mockFlagLookupService, mockLwRepo, mockOpTypeRepo, mockOpRepo,
                mockActionRepo, mockResultOpRepo, mockMeasurementRepo));
    }

    private void setupOpTypes() {
        extractOpType = EntityFactory.makeOperationType("Extract", null, OperationTypeFlag.DISCARD_SOURCE);
        resultOpType = EntityFactory.makeOperationType("Record result", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);
        when(mockOpTypeRepo.getByName(extractOpType.getName())).thenReturn(extractOpType);
        when(mockOpTypeRepo.getByName(resultOpType.getName())).thenReturn(resultOpType);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true", "false,", "true,"})
    public void testGetExtractResult(boolean resultExists, Boolean flagged) {
        Labware lw = EntityFactory.getTube();
        boolean loadFlags = (flagged!=null);
        LabwareFlagged lf;
        if (loadFlags) {
            lf = new LabwareFlagged(lw, flagged);
            when(mockFlagLookupService.getLabwareFlagged(lw)).thenReturn(lf);
        } else {
            lf = new LabwareFlagged(lw, false);
        }
        String barcode = lw.getBarcode();
        when(mockLwRepo.getByBarcode(barcode)).thenReturn(lw);
        ExtractResult result = resultExists ? new ExtractResult(lf, PassFail.pass, "200") : null;
        doReturn(result).when(service).findExtractResult(any());
        if (resultExists) {
            assertSame(result, service.getExtractResult(barcode, loadFlags));
        } else {
            assertEquals(new ExtractResult(lf, null, null), service.getExtractResult(barcode, loadFlags));
        }
        if (loadFlags) {
            verify(mockFlagLookupService).getLabwareFlagged(lw);
        } else {
            verifyNoInteractions(mockFlagLookupService);
        }
        verify(mockLwRepo).getByBarcode(barcode);
        verify(service).findExtractResult(lf);
    }

    @Test
    public void testFindExtractResult_foundOnLabware() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        LabwareFlagged lf = new LabwareFlagged(lw, false);
        ResultOp ro = new ResultOp(20, PassFail.pass, 30, 40, 50, 60);
        doReturn(ro).when(service).selectExtractResult(List.of(lw));
        String conc = "20";
        doReturn(conc).when(service).getConcentration(30, List.of(lw));
        assertEquals(new ExtractResult(lf, PassFail.pass, conc), service.findExtractResult(lf));
        verify(service).selectExtractResult(List.of(lw));
        verify(service).getConcentration(ro.getOperationId(), List.of(lw));
        verifyNoInteractions(mockActionRepo);
    }

    @Test
    public void testFindExtractResult_noSources() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        LabwareFlagged lf = new LabwareFlagged(lw, false);
        doReturn(null).when(service).selectExtractResult(List.of(lw));
        when(mockActionRepo.findSourceLabwareIdsForDestinationLabwareIds(any())).thenReturn(List.of());
        assertNull(service.findExtractResult(lf));
        verify(service).selectExtractResult(List.of(lw));
        verify(service, never()).getConcentration(any(), any());
        verify(mockActionRepo).findSourceLabwareIdsForDestinationLabwareIds(List.of(lw.getId()));
        verifyNoInteractions(mockLwRepo);
    }

    @Test
    public void testFindExtractResult_foundOnSources() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        LabwareFlagged lf = new LabwareFlagged(lw, false);
        final LabwareType lt = lw.getLabwareType();
        doReturn(null).when(service).selectExtractResult(List.of(lw));
        List<Labware> sourceLabware = List.of(EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt));
        List<Integer> sourceLwIds = sourceLabware.stream().map(Labware::getId).collect(toList());
        when(mockActionRepo.findSourceLabwareIdsForDestinationLabwareIds(any())).thenReturn(sourceLwIds);
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(sourceLabware);
        ResultOp ro = new ResultOp(20, PassFail.pass, 30, 40, 50, 60);
        doReturn(ro).when(service).selectExtractResult(sourceLabware);
        String conc = "41";
        doReturn(conc).when(service).getConcentration(any(), any());

        assertEquals(new ExtractResult(lf, PassFail.pass, conc), service.findExtractResult(lf));

        verify(service).selectExtractResult(List.of(lw));
        verify(mockActionRepo).findSourceLabwareIdsForDestinationLabwareIds(List.of(lw.getId()));
        verify(mockLwRepo).findAllByIdIn(sourceLwIds);
        verify(service).selectExtractResult(sourceLabware);
        verify(service).getConcentration(ro.getOperationId(), sourceLabware);
    }

    @Test
    public void testFindExtractResult_notFoundOnSources() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        LabwareFlagged lf = new LabwareFlagged(lw, false);
        final LabwareType lt = lw.getLabwareType();
        doReturn(null).when(service).selectExtractResult(any());
        List<Labware> sourceLabware = List.of(EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt));
        List<Integer> sourceLwIds = sourceLabware.stream().map(Labware::getId).collect(toList());
        when(mockActionRepo.findSourceLabwareIdsForDestinationLabwareIds(any())).thenReturn(sourceLwIds);
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(sourceLabware);

        assertNull(service.findExtractResult(lf));

        verify(service).selectExtractResult(List.of(lw));
        verify(mockActionRepo).findSourceLabwareIdsForDestinationLabwareIds(List.of(lw.getId()));
        verify(mockLwRepo).findAllByIdIn(sourceLwIds);
        verify(service).selectExtractResult(sourceLabware);
        verify(service, never()).getConcentration(any(), any());
    }

    @Test
    public void testSelectExtractResult_noOps() {
        setupOpTypes();
        Labware lw = EntityFactory.getTube();
        List<Labware> labware = List.of(lw, EntityFactory.makeEmptyLabware(lw.getLabwareType()));
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(List.of());

        assertNull(service.selectExtractResult(labware));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, labwareIds);
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

        assertNull(service.selectExtractResult(List.of(lw)));
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, Set.of(lw.getId()));
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

        assertNull(service.selectExtractResult(List.of(lw)));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, Set.of(lw.getId()));
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

        assertSame(ros.get(1), service.selectExtractResult(List.of(lw)));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(resultOpType, Set.of(lw.getId()));
        verify(mockResultOpRepo).findAllByOperationIdIn(List.of(10,11,12,13,14,15));
        verify(mockOpRepo).findAllById(Set.of(5,6,7,8));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testGetConcentration(boolean found) {
        Labware lw = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw.getLabwareType());
        List<Labware> labware = List.of(lw, lw2);
        Integer opId = 40;
        Integer slotId = lw.getFirstSlot().getId();
        Integer notSlotId = slotId+1;
        List<Measurement> measurements = List.of(
                new Measurement(1, "Bananas", "Custard", 10, opId, slotId),
                new Measurement(2, "RNA concentration", "30.0", 10, opId, found ? slotId : notSlotId),
                new Measurement(3, "RNA concentration", "40.0", 10, opId, notSlotId)
        );
        when(mockMeasurementRepo.findAllByOperationIdIn(any())).thenReturn(measurements);

        if (found) {
            assertEquals("30.0", service.getConcentration(opId, labware));
        } else {
            assertNull(service.getConcentration(opId, labware));
        }
        verify(mockMeasurementRepo).findAllByOperationIdIn(List.of(opId));
    }
}
