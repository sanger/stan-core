package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.config.StanConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.BasicLocation;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest.ReleaseLabware;
import uk.ac.sanger.sccp.stan.request.ReleaseResult;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * Tests {@link ReleaseService} {@link ReleaseServiceImp implementation}
 * @author dr6
 */
public class TestReleaseService {
    private StanConfig mockStanConfig;
    private EntityManager mockEntityManager;
    private ReleaseDestinationRepo mockDestinationRepo;
    private ReleaseRecipientRepo mockRecipientRepo;
    private LabwareRepo mockLabwareRepo;
    private StoreService mockStoreService;
    private ReleaseRepo mockReleaseRepo;
    private Transactor mockTransactor;
    private SnapshotService mockSnapshotService;

    private ReleaseDestination destination;
    private ReleaseRecipient recipient;
    private List<ReleaseRecipient> otherRecs;
    private User user;
    private Sample sample, sample1;
    private LabwareType labwareType;
    private EmailService mockEmailService;
    private WorkService mockWorkService;

    private ReleaseServiceImp service;

    @BeforeEach
    void setup() {
        mockStanConfig = mock(StanConfig.class);
        mockEntityManager = mock(EntityManager.class);
        mockDestinationRepo = mock(ReleaseDestinationRepo.class);
        mockRecipientRepo = mock(ReleaseRecipientRepo.class);
        mockTransactor = mock(Transactor.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockStoreService = mock(StoreService.class);
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockSnapshotService = mock(SnapshotService.class);
        user = EntityFactory.getUser();
        destination = new ReleaseDestination(20, "Venus");
        recipient = new ReleaseRecipient(30, "Mekon");
        when(mockDestinationRepo.getByName(destination.getName())).thenReturn(destination);
        when(mockRecipientRepo.getByUsername(recipient.getUsername())).thenReturn(recipient);
        mockEmailService = mock(EmailService.class);
        mockWorkService = mock(WorkService.class);

        sample = EntityFactory.getSample();
        sample1 = new Sample(sample.getId()+1, 7, sample.getTissue(), EntityFactory.getBioState());
        labwareType = EntityFactory.makeLabwareType(1,4);
        otherRecs = List.of(new ReleaseRecipient(7, "ford"));

        service = spy(new ReleaseServiceImp(mockStanConfig, mockTransactor, mockEntityManager,
                mockDestinationRepo, mockRecipientRepo, mockLabwareRepo, mockStoreService,
                mockReleaseRepo, mockSnapshotService, mockEmailService, mockWorkService));

        when(mockTransactor.transact(any(), any())).then(invocation -> {
            Supplier<List<Release>> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @ParameterizedTest
    @MethodSource("releaseAndUnstoreArgs")
    public void testReleaseAndUnstore(ReleaseRequest request, ReleaseRecipient recipient,
                                      ReleaseDestination destination, List<Labware> labware,
                                      String loadLabwareError, String labwareValidationError, String labwareContentsError,
                                      String expectedExceptionMessage) {
        if (recipient!=null) {
            when(mockRecipientRepo.getByUsername(request.getRecipient())).thenReturn(recipient);
        } else {
            when(mockRecipientRepo.getByUsername(any())).thenThrow(new EntityNotFoundException("Recipient not found."));
        }

        if (destination!=null) {
            when(mockDestinationRepo.getByName(request.getDestination())).thenReturn(destination);
        } else {
            when(mockDestinationRepo.getByName(any())).thenThrow(new EntityNotFoundException("Destination not found."));
        }

        if (loadLabwareError!=null) {
            doThrow(new IllegalArgumentException(loadLabwareError)).when(service).loadLabware(any());
        } else if (labware!=null && recipient!=null && destination != null) {
            doReturn(labware).when(service).loadLabware(any());

            if (labwareValidationError!=null) {
                doThrow(new IllegalArgumentException(labwareValidationError)).when(service).validateLabware(any());
            } else {
                doNothing().when(service).validateLabware(any());
                if (labwareContentsError!=null) {
                    doThrow(new IllegalArgumentException(labwareContentsError)).when(service).validateContents(any());
                } else {
                    doNothing().when(service).validateContents(any());
                }
            }
        }

        if (expectedExceptionMessage!=null) {
            var ex = assertThrows(Exception.class, () -> service.releaseAndUnstore(user, request));
            assertThat(ex).hasMessage(expectedExceptionMessage);
            verifyNoInteractions(mockStoreService);
            verify(service, never()).transactRelease(any(), any(), any(), any(), any(), any(), any());
            return;
        }
        assert labware != null;

        UCMap<BasicLocation> locations = new UCMap<>(1);
        locations.put(labware.get(0).getBarcode(), new BasicLocation("STO-123", new Address(1,2)));
        when(mockStoreService.loadBasicLocationsOfItems(any())).thenReturn(locations);
        Work work = new Work();
        work.setWorkNumber("SGP1");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);
        doReturn(workMap).when(service).loadWork(any());

        List<Release> releases = List.of(
                new Release(labware.get(0), user, destination, recipient, 100)
        );
        for (int i = 0; i < releases.size(); ++i) {
            releases.get(i).setId(100+i);
        }
        doReturn(otherRecs).when(service).loadOtherRecipients(any());
        doReturn(releases).when(service).transactRelease(any(), any(), any(), any(), any(), any(), any());
        String releaseFilePath = "root/release?id=1,2,3";
        assert recipient != null;
        String recEmail = recipient.getUsername();
        if (recEmail.indexOf('@') < 0) {
            recEmail += "@sanger.ac.uk";
        }
        doReturn(releaseFilePath).when(service).releaseFileLink(any());

        ReleaseResult result = service.releaseAndUnstore(user, request);
        List<String> expectedBarcodes = request.getReleaseLabware().stream()
                .map(ReleaseLabware::getBarcode)
                .collect(toList());

        verify(service).loadLabware(sameElements(expectedBarcodes, true));
        verify(service).validateLabware(labware);
        verify(service).validateContents(labware);
        verify(service).loadWork(request.getReleaseLabware());
        verify(service).loadOtherRecipients(request.getOtherRecipients());
        verify(mockEmailService).tryReleaseEmail(recEmail, List.of("ford@sanger.ac.uk"), List.of("SGP1"), releaseFilePath);
        verify(mockStoreService).loadBasicLocationsOfItems(labware.stream().map(Labware::getBarcode).collect(toList()));
        verify(service).transactRelease(user, recipient, otherRecs, destination, labware, locations, workMap);
        verify(mockStoreService).discardStorage(same(user), sameElements(expectedBarcodes, true));
        assertEquals(result, new ReleaseResult(releases));
    }

    static Stream<Arguments> releaseAndUnstoreArgs() {
        ReleaseRecipient rec = new ReleaseRecipient(10, "dr6");
        ReleaseDestination dest = new ReleaseDestination(20, "Moon");
        ReleaseRecipient disRec = new ReleaseRecipient(10, "dr6");
        disRec.setEnabled(false);
        ReleaseDestination disDest = new ReleaseDestination(20, "Moon");
        disDest.setEnabled(false);
        List<Labware> lws = List.of(EntityFactory.getTube());
        ReleaseRequest request = new ReleaseRequest(List.of(new ReleaseLabware(lws.get(0).getBarcode())), dest.getName(), rec.getUsername());
        request.setOtherRecipients(List.of("ford"));
        return Arrays.stream(new Object[][] {
                {request, rec, dest, lws, null, null, null, null},
                {request, null, dest, null, null, null, null, "Recipient not found."},
                {request, rec, null, null, null, null, null, "Destination not found."},
                {new ReleaseRequest(List.of(), dest.getName(), rec.getUsername()),
                   rec, dest, null, null, null, null, "No labware specified to release."},
                {request, rec, disDest, null, null, null, null, "Release destination Moon is not enabled."},
                {request, disRec, dest, null, null, null, null, "Release recipient dr6 is not enabled."},
                {request, rec, dest, null, "Bad barcodes.", null, null, "Bad barcodes."},
                {request, rec, dest, lws, null, "Bad labware.", null, "Bad labware."},
                {request, rec, dest, lws, null, null, "Bad contents.", "Bad contents."},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("loadOtherRecipientsArgs")
    public void testLoadOtherRecipients(Collection<String> usernames,
                                        Object repoResult,
                                        boolean anyRepeats,
                                        String expectedError) {
        if (repoResult instanceof Exception) {
            Exception dbException = (Exception) repoResult;
            when(mockRecipientRepo.getAllByUsernameIn(any())).thenThrow(dbException);
            assertSame(dbException, assertThrows(Exception.class, () -> service.loadOtherRecipients(usernames)));
            verify(mockRecipientRepo).getAllByUsernameIn(usernames);
            return;
        }
        if (nullOrEmpty(usernames)) {
            assertThat(service.loadOtherRecipients(usernames)).isEmpty();
            verifyNoInteractions(mockRecipientRepo);
            return;
        }
        //noinspection unchecked
        List<ReleaseRecipient> dbRecs = nullToEmpty((List<ReleaseRecipient>) repoResult);
        when(mockRecipientRepo.getAllByUsernameIn(any())).thenReturn(dbRecs);

        if (expectedError!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.loadOtherRecipients(usernames)))
                    .hasMessage(expectedError);
        } else if (anyRepeats) {
            List<ReleaseRecipient> result = service.loadOtherRecipients(usernames);
            assertThat(result).containsOnlyOnceElementsOf(dbRecs);
            assertThat(result).containsAll(dbRecs);
        } else {
            assertThat(service.loadOtherRecipients(usernames)).containsExactlyInAnyOrderElementsOf(dbRecs);
        }
        verify(mockRecipientRepo).getAllByUsernameIn(usernames);
    }

    static Stream<Arguments> loadOtherRecipientsArgs() {
        ReleaseRecipient rec1 = new ReleaseRecipient(1, "rec1");
        ReleaseRecipient rec2 = new ReleaseRecipient(2, "rec2");
        ReleaseRecipient rec3 = new ReleaseRecipient(3, "rec3");
        ReleaseRecipient rec4 = new ReleaseRecipient(4, "rec4");
        rec3.setEnabled(false);
        rec4.setEnabled(false);
        return Arrays.stream(new Object[][] {
                {null,null,false,null},
                {List.of(),null,false,null},
                {List.of("rec1", "rec2"), List.of(rec1, rec2), false, null},
                {List.of("rec1", "rec404"), new EntityNotFoundException("No such recipient"), false, null},
                {List.of("rec1", "rec1", "rec2"), List.of(rec1, rec1, rec2), true, null},
                {List.of("rec1", "rec3", "rec4"), List.of(rec1, rec3, rec4), false, "Other recipients disabled: [rec3, rec4]"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @CsvSource({
            "alpha, alpha@sanger.ac.uk",
            "x@y.com, x@y.com"
    })
    public void testCanonicaliseEmail(String input, String expected) {
        assertEquals(expected, service.canonicaliseEmail(input));
    }

    @Test
    public void testReleaseFileLink() {
        when(mockStanConfig.getRoot()).thenReturn("stanroot/");
        List<Release> releases = List.of(new Release(), new Release(), new Release());
        for (int i = 0; i < 3; ++i) {
            releases.get(i).setId(10+i);
        }
        assertEquals("stanroot/release?id=10,11,12", service.releaseFileLink(releases));
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testTransactRelease(final boolean successful) {
        List<Labware> lws = List.of(EntityFactory.getTube());
        UCMap<BasicLocation> locations = new UCMap<>();
        locations.put(lws.get(0).getBarcode(), new BasicLocation("STO-A1", new Address(1,2)));
        UCMap<Work> workMap = new UCMap<>(1);
        workMap.put("STAN-01", new Work());

        IllegalArgumentException exception;
        List<Release> releases;
        if (successful) {
            exception = null;
            releases = List.of();
            doReturn(releases).when(service).release(any(), any(), any(), any(), any(), any(), any());
        } else {
            exception = new IllegalArgumentException("Bad.");
            releases = List.of();
            doThrow(exception).when(service).release(any(), any(), any(), any(), any(), any(), any());
        }

        if (successful) {
            assertEquals(releases, service.transactRelease(user, recipient, otherRecs, destination, lws, locations, workMap));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class,
                    () -> service.transactRelease(user, recipient, otherRecs, destination, lws, locations, workMap)))
                    .hasMessage(exception.getMessage());
        }

        verify(mockTransactor).transact(anyString(), any());
        verify(service).release(user, recipient, otherRecs, destination, lws, locations, workMap);
    }

    @Test
    public void testRelease() {
        List<Labware> lws = List.of(EntityFactory.getTube());
        UCMap<BasicLocation> locations = new UCMap<>(1);
        locations.put(lws.get(0).getBarcode(), new BasicLocation("STO-A1", new Address(1,2)));
        doNothing().when(service).validateLabware(any());
        doNothing().when(service).validateContents(any());
        doNothing().when(service).link(any(), any());
        doReturn(lws).when(service).updateReleasedLabware(lws);
        UCMap<Work> workMap = new UCMap<>(1);
        workMap.put("STAN-1", new Work());
        List<Release> releases = List.of(new Release(100, lws.get(0), user, destination, recipient, 200, null));
        doReturn(releases).when(service).recordReleases(user, destination, recipient, otherRecs, lws, locations);
        assertSame(releases, service.release(user, recipient, otherRecs, destination, lws, locations, workMap));
        lws.forEach(lw -> verify(mockEntityManager).refresh(lw));
        verify(service).validateLabware(lws);
        verify(service).validateContents(lws);
        verify(service).updateReleasedLabware(lws);
        verify(service).link(releases, workMap);
        verify(service).recordReleases(user, destination, recipient, otherRecs, lws, locations);
    }

    @Test
    public void testLink() {
        Work[] works = quickWorks(2);
        UCMap<Work> workMap = new UCMap<>(3);
        workMap.put("STAN-1", works[0]);
        workMap.put("STAN-2", works[0]);
        workMap.put("STAN-3", works[1]);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.rangeClosed(1,4)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.setId(i);
                    lw.setBarcode("STAN-"+i);
                    return lw;
                })
                .toArray(Labware[]::new);
        List<Release> releases = Arrays.stream(labware)
                .map(lw -> {
                    Release release = new Release();
                    release.setId(100+lw.getId());
                    release.setLabware(lw);
                    return release;
                })
                .collect(toList());
        service.link(releases, workMap);
        verify(mockWorkService, times(2)).linkReleases(any(), any());
        verify(mockWorkService).linkReleases(works[0], releases.subList(0,2));
        verify(mockWorkService).linkReleases(works[1], List.of(releases.get(2)));
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

    @Test
    public void testLoadWork_none() {
        List<ReleaseLabware> rls = List.of(new ReleaseLabware("STAN-1", null), new ReleaseLabware("STAN-2", null));
        assertThat(service.loadWork(rls)).isEmpty();
        verifyNoInteractions(mockWorkService);
    }

    @Test
    public void testLoadWork() {
        Work[] works = quickWorks(2);
        List<ReleaseLabware> rls = List.of(
                new ReleaseLabware("STAN-A1", "SGP11"),
                new ReleaseLabware("STAN-A2", "SGP11"),
                new ReleaseLabware("STAN-A3", "SGP12"),
                new ReleaseLabware("STAN-A4", null)
        );
        UCMap<Work> workNumberMap = UCMap.from(Work::getWorkNumber, works);
        when(mockWorkService.getUsableWorkMap(any())).thenReturn(workNumberMap);

        UCMap<Work> result = service.loadWork(rls);
        assertThat(result).hasSize(3)
                .containsEntry("STAN-A1", works[0])
                .containsEntry("STAN-A2", works[0])
                .containsEntry("STAN-A3", works[1]);
        verify(mockWorkService).getUsableWorkMap(Set.of("SGP11", "SGP12"));
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
        Labware used = EntityFactory.makeLabware(lt, sample);
        used.setUsed(true);
        Labware empty = EntityFactory.makeEmptyLabware(lt);
        Labware destroyed = EntityFactory.makeLabware(lt, sample);
        destroyed.setDestroyed(true);
        Labware released = EntityFactory.makeLabware(lt, sample);
        released.setReleased(true);
        Labware discarded = EntityFactory.makeLabware(lt, sample);
        discarded.setDiscarded(true);

        String emptyError = "Cannot release empty labware: [" + empty.getBarcode()+"]";
        String releasedError = "Labware has already been released: ["+released.getBarcode()+"]";
        String destroyedError = "Labware cannot be released because it is destroyed: ["+destroyed.getBarcode()+"]";
        String discardedError = "Labware cannot be released because it is discarded: ["+discarded.getBarcode()+"]";
        return Stream.of(
                Arguments.of(List.of(good, used), null),
                Arguments.of(List.of(good, empty), emptyError),
                Arguments.of(List.of(empty, good), emptyError),
                Arguments.of(List.of(destroyed), destroyedError),
                Arguments.of(List.of(good, destroyed), destroyedError),
                Arguments.of(List.of(released), releasedError),
                Arguments.of(List.of(discarded), discardedError)
        );
    }

    @ParameterizedTest
    @MethodSource("validateContentsArgs")
    public void testValidateContents(Collection<Labware> labware, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            service.validateContents(labware);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.validateContents(labware)))
                    .hasMessage(expectedErrorMessage);
        }
    }

    static Stream<Arguments> validateContentsArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Tissue tissue = EntityFactory.getTissue();
        BioState bs1 = new BioState(1, "Tissue");
        BioState bs2 = new BioState(2, "RNA");
        BioState cdna = new BioState(3, "cDNA");
        Labware[] lw = IntStream.range(1, 5).mapToObj(i -> {
            Sample sample = new Sample(i, i, tissue, i==1 ? bs1 : i==2 ? bs2 : cdna);
            return EntityFactory.makeLabware(lt, sample);
        }).toArray(Labware[]::new);
        return Stream.of(
                Arguments.of(List.of(lw[0], lw[1]), null),
                Arguments.of(List.of(lw[2], lw[3]), null),
                Arguments.of(List.of(lw[0], lw[2]), "Cannot release a mix of cDNA and other bio states.")
        );
    }

    @Test
    public void testRecordReleases() {
        List<Labware> labware = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeLabware(labwareType, sample, sample1))
                .collect(toList());
        LocalDateTime timestamp = LocalDateTime.now();
        List<Release> releases = labware.stream()
                .map(lw -> new Release(10, lw, user, destination, recipient, 1, timestamp))
                .collect(toList());

        BasicLocation[] locs = {
                new BasicLocation("STO-A1", new Address(1,2)),
                new BasicLocation("STO-B2", null),
        };

        UCMap<BasicLocation> locations = new UCMap<>(labware.size());
        for (int i = 0; i < locs.length; ++i) {
            locations.put(labware.get(i).getBarcode(), locs[i]);
        }

        doReturn(releases.get(0)).when(service).recordRelease(user, destination, recipient, otherRecs, labware.get(0), locs[0]);
        doReturn(releases.get(1)).when(service).recordRelease(user, destination, recipient, otherRecs, labware.get(1), locs[1]);
        doReturn(releases.get(2)).when(service).recordRelease(user, destination, recipient, otherRecs, labware.get(2), null);

        assertEquals(releases, service.recordReleases(user, destination, recipient, otherRecs, labware, locations));
    }

    @ParameterizedTest
    @CsvSource({
            ",,,",
            "STO-A1,,,",
            "STO-A1,D2,,D2",
            "STO-A1,D2,6,6",
    })
    public void testRecordRelease(String locBarcode, Address address, Integer addressIndex, String expectedAddressDesc) {
        Labware lw = EntityFactory.makeEmptyLabware(labwareType);
        lw.getSlots().get(0).getSamples().add(sample);
        lw.getSlots().get(0).getSamples().add(sample1);
        lw.getSlots().get(1).getSamples().add(sample1);
        BasicLocation loc;
        if (locBarcode==null) {
            loc = null;
        } else {
            loc = new BasicLocation(locBarcode, address);
            loc.setAddressIndex(addressIndex);
        }

        final int releaseId = 10;
        Release release = new Release(releaseId, lw, user, destination, recipient, 1, LocalDateTime.now(),
                locBarcode, expectedAddressDesc, otherRecs);

        when(mockReleaseRepo.save(any())).thenReturn(release);

        Snapshot snap = EntityFactory.makeSnapshot(lw);
        when(mockSnapshotService.createSnapshot(any())).thenReturn(snap);

        assertSame(release, service.recordRelease(user, destination, recipient,otherRecs, lw, loc));

        verify(mockSnapshotService).createSnapshot(lw);
        final Release expectedNewRelease = new Release(lw, user, destination, recipient, snap.getId());
        if (loc!=null) {
            expectedNewRelease.setLocationBarcode(loc.getBarcode());
            expectedNewRelease.setStorageAddress(expectedAddressDesc);
        }
        expectedNewRelease.setOtherRecipients(otherRecs);
        verify(mockReleaseRepo).save(expectedNewRelease);
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

    static Work quickWork(int id) {
        Work work = new Work();
        work.setId(id);
        work.setWorkNumber("SGP1"+id);
        return work;
    }
    static Work[] quickWorks(int num) {
        return IntStream.rangeClosed(1, num)
                .mapToObj(TestReleaseService::quickWork)
                .toArray(Work[]::new);
    }
}
