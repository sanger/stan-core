package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.DestroyRequest;
import uk.ac.sanger.sccp.stan.request.DestroyResult;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link DestructionServiceImp}
 * @author dr6
 */
public class TestDestructionService {
    private Transactor mockTransactor;
    private LabwareRepo mockLabwareRepo;
    private DestructionRepo mockDestructionRepo;
    private DestructionReasonRepo mockReasonRepo;
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private StoreService mockStoreService;

    private DestructionServiceImp destructionService;

    @BeforeEach
    void setup() {
        mockTransactor = mock(Transactor.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockDestructionRepo = mock(DestructionRepo.class);
        mockReasonRepo = mock(DestructionReasonRepo.class);
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockStoreService = mock(StoreService.class);

        destructionService = spy(new DestructionServiceImp(mockTransactor, mockLabwareRepo, mockDestructionRepo,
                mockReasonRepo, mockLabwareValidatorFactory, mockStoreService));
    }

    @Test
    public void testDestroyAndUnstore() {
        User user = EntityFactory.getUser();
        DestroyRequest request = new DestroyRequest(List.of("STAN-A1"), 1);
        DestroyResult result = new DestroyResult(List.of(new Destruction()));
        doReturn(result).when(destructionService).transactDestroy(any(), any());

        assertSame(result, destructionService.destroyAndUnstore(user, request));

        verify(destructionService).transactDestroy(user, request);
        verify(mockStoreService).discardStorage(user, request.getBarcodes());
    }

    @ParameterizedTest
    @MethodSource("destroyArgs")
    public void testDestroy(DestroyRequest request, List<Labware> labware, DestructionReason reason,
                            Object expected) {
        User user = EntityFactory.getUser();
        Iterable<Labware> updatedLabware;
        if (labware!=null) {
            doReturn(labware).when(destructionService).loadAndValidateLabware(any());
            //noinspection FunctionalExpressionCanBeFolded
            updatedLabware = labware::iterator; // so verify can tell this one from labware
            doReturn(updatedLabware).when(destructionService).destroyLabware(any());
        } else {
            updatedLabware = null;
            doThrow(IllegalArgumentException.class).when(destructionService).loadAndValidateLabware(any());
        }
        if (reason!=null) {
            when(mockReasonRepo.getById(request.getReasonId())).thenReturn(reason);
        } else {
            when(mockReasonRepo.getById(any())).thenThrow(EntityNotFoundException.class);
        }
        if (expected instanceof DestroyResult) {
            doReturn(((DestroyResult) expected).getDestructions())
                    .when(destructionService).recordDestructions(any(), any(), any());

            assertEquals(expected, destructionService.destroy(user, request));

            verify(destructionService).loadAndValidateLabware(request.getBarcodes());
            assert labware != null;
            verify(destructionService).destroyLabware(labware);
            verify(destructionService).recordDestructions(user, reason, updatedLabware);
        } else {
            if (expected instanceof String) {
                assertException(IllegalArgumentException.class, (String) expected, () -> destructionService.destroy(user, request));
            } else {
                //noinspection unchecked
                Class<? extends Exception> cls = (Class<? extends Exception>) expected;
                assertThrows(cls, () -> destructionService.destroy(user, request));
            }
            verify(destructionService, never()).destroyLabware(any());
            verify(destructionService, never()).recordDestructions(any(), any(), any());
        }
    }

    static Stream<Arguments> destroyArgs() {
        DestructionReason reason = new DestructionReason(1, "All wrong.");
        DestructionReason disabledReason = new DestructionReason(1, "We're not ready yet.");
        disabledReason.setEnabled(false);
        List<Labware> labware = List.of(EntityFactory.getTube());
        List<String> barcodes = List.of(labware.get(0).getBarcode());
        DestroyRequest request = new DestroyRequest(barcodes, 1);
        // request, labware, reason, expected
        DestroyResult destroyResult = new DestroyResult(List.of(new Destruction()));
        return Stream.of(
                Arguments.of(new DestroyRequest(null, 1), null, reason, "No barcodes supplied."),
                Arguments.of(new DestroyRequest(List.of(), 1), null, reason, "No barcodes supplied."),
                Arguments.of(new DestroyRequest(barcodes, null), labware, reason, "No reason id supplied."),
                Arguments.of(request, labware, null, EntityNotFoundException.class),
                Arguments.of(request, null, reason, IllegalArgumentException.class),
                Arguments.of(request, labware, disabledReason, "Specified destruction reason is not enabled."),
                Arguments.of(request, labware, reason, destroyResult)
        );
    }

    @Test
    public void testTransactDestroy() {
        User user = EntityFactory.getUser();
        DestroyRequest request = new DestroyRequest(List.of("STAN-A1"), 1);
        DestroyResult result = new DestroyResult(List.of(new Destruction()));
        doReturn(result).when(destructionService).destroy(any(), any());
        when(mockTransactor.transact(any(), any())).then(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });

        assertSame(result, destructionService.transactDestroy(user, request));

        verify(destructionService).destroy(user, request);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testLoadAndValidateLabware(boolean valid) {
        LabwareValidator mockValidator = mock(LabwareValidator.class);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(mockValidator);
        List<Labware> labware = List.of(EntityFactory.getTube());
        List<String> barcodes = List.of(labware.get(0).getBarcode());
        when(mockValidator.loadLabware(any(), any())).thenReturn(labware);

        if (valid) {
            assertEquals(labware, destructionService.loadAndValidateLabware(barcodes));
        } else {
            doThrow(IllegalArgumentException.class).when(mockValidator).throwError(any());
            assertThrows(IllegalArgumentException.class, () -> destructionService.loadAndValidateLabware(barcodes));
        }

        verify(mockValidator).setUsedAllowed(true);
        verify(mockValidator).setUniqueRequired(true);
        verify(mockValidator).loadLabware(mockLabwareRepo, barcodes);
        verify(mockValidator).validateSources();
        verify(mockValidator).throwError(any());
    }

    @Test
    public void testDestroyLabware() {
        List<Labware> labware = List.of(EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()),
                EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()));
        //noinspection FunctionalExpressionCanBeFolded
        Iterable<Labware> savedLw = labware::iterator;
        when(mockLabwareRepo.saveAll(any())).thenReturn(savedLw);
        assertSame(savedLw, destructionService.destroyLabware(labware));
        for (Labware lw : labware) {
            assertTrue(lw.isDestroyed());
        }
        verify(mockLabwareRepo).saveAll(labware);
    }

    @Test
    public void testRecordDestructions() {
        User user = EntityFactory.getUser();
        DestructionReason reason = new DestructionReason(1, "Out of cheese error.");
        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample()))
                .collect(toList());
        List<Destruction> destructions = labware.stream()
                .map(lw -> new Destruction(20+lw.getId(), lw, user, null, null))
                .collect(toList());
        when(mockDestructionRepo.save(any())).thenReturn(destructions.get(0), destructions.get(1));

        assertEquals(destructions, destructionService.recordDestructions(user, reason, labware));
        for (Labware value : labware) {
            verify(mockDestructionRepo).save(new Destruction(null, value, user, null, reason));
        }
    }

    private static void assertException(Class<? extends Exception> exCls, String exMsg, Executable exec) {
        assertThat(assertThrows(exCls, exec)).hasMessage(exMsg);
    }
}
