package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InOrder;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest;
import uk.ac.sanger.sccp.stan.request.ReleaseResult;
import uk.ac.sanger.sccp.stan.service.store.StoreException;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ReleaseService} {@link ReleaseServiceImp implementation}
 * @author dr6
 */
public class TestReleaseService {
    private LabwareRepo mockLabwareRepo;
    private StoreService mockStoreService;
    private ReleaseRepo mockReleaseRepo;
    private ReleaseDetailRepo mockReleaseDetailRepo;
    private EntityManager mockEntityManager;
    private Transactor mockTransactor;

    private ReleaseDestination destination;
    private ReleaseRecipient recipient;
    private User user;
    private Sample sample, sample1;
    private LabwareType labwareType;

    private ReleaseServiceImp service;

    @BeforeEach
    void setup() {
        ReleaseDestinationRepo mockDestinationRepo = mock(ReleaseDestinationRepo.class);
        ReleaseRecipientRepo mockRecipientRepo = mock(ReleaseRecipientRepo.class);
        mockTransactor = mock(Transactor.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockStoreService = mock(StoreService.class);
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockReleaseDetailRepo = mock(ReleaseDetailRepo.class);
        mockEntityManager = mock(EntityManager.class);
        user = new User(10, "user1");
        destination = new ReleaseDestination(20, "Venus");
        recipient = new ReleaseRecipient(30, "Mekon");
        when(mockDestinationRepo.getByName(destination.getName())).thenReturn(destination);
        when(mockRecipientRepo.getByUsername(recipient.getUsername())).thenReturn(recipient);

        sample = EntityFactory.getSample();
        sample1 = new Sample(sample.getId()+1, 7, sample.getTissue(), EntityFactory.getBioState());
        labwareType = EntityFactory.makeLabwareType(1,4);

        service = spy(new ReleaseServiceImp(mockTransactor, mockDestinationRepo, mockRecipientRepo, mockLabwareRepo, mockStoreService,
                mockReleaseRepo, mockReleaseDetailRepo, mockEntityManager));

        when(mockTransactor.transact(any(), any())).then(invocation -> {
            Supplier<List<Release>> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Test
    public void testReleaseAndUnstore() {
        Labware lw1 = EntityFactory.makeLabware(labwareType, sample, sample, sample1);
        Labware lw2 = EntityFactory.makeLabware(labwareType, sample1);
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        List<String> barcodes = List.of(lw1.getBarcode(), lw2.getBarcode());
        ReleaseRequest request = new ReleaseRequest(barcodes, "Venus", "Mekon");
        List<Release> releases = List.of(new Release(1, lw1, user, destination, recipient, timestamp),
                new Release(2, lw2, user, destination, recipient, timestamp));

        doReturn(releases).when(service).release(any(), any());
        doNothing().when(service).unstore(any(), any());

        assertEquals(new ReleaseResult(releases), service.releaseAndUnstore(user, request));

        verify(service).release(user, request);
        verify(service).unstore(user, barcodes);
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testTransactRelease(final boolean successful) {
        ReleaseRequest request = new ReleaseRequest();

        IllegalArgumentException exception;
        List<Release> releases;
        if (successful) {
            exception = null;
            releases = List.of();
            doReturn(releases).when(service).release(any(), any());
        } else {
            exception = new IllegalArgumentException("Bad.");
            releases = List.of();
            doThrow(exception).when(service).release(any(), any());
        }

        if (successful) {
            assertEquals(releases, service.transactRelease(user, request));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.transactRelease(user, request)))
                    .hasMessage(exception.getMessage());
        }

        verify(mockTransactor).transact(anyString(), any());
        verify(service).release(user, request);
    }

    @ParameterizedTest
    @MethodSource("releaseArgs")
    public void testRelease(Integer numBarcodes) {
        List<Labware> labware;
        List<String> barcodes;
        if (numBarcodes==null) {
            barcodes = null;
            labware = null;
        } else if (numBarcodes <= 0) {
            barcodes = List.of();
            labware = null;
        } else {
            LabwareType lt = EntityFactory.getTubeType();
            labware = IntStream.range(0, numBarcodes)
                    .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                    .collect(toList());
            barcodes = labware.stream().map(Labware::getBarcode).collect(toList());
        }
        ReleaseRequest request = new ReleaseRequest(barcodes, destination.getName(), recipient.getUsername());

        if (numBarcodes==null || numBarcodes <= 0) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.release(user, request)))
                    .hasMessage("No barcodes supplied to release.");
            return;
        }
        doReturn(labware).when(service).loadLabware(any());
        doNothing().when(service).validateLabware(any());
        doReturn(labware).when(service).updateReleasedLabware(any());
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        List<Release> releases = labware.stream()
                .map(lw -> new Release(10+lw.getId(), lw, user, destination, recipient, timestamp))
                .collect(toList());
        doReturn(releases).when(service).recordReleases(any(), any(), any(), any());

        assertSame(releases, service.release(user, request));

        verify(service).loadLabware(barcodes);
        verify(service).validateLabware(labware);
        verify(service).updateReleasedLabware(labware);
        verify(service).recordReleases(user, destination, recipient, labware);
    }

    static Stream<Integer> releaseArgs() {
        return Stream.of(null, 0, 1, 2);
    }

