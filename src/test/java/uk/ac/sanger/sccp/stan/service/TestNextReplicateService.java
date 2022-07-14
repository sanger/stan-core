package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.TissueRepo;
import uk.ac.sanger.sccp.stan.request.NextReplicateData;
import uk.ac.sanger.sccp.stan.service.NextReplicateServiceImp.RepKey;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestNextReplicateService {
    private LabwareRepo mockLwRepo;
    private TissueRepo mockTissueRepo;
    private NextReplicateServiceImp service;

    @BeforeEach
    void setup() {
        mockLwRepo = mock(LabwareRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        service = spy(new NextReplicateServiceImp(mockLwRepo, mockTissueRepo));
    }

    @Test
    public void testGetNextReplicateData_none() {
        assertThat(service.getNextReplicateData(List.of())).isEmpty();
        verify(service, never()).groupBarcodes(any());
        verify(service, never()).toReplicateData(any(), any());
    }

    @Test
    public void testGetNextReplicateData() {
        Map<RepKey, Set<String>> groups = Map.of(
                new RepKey(1,2), Set.of("STAN-1"),
                new RepKey(2, 3), Set.of("STAN-2")
        );
        doReturn(groups).when(service).groupBarcodes(any());
        List<String> barcodes = List.of("STAN-1", "STAN-2");
        final NextReplicateData rep1 = new NextReplicateData(List.of("STAN-1"), 1, 2, 3);
        final NextReplicateData rep2 = new NextReplicateData(List.of("STAN-2"), 2, 3, 4);
        doReturn(rep1, rep2).when(service).toReplicateData(any(), any());

        assertThat(service.getNextReplicateData(barcodes)).containsExactly(rep1, rep2);

        verify(service).groupBarcodes(barcodes);
        groups.forEach((key, value) -> verify(service).toReplicateData(key, value));
    }

    @Test
    public void testGroupBarcodes() {
        Donor d1 = new Donor(1, "DONOR1", null, null);
        Donor d2 = new Donor(2, "DONOR2", null, null);
        SpatialLocation sl1 = EntityFactory.getSpatialLocation();
        SpatialLocation sl2 = new SpatialLocation(sl1.getId()+1, "sl2", sl1.getCode()+1, sl1.getTissueType());

        Tissue[] tissues = {
                EntityFactory.makeTissue(d1, sl1),
                EntityFactory.makeTissue(d2, sl1),
                EntityFactory.makeTissue(d1, sl2),
                EntityFactory.makeTissue(d1, sl1),
        };
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,4)
                .mapToObj(n -> EntityFactory.makeEmptyLabware(lt))
                .collect(toCollection(ArrayList::new));
        labware.add(labware.get(0));
        for (int i = 0; i < tissues.length; ++i) {
            doReturn(tissues[i]).when(service).getSingleTissue(labware.get(i));
        }
        doReturn(labware).when(mockLwRepo).getByBarcodeIn(any());
        List<String> barcodes = labware.stream().map(Labware::getBarcode).collect(toList());

        var groups = service.groupBarcodes(barcodes);
        verify(mockLwRepo).getByBarcodeIn(barcodes);
        assertThat(groups).hasSize(3);
        assertThat(groups.get(new RepKey(tissues[0]))).containsExactlyInAnyOrder(labware.get(0).getBarcode(), labware.get(3).getBarcode());
        assertThat(groups.get(new RepKey(tissues[1]))).containsExactly(labware.get(1).getBarcode());
        assertThat(groups.get(new RepKey(tissues[2]))).containsExactly(labware.get(2).getBarcode());
    }

    @ParameterizedTest
    @CsvSource({",1","4,5"})
    public void testToReplicateData(Integer maxRep, int nextRep) {
        when(mockTissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(anyInt(), anyInt())).thenReturn(maxRep);
        final int donorId = 10, slId = 11;
        final Set<String> barcodes = Set.of("STAN-A1", "STAN-A2");
        var data = service.toReplicateData(new RepKey(donorId, slId), barcodes);
        assertEquals(new NextReplicateData(data.getBarcodes(), donorId, slId, nextRep), data);
        assertThat(data.getBarcodes()).containsExactlyInAnyOrderElementsOf(barcodes);
        verify(mockTissueRepo).findMaxReplicateForDonorIdAndSpatialLocationId(donorId, slId);
    }

    @Test
    public void testGetSingleTissue() {
        Sample s1 = EntityFactory.getSample();
        Tissue tissue1 = s1.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        Sample s2 = new Sample(500, null, tissue2, EntityFactory.getBioState());

        LabwareType lt1 = EntityFactory.getTubeType();
        LabwareType lt2 = EntityFactory.makeLabwareType(1,2);
        assertSame(tissue1, service.getSingleTissue(EntityFactory.makeLabware(lt1, s1)));
        assertSame(tissue1, service.getSingleTissue(EntityFactory.makeLabware(lt2, s1, s1)));

        Labware lw1 = EntityFactory.makeEmptyLabware(lt1);
        assertIllegalArgument(() -> service.getSingleTissue(lw1), "Labware "+lw1.getBarcode()+" is empty.");
        assertThrows(IllegalArgumentException.class, () -> service.getSingleTissue(lw1));
        Labware lw2 = EntityFactory.makeLabware(lt2, s1, s2);
        assertIllegalArgument(() -> service.getSingleTissue(lw2), "Labware "+lw2.getBarcode()+" contains multiple different tissues.");
    }

    private static void assertIllegalArgument(Executable ex, String message) {
        assertThat(assertThrows(IllegalArgumentException.class, ex)).hasMessage(message);
    }
}