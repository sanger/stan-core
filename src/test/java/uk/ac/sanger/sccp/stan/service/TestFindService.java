package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.Location;
import uk.ac.sanger.sccp.stan.model.store.StoredItem;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FindRequest;
import uk.ac.sanger.sccp.stan.request.FindResult;
import uk.ac.sanger.sccp.stan.request.FindResult.FindEntry;
import uk.ac.sanger.sccp.stan.request.FindResult.LabwareLocation;
import uk.ac.sanger.sccp.stan.service.FindService.LabwareSample;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.*;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link FindService}
 * @author dr6
 */
public class TestFindService {
    private LabwareService mockLabwareService;
    private StoreService mockStoreService;
    private LabwareRepo mockLabwareRepo;
    private DonorRepo mockDonorRepo;
    private TissueRepo mockTissueRepo;
    private SampleRepo mockSampleRepo;

    private FindService findService;

    @BeforeEach
    void setup() {
        mockLabwareService = mock(LabwareService.class);
        mockStoreService = mock(StoreService.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockDonorRepo = mock(DonorRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockSampleRepo = mock(SampleRepo.class);

        findService = spy(new FindService(mockLabwareService, mockStoreService, mockLabwareRepo, mockDonorRepo,
                mockTissueRepo, mockSampleRepo));
    }

    @ParameterizedTest
    @MethodSource("findArgs")
    public void testFind(FindRequest request) {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        Sample sam = EntityFactory.getSample();
        List<LabwareSample> ls1 = List.of(new LabwareSample(lw1, sam), new LabwareSample(lw2, sam));
        List<LabwareSample> ls2 = List.of(new LabwareSample(lw1, sam));
        doReturn(ls2).when(findService).filter(ls1, request);
        List<StoredItem> sis = List.of(new StoredItem("STAN-A1", new Location()));
        doReturn(sis).when(findService).getStoredItems(any());
        FindResult result = new FindResult();
        result.setNumRecords(10);
        doReturn(result).when(findService).assembleResult(any(), any(), any());

        final int mode;
        if (request.getLabwareBarcode()!=null) {
            mode = 0;
        } else if (request.getTissueExternalName()!=null) {
            mode = 1;
        } else {
            mode = 2;
        }

        switch (mode) {
            case 0:
                doReturn(ls1).when(findService).findByLabwareBarcode(request.getLabwareBarcode());
                break;
            case 1:
                doReturn(ls1).when(findService).findByTissueExternalName(request.getTissueExternalName());
                break;
            case 2:
                doReturn(ls1).when(findService).findByDonorName(request.getDonorName());
                break;
        }

        assertSame(result, findService.find(request));

        verify(findService).validateRequest(request);

        switch (mode) {
            case 0:
                verify(findService).findByLabwareBarcode(request.getLabwareBarcode());
                break;
            case 1:
                verify(findService).findByTissueExternalName(request.getTissueExternalName());
                break;
            case 2:
                verify(findService).findByDonorName(request.getDonorName());
                break;
        }
        verify(findService).filter(ls1, request);
        verify(findService).getStoredItems(ls2);
        verify(findService).assembleResult(request, ls2, sis);
    }

    static Stream<FindRequest> findArgs() {
        return Stream.of(
                new FindRequest("STAN-A1", null, null, null, -1),
                new FindRequest(null, null, "TISSUE1", null, -1),
                new FindRequest(null, "DONOR1", null, null, -1)
        );
    }

    @Test
    public void testValidateRequest() {
        findService.validateRequest(new FindRequest("STAN-A1", null, null, null, 50));
        findService.validateRequest(new FindRequest(null, "DONOR1", null, null, 50));
        findService.validateRequest(new FindRequest(null, null, "TISSUE1", null, 50));
        findService.validateRequest(new FindRequest("STAN-A1", "DONOR1", "TISSUE1", "TTYPE", 50));

        assertThat(assertThrows(IllegalArgumentException.class, () ->
                findService.validateRequest(new FindRequest(null, null, null, "TTYPE", 50))))
                .hasMessage("Donor name or external name or labware barcode must be specified.");
    }

    @ParameterizedTest
    @MethodSource("findByLabwareBarcodeArgs")
    public void testFindByLabwareBarcode(Labware lw) {
        final String barcode;
        if (lw==null) {
            barcode = "STAN-A1";
            doThrow(EntityNotFoundException.class).when(mockLabwareRepo).getByBarcode(barcode);
        } else {
            barcode = lw.getBarcode();
            doReturn(lw).when(mockLabwareRepo).getByBarcode(barcode);
        }

        if (lw==null) {
            assertThrows(EntityNotFoundException.class, () -> findService.findByLabwareBarcode(barcode));
            return;
        }
        Set<Sample> samples = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .collect(Collectors.toSet());
        List<LabwareSample> expected = samples.stream()
                .map(sam -> new LabwareSample(lw, sam))
                .collect(toList());

        assertThat(findService.findByLabwareBarcode(barcode)).containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Labware> findByLabwareBarcodeArgs() {
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, 8, sample1.getTissue(), sample1.getBioState());
        LabwareType lt1 = EntityFactory.getTubeType();
        Labware lwWithMultipleSamplesInOneSlot = EntityFactory.makeEmptyLabware(lt1);
        lwWithMultipleSamplesInOneSlot.getFirstSlot().getSamples().addAll(List.of(sample1, sample2));
        LabwareType lt2 = EntityFactory.makeLabwareType(1, 2);

        return Stream.of(
                lwWithMultipleSamplesInOneSlot,
                EntityFactory.makeEmptyLabware(lt1),
                null,
                EntityFactory.makeLabware(lt1, sample1),
                EntityFactory.makeLabware(lt2, sample1, sample1),
                EntityFactory.makeLabware(lt2, sample1, sample2)
        );
    }

    @Test
    public void testFindByTissueIds() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue1 = EntityFactory.makeTissue(donor, sl);
        Tissue tissue2 = EntityFactory.makeTissue(donor, sl);
        tissue2.setReplicate(2);
        Tissue tissue3 = EntityFactory.makeTissue(donor, sl);
        tissue3.setReplicate(3);
        BioState bioState = EntityFactory.getBioState();

        Sample[] samples = {
                new Sample(100, null, tissue1, bioState),
                new Sample(101, 1, tissue1, bioState),
                new Sample(102, 2, tissue2, bioState),
                new Sample(103, 3, tissue3, bioState)
        };
        LabwareType lt1 = EntityFactory.getTubeType();
        LabwareType lt2 = EntityFactory.makeLabwareType(1, 2);
        Labware[] labware = {
                EntityFactory.makeLabware(lt1, samples[0]),
                EntityFactory.makeLabware(lt1, samples[1]),
                EntityFactory.makeLabware(lt1, samples[2]),
                EntityFactory.makeLabware(lt2, samples[0], samples[2]),
        };

        when(mockSampleRepo.findAllByTissueIdIn(any())).then(invocation -> {
            Collection<Integer> tissueIds = invocation.getArgument(0);
            return Arrays.stream(samples)
                    .filter(sam -> tissueIds.contains(sam.getTissue().getId()))
                    .collect(toList());
        });
        when(mockLabwareService.findBySample(any())).then(invocation -> {
            Collection<Sample> sams = invocation.getArgument(0);
            return Arrays.stream(labware)
                    .filter(lw -> lw.getSlots().stream().flatMap(slot -> slot.getSamples().stream())
                            .anyMatch(sams::contains))
                    .collect(toList());
        });


        assertThat(findService.findByTissueIds(List.of(tissue1.getId(), tissue2.getId())))
                .containsExactlyInAnyOrder(
                        new LabwareSample(labware[0], samples[0]),
                        new LabwareSample(labware[1], samples[1]),
                        new LabwareSample(labware[2], samples[2]),
                        new LabwareSample(labware[3], samples[0]),
                        new LabwareSample(labware[3], samples[2])
                );
        assertThat(findService.findByTissueIds(List.of(tissue1.getId())))
                .containsExactlyInAnyOrder(
                        new LabwareSample(labware[0], samples[0]),
                        new LabwareSample(labware[1], samples[1]),
                        new LabwareSample(labware[3], samples[0])
                );
        assertThat(findService.findByTissueIds(List.of(tissue3.getId())))
                .isEmpty();
        assertThat(findService.findByTissueIds(List.of(-400)))
                .isEmpty();
        // Check that unstorable labware are filtered out
        labware[0].setReleased(true);
        labware[1].setDiscarded(true);
        labware[3].setDestroyed(true);
        assertThat(findService.findByTissueIds(List.of(tissue1.getId(), tissue2.getId())))
                .containsExactly(new LabwareSample(labware[2], samples[2]));
    }

    @Test
    public void testFindByTissueExternalName() {
        String invalidName = "TISSUE_X";
        doThrow(EntityNotFoundException.class).when(mockTissueRepo).getByExternalName(invalidName);
        assertThrows(EntityNotFoundException.class, () -> findService.findByTissueExternalName(invalidName));
        verify(findService, never()).findByTissueIds(any());

        Tissue tissue = EntityFactory.getTissue();
        when(mockTissueRepo.getByExternalName(tissue.getExternalName())).thenReturn(tissue);
        Labware lw = EntityFactory.getTube();
        Sample sample = EntityFactory.getSample();
        List<LabwareSample> lss = List.of(new LabwareSample(lw, sample));
        doReturn(lss).when(findService).findByTissueIds(List.of(tissue.getId()));
        assertEquals(lss, findService.findByTissueExternalName(tissue.getExternalName()));
        verify(findService).findByTissueIds(List.of(tissue.getId()));
    }

    @Test
    public void testFindByDonorName() {
        Sample sample = EntityFactory.getSample();
        Tissue tissue = sample.getTissue();
        Donor donor = tissue.getDonor();
        Labware lw = EntityFactory.getTube();

        when(mockDonorRepo.getByDonorName(donor.getDonorName())).thenReturn(donor);
        when(mockTissueRepo.findByDonorId(donor.getId())).thenReturn(List.of(tissue));
        List<LabwareSample> lss = List.of(new LabwareSample(lw, sample));
        doReturn(lss).when(findService).findByTissueIds(List.of(tissue.getId()));

        assertEquals(lss, findService.findByDonorName(donor.getDonorName()));
    }

    @ParameterizedTest
    @MethodSource("filterArgs")
    public void testFilter(List<LabwareSample> lss, FindRequest request, List<LabwareSample> expected) {
        assertThat(findService.filter(lss, request)).containsExactlyInAnyOrderElementsOf(expected);
    }

    static Stream<Arguments> filterArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        Sample sample1 = EntityFactory.getSample();
        Tissue tissue1 = sample1.getTissue();
        Donor donor1 = tissue1.getDonor();
        TissueType tt1 = tissue1.getTissueType();
        Species species = new Species(1, "Human");

        TissueType tt2 = new TissueType(200, "Jelly", "JLY");
        SpatialLocation sl2 = new SpatialLocation(201, "SL2", 2, tt2);
        Donor donor2 = new Donor(null, "DONOR2", LifeStage.fetal, species);
        Tissue tissue2 = new Tissue(201, "TISSUE2", 4, sl2, donor2, tissue1.getMouldSize(), tissue1.getMedium(),
                tissue1.getFixative(), tissue1.getHmdmc());
        Sample sample2 = new Sample(202, 2, tissue2, EntityFactory.getBioState());

        List<LabwareSample> lss = List.of(
                new LabwareSample(lw1, sample1),
                new LabwareSample(lw1, sample2),
                new LabwareSample(lw2, sample1),
                new LabwareSample(lw2, sample2)
        );

        return Stream.of(
                Arguments.of(lss, new FindRequest(null, null, null, null, 0), lss),
                Arguments.of(lss, new FindRequest(lw1.getBarcode().toLowerCase(), null, null, null, 0), lss.subList(0,2)),
                Arguments.of(lss, new FindRequest(null, donor1.getDonorName(), null, null, 0),
                        List.of(lss.get(0), lss.get(2))),
                Arguments.of(lss, new FindRequest(null, null, tissue2.getExternalName(), null, 0),
                        List.of(lss.get(1), lss.get(3))),
                Arguments.of(lss, new FindRequest(null, null, null, tt1.getName().toLowerCase(), 0),
                        List.of(lss.get(0), lss.get(2))),
                Arguments.of(lss, new FindRequest(lw1.getBarcode(), donor1.getDonorName(), tissue1.getExternalName(), tt2.getName(), 0), List.of())
        );
    }

