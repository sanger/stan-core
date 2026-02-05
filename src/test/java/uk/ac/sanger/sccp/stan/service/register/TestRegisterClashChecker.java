package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link RegisterClashChecker}
 * @author dr6
 */
public class TestRegisterClashChecker {
    private TissueRepo mockTissueRepo;
    private SampleRepo mockSampleRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationRepo mockOpRepo;
    private LabwareRepo mockLwRepo;

    private RegisterClashChecker checker;

    private Tissue[] tissues;
    private Sample[] samples;
    private Labware[] labware;
    private OperationType opType;
    private Operation[] ops;

    @BeforeEach
    void setup() {
        mockTissueRepo = mock(TissueRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        checker = spy(new RegisterClashChecker(mockTissueRepo, mockSampleRepo, mockOpTypeRepo, mockOpRepo, mockLwRepo));
        opType = new OperationType(1, "Register");
    }

    private Tissue[] createTissues() {
        if (tissues==null) {
            Donor donor = EntityFactory.getDonor();
            SpatialLocation sl = EntityFactory.getSpatialLocation();
            tissues = IntStream.range(0, 2).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        }
        return tissues;
    }

    private Sample[] createSamples() {
        if (samples==null) {
            BioState bs = EntityFactory.getBioState();
            samples = Arrays.stream(createTissues()).map(t -> new Sample(10+t.getId(), 3, t, bs)).toArray(Sample[]::new);
        }
        return samples;
    }

    private Labware[] createLabware() {
        if (labware==null) {
            LabwareType lt = EntityFactory.getTubeType();
            labware = Arrays.stream(createSamples()).map(sam -> EntityFactory.makeLabware(lt, sam)).toArray(Labware[]::new);
        }
        return labware;
    }

    private Operation[] createOps() {
        if (ops==null) {
            ops = Arrays.stream(createLabware())
                    .map(List::of)
                    .map(lws -> EntityFactory.makeOpForLabware(opType, lws, lws))
                    .toArray(Operation[]::new);
        }
        return ops;
    }

    @ParameterizedTest
    @MethodSource("findClashesArgs")
    public void testFindClashes(RegisterRequest request, List<Tissue> tissues) {
        Set<String> newXns = request.getBlocks().stream()
                .filter(b -> !b.isExistingTissue())
                .map(BlockRegisterRequest_old::getExternalIdentifier)
                .collect(toSet());
        if (newXns.isEmpty()) {
            assertThat(checker.findClashes(request)).isEmpty();
            verifyNoInteractions(mockTissueRepo);
            return;
        }
        if (tissues==null) {
            tissues = List.of();
        }
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(tissues);
        if (tissues.isEmpty()) {
            assertThat(checker.findClashes(request)).isEmpty();
            verify(mockTissueRepo).findAllByExternalNameIn(newXns);
            verify(checker, never()).createClashInfo(any());
            return;
        }
        final List<RegisterClash> clashes = List.of(new RegisterClash(EntityFactory.getTissue(), List.of()));
        doReturn(clashes).when(checker).createClashInfo(any());

        assertEquals(clashes, checker.findClashes(request));
        verify(mockTissueRepo).findAllByExternalNameIn(newXns);
        verify(checker).createClashInfo(tissues);
    }

    static Stream<Arguments> findClashesArgs() {
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        String xn1 = tissue1.getExternalName();
        String xn2 = tissue2.getExternalName();
        return Stream.of(
                Arguments.of(makeRequest("Alpha", true, "Beta", true), List.of()),
                Arguments.of(makeRequest("Alpha", false, "Beta", false), List.of()),
                Arguments.of(makeRequest("Alpha", true, xn1, false, xn2, false), List.of(tissue1, tissue2))
        );
    }

    static RegisterRequest makeRequest(Object... data) {
        List<BlockRegisterRequest_old> brs = new ArrayList<>(data.length/2);
        for (int i = 0; i < data.length; i += 2) {
            String xn = (String) data[i];
            boolean exists = (boolean) data[i+1];
            BlockRegisterRequest_old br = new BlockRegisterRequest_old();
            br.setExternalIdentifier(xn);
            br.setExistingTissue(exists);
            brs.add(br);
        }
        return new RegisterRequest(brs);
    }

    @Test
    public void testCreateClashInfo() {
        createOps();
        when(mockOpTypeRepo.getByName("Register")).thenReturn(opType);
        List<Operation> ops = Arrays.asList(this.ops);
        Set<Integer> sampleIds = Arrays.stream(samples).map(Sample::getId).collect(toSet());
        doReturn(sampleIds).when(checker).loadSampleIds(any());
        when(mockOpRepo.findAllByOperationTypeAndSampleIdIn(any(), any())).thenReturn(ops);
        Map<Integer, List<Labware>> tissueIdLabwareMap = Map.of(17, Arrays.asList(labware));
        doReturn(tissueIdLabwareMap).when(checker).createTissueIdLabwareMap(any(), any());
        RegisterClash[] clashes = IntStream.range(0,2)
                .mapToObj(i -> new RegisterClash(tissues[i], List.of(labware[i])))
                .toArray(RegisterClash[]::new);
        doReturn(clashes[0], clashes[1]).when(checker).toRegisterClash(any(), any());

        assertThat(checker.createClashInfo(Arrays.asList(tissues)))
                .containsExactly(clashes);

        verify(mockOpRepo).findAllByOperationTypeAndSampleIdIn(opType, sampleIds);
        final Set<Integer> tissueIds = Arrays.stream(tissues).map(Tissue::getId).collect(toSet());
        verify(checker).loadSampleIds(tissueIds);
        verify(checker).createTissueIdLabwareMap(tissueIds, ops);
        verify(checker).toRegisterClash(tissues[0], tissueIdLabwareMap);
        verify(checker).toRegisterClash(tissues[1], tissueIdLabwareMap);
    }

    @Test
    public void testLoadSampleIds() {
        createSamples();
        when(mockSampleRepo.findAllByTissueIdIn(any())).thenReturn(Arrays.asList(samples));
        Set<Integer> tissueIds = Set.of(1,2,3);
        Set<Integer> sampleIds = checker.loadSampleIds(tissueIds);
        verify(mockSampleRepo).findAllByTissueIdIn(tissueIds);
        assertEquals(Arrays.stream(samples).map(Sample::getId).collect(toSet()), sampleIds);
    }

    @Test
    public void testCreateTissueIdLabwareMap() {
        createOps();
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(Arrays.asList(labware));
        Set<Integer> tissueIds = Arrays.stream(tissues).map(Tissue::getId).collect(toSet());
        var resultMap = checker.createTissueIdLabwareMap(tissueIds, Arrays.asList(ops));
        Set<Integer> labwareIds = Arrays.stream(labware).map(Labware::getId).collect(toSet());
        verify(mockLwRepo).findAllByIdIn(labwareIds);
        assertEquals(Map.of(tissues[0].getId(), List.of(labware[0]), tissues[1].getId(), List.of(labware[1])), resultMap);
    }

    @Test
    public void testToRegisterClash() {
        createTissues();
        createLabware();
        Map<Integer, List<Labware>> tissueIdLabwareMap = Map.of(tissues[0].getId(), Arrays.asList(labware));
        assertEquals(new RegisterClash(tissues[0], Arrays.asList(labware)), checker.toRegisterClash(tissues[0], tissueIdLabwareMap));
        assertEquals(new RegisterClash(tissues[1], List.of()), checker.toRegisterClash(tissues[1], tissueIdLabwareMap));

    }
}
