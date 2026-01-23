package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.block.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BlockProcessingServiceImp}
 */
public class TestBlockProcessingService {
    @Mock
    BlockValidatorFactory mockBlockValFactory;
    @Mock
    BlockMakerFactory mockBlockMakerFactory;
    @Mock
    StoreService mockStoreService;
    @Mock
    Transactor mockTransactor;
    @InjectMocks
    BlockProcessingServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @CsvSource({
            "true,true",
            "true,false",
            "false,true",
    })
    void testPerform(boolean success, boolean discard) {
        User user = EntityFactory.getUser();
        TissueBlockRequest request = new TissueBlockRequest();
        List<String> discardBarcodes = discard ? List.of("STAN-1") : List.of();
        request.setDiscardSourceBarcodes(discardBarcodes);
        Matchers.mockTransactor(mockTransactor);
        OperationResult opres;
        List<String> problems;
        if (success) {
            opres = new OperationResult(List.of(new Operation()), List.of());
            problems = null;
            doReturn(opres).when(service).performInsideTransaction(any(), any());
        } else {
            opres = null;
            problems = List.of("Bad");
            doThrow(new ValidationException(problems)).when(service).performInsideTransaction(any(), any());
        }
        if (problems != null) {
            Matchers.assertValidationException(() -> service.perform(user, request), problems);
        } else {
            assertSame(opres, service.perform(user, request));
        }
        verify(service).performInsideTransaction(user, request);
        verify(mockTransactor).transact(eq("Block processing"), any());
        if (success && discard) {
            verify(mockStoreService).discardStorage(user, discardBarcodes);
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    void testPerformInsideTransaction(boolean success) {
        User user = EntityFactory.getUser();
        TissueBlockRequest request = new TissueBlockRequest();
        BlockValidator val = mock(BlockValidator.class);
        when(mockBlockValFactory.createBlockValidator(any())).thenReturn(val);
        BlockMaker maker = mock(BlockMaker.class);
        when(mockBlockMakerFactory.createBlockMaker(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(maker);
        OperationResult opres;
        List<String> problems;
        Medium medium;
        Work work;
        OperationType opType;
        BioState bioState;
        List<BlockLabwareData> lwData;
        if (success) {
            opres = new OperationResult(List.of(new Operation()), List.of());
            problems = null;
            medium = EntityFactory.getMedium();
            work = EntityFactory.makeWork("SGP-1");
            opType = EntityFactory.makeOperationType("Block processing", null);
            bioState = EntityFactory.getBioState();
            lwData = singletonList(null);
            when(val.getMedium()).thenReturn(medium);
            when(val.getWork()).thenReturn(work);
            when(val.getOpType()).thenReturn(opType);
            when(val.getBioState()).thenReturn(bioState);
            when(val.getLwData()).thenReturn(lwData);
            when(maker.record()).thenReturn(opres);
        } else {
            opres = null;
            medium = null;
            work = null;
            opType = null;
            bioState = null;
            lwData = null;
            problems = List.of("Bad");
            doThrow(new ValidationException(problems)).when(val).raiseError();
        }
        if (success) {
            assertSame(opres, service.performInsideTransaction(user, request));
        } else {
            Matchers.assertValidationException(() -> service.performInsideTransaction(user, request), problems);
        }
        InOrder inOrder = inOrder(mockBlockValFactory, mockBlockMakerFactory, val, maker);
        inOrder.verify(mockBlockValFactory).createBlockValidator(request);
        inOrder.verify(val).validate();
        inOrder.verify(val).raiseError();
        if (success) {
            inOrder.verify(mockBlockMakerFactory).createBlockMaker(request, lwData, medium, bioState, work, opType, user);
            inOrder.verify(maker).record();
        } else {
            inOrder.verifyNoMoreInteractions();
        }
    }
}