    @Test
    public void testGetStoredItems() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        final Tissue tissue = EntityFactory.getTissue();
        final BioState bs = EntityFactory.getBioState();
        Sample[] samples = IntStream.range(0,2).mapToObj(i -> new Sample(10+i, 1+i, tissue, bs)).toArray(Sample[]::new);
        Labware[] labware = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeLabware(lt, samples)).toArray(Labware[]::new);
        List<LabwareSample> lss = Arrays.stream(samples).flatMap(
                sam -> Arrays.stream(labware).map(lw -> new LabwareSample(lw, sam))
        ).collect(toList());

        StoredItem storedItem = new StoredItem();
        storedItem.setBarcode(labware[0].getBarcode());
        storedItem.setLocation(new Location());
        List<StoredItem> storedItems = List.of(storedItem);
        when(mockStoreService.getStored(any())).thenReturn(storedItems);

        assertSame(storedItems, findService.getStoredItems(lss));
        verify(mockStoreService).getStored(Set.of(labware[0].getBarcode(), labware[1].getBarcode()));
    }

    @ParameterizedTest
    @MethodSource("assembleResultArgs")
    public void testAssembleResult(FindRequest request, List<LabwareSample> lss, List<StoredItem> sis,
                                   FindResult expected) {
        assertFindResult(expected, findService.assembleResult(request, lss, sis));
    }

    static Stream<Arguments> assembleResultArgs() {

        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,4)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        Tissue tissue = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        Sample[] samples = IntStream.range(0,2)
                .mapToObj(i -> new Sample(10+i, 1+i, tissue, bs))
                .toArray(Sample[]::new);
        List<LabwareSample> lss = Arrays.stream(labware)
                .flatMap(lw -> Arrays.stream(samples)
                        .map(sam -> new LabwareSample(lw, sam)))
                .collect(toList());
        Location loc1 = new Location();
        loc1.setId(100);
        Location loc2 = new Location();
        loc2.setId(101);
        Address A1 = new Address(1,1);
        Address B3 = new Address(2,3);
        List<StoredItem> storedItems = List.of(
                new StoredItem(labware[0].getBarcode(), loc1, A1),
                new StoredItem(labware[1].getBarcode(), loc1, B3),
                new StoredItem(labware[2].getBarcode(), loc2)
        );
        List<LabwareSample> lssForLw3 = lss.stream()
                .filter(ls -> ls.labware.equals(labware[3]))
                .collect(toList());
        List<LabwareSample> lssWithoutLw3 = lss.stream()
                .filter(ls -> !ls.labware.equals(labware[3]))
                .collect(toList());

        // Case 0:
        // barcode specified and lw unstored - no limit - 1 item returned
        // Case 1:
        // differnet barcode specified and lw unstored - no limit - no items returned
        // Case 2:
        // barcode unspecified, some stored and some unstored lw - no limit - 6 returned
        // Case 3:
        // barcode unspecified, some stored and some unstored - limit 3 - 3 returned

        return Stream.of(
                Arguments.of(new FindRequest(labware[3].getBarcode(), null, null, null, -1),
                        lssForLw3, storedItems,
                        result(2, labware[3], samples[0], samples[1],
                                lssForLw3)),
                Arguments.of(new FindRequest(null, null, null, null, -1),
                        lssForLw3, storedItems,
                        result(0)),
                Arguments.of(new FindRequest(null, null, null, null, -1),
                        lss, storedItems,
                        result(6, labware[0], labware[1], labware[2], samples[0], samples[1],
                                lssWithoutLw3, loc1, loc2, storedItems)),
                Arguments.of(new FindRequest(null, null, null, null, 3),
                        lss, storedItems,
                        result(6, labware[0], labware[1], samples[0], samples[1],
                                lss.get(0), lss.get(1), lss.get(2), storedItems.get(0), storedItems.get(1), loc1))
        );
    }

    private static FindResult result(int numResults, Object... data) {
        List<FindEntry> entries = new ArrayList<>();
        List<Sample> samples = new ArrayList<>();
        List<Labware> labware = new ArrayList<>();
        List<LabwareLocation> labwareLocations = new ArrayList<>();
        List<Location> locations = new ArrayList<>();

        for (Object dat : data) {
            Iterable<?> iterable;
            if (dat instanceof Collection) {
                iterable = (Iterable<?>) dat;
            } else {
                iterable = Stream.of(dat)::iterator;
            }
            for (Object item : iterable) {
                if (item instanceof Sample) {
                    samples.add((Sample) item);
                } else if (item instanceof LabwareSample) {
                    LabwareSample ls = (LabwareSample) item;
                    entries.add(new FindEntry(ls.sample.getId(), ls.labware.getId()));
                } else if (item instanceof Labware) {
                    labware.add((Labware) item);
                } else if (item instanceof Location) {
                    locations.add((Location) item);
                } else if (item instanceof StoredItem) {
                    StoredItem si = (StoredItem) item;
                    Labware lwItem = labware.stream().filter(lw -> lw.getBarcode().equalsIgnoreCase(si.getBarcode()))
                            .findAny().orElseThrow();
                    LabwareLocation lwloc = new LabwareLocation(lwItem.getId(), si.getLocation().getId(), si.getAddress());
                    labwareLocations.add(lwloc);
                }
            }
        }
        return new FindResult(numResults, entries, samples, labware, labwareLocations, locations);
    }

    private static void assertFindResult(FindResult expected, FindResult actual) {
        assertEquals(expected.getNumRecords(), actual.getNumRecords());
        assertThat(actual.getEntries()).containsExactlyInAnyOrderElementsOf(expected.getEntries());
        assertThat(actual.getLabware()).containsExactlyInAnyOrderElementsOf(expected.getLabware());
        assertThat(actual.getLocations()).containsExactlyInAnyOrderElementsOf(expected.getLocations());
        assertThat(actual.getLabwareLocations()).containsExactlyInAnyOrderElementsOf(expected.getLabwareLocations());
        assertThat(actual.getSamples()).containsExactlyInAnyOrderElementsOf(expected.getSamples());
    }
}