    @ParameterizedTest
    @MethodSource("loadLabwareArgs")
    public void testLoadLabware(Collection<String> barcodes, Collection<Labware> labware, String expectedErrorMessage) {
        when(mockLabwareRepo.findByBarcodeIn(any())).then(invocation -> {
            Collection<String> barg = invocation.getArgument(0);
            return labware.stream().filter(lw -> barg.stream().anyMatch(lw.getBarcode()::equalsIgnoreCase))
                    .collect(toList());
        });

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.loadLabware(barcodes)))
                    .hasMessage(expectedErrorMessage);
            return;
        }
        assertThat(service.loadLabware(barcodes)).hasSameElementsAs(labware);
    }

    static Stream<Arguments> loadLabwareArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .collect(toList());
        List<String> barcodes = labware.stream().map(Labware::getBarcode).collect(toList());
        return Stream.of(
                Arguments.of(barcodes, labware, null),
                Arguments.of(barcodes.stream().map(String::toLowerCase).collect(toList()), labware, null),
                Arguments.of(List.of(barcodes.get(0), barcodes.get(1), barcodes.get(0).toLowerCase()),
                        labware, "Repeated barcodes: ["+barcodes.get(0)+"]"),
                Arguments.of(Arrays.asList(barcodes.get(0), null), labware,
                        "null is not a valid barcode."),
                Arguments.of(List.of(barcodes.get(0), "BANANAS"), labware, "Unknown labware barcodes: [BANANAS]")
        );
    }

    @ParameterizedTest
    @MethodSource("validateLabwareArgs")
    public void testValidateLabware(Collection<Labware> labware, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            service.validateLabware(labware);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.validateLabware(labware)))
                    .hasMessage(expectedErrorMessage);
        }
    }

    static Stream<Arguments> validateLabwareArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware good = EntityFactory.makeLabware(lt, sample);
        Labware empty = EntityFactory.makeEmptyLabware(lt);
        Labware destroyed = EntityFactory.makeLabware(lt, sample);
        destroyed.setDestroyed(true);
        Labware released = EntityFactory.makeLabware(lt, sample);
        released.setReleased(true);

        String emptyError = "Cannot release empty labware: [" + empty.getBarcode()+"]";
        String releasedError = "Labware has already been released: ["+released.getBarcode()+"]";
        String destroyedError = "Labware cannot be released because it is destroyed: ["+destroyed.getBarcode()+"]";
        return Stream.of(
                Arguments.of(List.of(good), null),
                Arguments.of(List.of(good, empty), emptyError),
                Arguments.of(List.of(empty, good), emptyError),
                Arguments.of(List.of(destroyed), destroyedError),
                Arguments.of(List.of(good, destroyed), destroyedError),
                Arguments.of(List.of(released), releasedError)
        );
    }

    @Test
    public void testRecordReleases() {
        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(labwareType, sample, sample1))
                .collect(toList());
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        List<Release> releases = labware.stream()
                .map(lw -> new Release(10, lw, user, destination, recipient, timestamp))
                .collect(toList());

        doReturn(releases.get(0)).when(service).recordRelease(user, destination, recipient, labware.get(0));
        doReturn(releases.get(1)).when(service).recordRelease(user, destination, recipient, labware.get(1));

        assertEquals(releases, service.recordReleases(user, destination, recipient, labware));
    }

    @Test
    public void testRecordRelease() {
        Labware lw = EntityFactory.makeEmptyLabware(labwareType);
        lw.getSlots().get(0).getSamples().add(sample);
        lw.getSlots().get(0).getSamples().add(sample1);
        lw.getSlots().get(1).getSamples().add(sample1);

        final int releaseId = 10;
        Release release = new Release(releaseId, lw, user, destination, recipient, new Timestamp(System.currentTimeMillis()));

        when(mockReleaseRepo.save(any())).thenReturn(release);

        assertSame(release, service.recordRelease(user, destination, recipient, lw));

        final int slot0Id = lw.getSlots().get(0).getId();
        final int slot1Id = lw.getSlots().get(1).getId();
        final int sampleId = sample.getId();
        final int sample1Id = sample1.getId();
        List<ReleaseDetail> expectedDetails = List.of(
                new ReleaseDetail(null, releaseId, slot0Id, sampleId),
                new ReleaseDetail(null, releaseId, slot0Id, sample1Id),
                new ReleaseDetail(null, releaseId, slot1Id, sample1Id)
        );

        InOrder order = inOrder(mockReleaseRepo, mockReleaseDetailRepo, mockEntityManager);

        order.verify(mockReleaseRepo).save(new Release(lw, user, destination, recipient));
        order.verify(mockReleaseDetailRepo).saveAll(expectedDetails);
        order.verify(mockEntityManager).refresh(release);
    }

    @Test
    public void testUpdateReleasedLabware() {
        List<Labware> labware = List.of(
                EntityFactory.makeLabware(labwareType, sample),
                EntityFactory.makeLabware(labwareType, sample1)
        );
        List<Labware> savedLabware = List.copyOf(labware);
        when(mockLabwareRepo.saveAll(labware)).thenReturn(savedLabware);

        assertSame(savedLabware, service.updateReleasedLabware(labware));
        labware.forEach(lw -> assertTrue(lw.isReleased()));
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testUnstore(boolean successful) {
        List<String> barcodes = List.of("STAN-001", "STAN-002");
        if (!successful) {
            when(mockStoreService.unstoreBarcodesWithoutValidatingThem(any(), any())).thenThrow(StoreException.class);
        }

        service.unstore(user, barcodes);
        verify(mockStoreService).unstoreBarcodesWithoutValidatingThem(user, barcodes);
    }

}
