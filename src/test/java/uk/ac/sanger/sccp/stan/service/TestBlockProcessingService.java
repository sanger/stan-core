package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.block.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BlockProcessingServiceImp}
 */
public class TestBlockProcessingService {
    @Mock
    private BlockValidatorFactory mockBlockValFactory;
    @Mock
    private BlockMakerFactory mockBlockMakerFactory;
    @Mock
    private StoreService mockStoreService;
    @Mock
    private Transactor mockTransactor;

    private BlockProcessingServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new BlockProcessingServiceImp(mockBlockValFactory, mockBlockMakerFactory,
                mockStoreService, mockTransactor));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "true,false",
            "false,true",
            "false,false",
    })
    void testPerforms(boolean success, boolean discarding) {
        TissueBlockRequest request = new TissueBlockRequest();
        request.setDiscardSourceBarcodes(discarding ? List.of("STAN-1", "STAN-2") : List.of());
        Matchers.mockTransactor(mockTransactor);
        OperationResult opres;
        if (success) {
            opres = new OperationResult(List.of(new Operation()), List.of(new Labware()));
            doReturn(opres).when(service).performInsideTransaction(any(), any());
        } else {
            opres = null;
            doThrow(new ValidationException(List.of("Bad request"))).when(service).performInsideTransaction(any(), any());
        }
        User user = EntityFactory.getUser();

        if (success) {
            assertSame(opres, service.perform(user, request));
        } else {
            Matchers.assertValidationException(() -> service.perform(user, request), List.of("Bad request"));
        }
        verify(service).performInsideTransaction(user, request);
        if (success && discarding) {
            verify(mockStoreService).discardStorage(user, request.getDiscardSourceBarcodes());
        } else {
            verifyNoInteractions(mockStoreService);
        }
    }

    @Test
    void testPerformInsideTransaction_noUser() {
        TissueBlockRequest request = new TissueBlockRequest();
        assertThrows(NullPointerException.class, () -> service.performInsideTransaction(null, request), "User is null.");
    }

    @Test
    void testPerformInsideTransaction_noRequest() {
        User user = EntityFactory.getUser();
        assertThrows(NullPointerException.class, () -> service.performInsideTransaction(user, null), "Request is null.");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testPerformInsideTransaction(boolean valid) {
        List<String> problems = valid ? List.of() : List.of("Bad request");
        BlockValidator val = mock(BlockValidator.class);
        User user = EntityFactory.getUser();
        TissueBlockRequest request = new TissueBlockRequest();
        BlockMaker maker;
        OperationResult opres;
        when(mockBlockValFactory.createBlockValidator(any())).thenReturn(val);
        List<BlockLabwareData> lwData;
        Medium medium;
        BioState bs;
        Work work;
        OperationType opType;
        if (!valid) {
            doThrow(new ValidationException(problems)).when(val).raiseError();
            maker = null;
            opres = null;
            lwData = null;
            medium = null;
            bs = null;
            work = null;
            opType = null;
        }  else {
            maker = mock(BlockMaker.class);
            when(mockBlockMakerFactory.createBlockMaker(any(), any(), any(), any(), any(), any(), any())).thenReturn(maker);
            opres = new OperationResult(List.of(new Operation()), List.of(new Labware()));
            when(maker.record()).thenReturn(opres);
            lwData = List.of(new BlockLabwareData(new TissueBlockLabware()));
            medium = EntityFactory.getMedium();
            bs = EntityFactory.getBioState();
            work = EntityFactory.makeWork("SGP1");
            opType = EntityFactory.makeOperationType("opname", null);
            when(val.getLwData()).thenReturn(lwData);
            when(val.getMedium()).thenReturn(medium);
            when(val.getBioState()).thenReturn(bs);
            when(val.getWork()).thenReturn(work);
            when(val.getOpType()).thenReturn(opType);
        }

        if (valid) {
            assertSame(opres, service.performInsideTransaction(user, request));
        } else {
            Matchers.assertValidationException(() -> service.performInsideTransaction(user, request), problems);
        }
        InOrder inOrder = inOrder(val, mockBlockMakerFactory);
        inOrder.verify(val).validate();
        inOrder.verify(val).raiseError();
        if (valid) {
            inOrder.verify(mockBlockMakerFactory).createBlockMaker(request, lwData, medium, bs, work, opType, user);
            verify(maker).record();
        } else {
            verifyNoInteractions(mockBlockMakerFactory);
        }
    }
}