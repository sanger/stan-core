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
import uk.ac.sanger.sccp.stan.service.*;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.eqCi;

/**
 * Test {@link RegisterServiceImp}
 * @author dr6
 */
public class TestRegisterService {
    private EntityManager mockEntityManager;
    private RegisterValidationFactory mockValidationFactory;
    private DonorRepo mockDonorRepo;
    private TissueRepo mockTissueRepo;
    private SampleRepo mockSampleRepo;
    private SlotRepo mockSlotRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private BioStateRepo mockBioStateRepo;
    private LabwareService mockLabwareService;
    private OperationService mockOpService;
    private RegisterValidation mockValidation;

    private User user;
    private OperationType opType;
    private int idCounter = 1000;

    private RegisterServiceImp registerService;

    @BeforeEach
    void setup() {
        mockEntityManager = mock(EntityManager.class);
        mockValidationFactory = mock(RegisterValidationFactory.class);
        mockDonorRepo = mock(DonorRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLabwareService = mock(LabwareService.class);
        mockOpService = mock(OperationService.class);
        mockValidation = mock(RegisterValidation.class);
        mockBioStateRepo = mock(BioStateRepo.class);
        user = EntityFactory.getUser();
        when(mockValidationFactory.createRegisterValidation(any())).thenReturn(mockValidation);
        opType = new OperationType(99, "Register");
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        when(mockBioStateRepo.getByName("Tissue")).thenReturn(EntityFactory.getBioState());

        registerService = spy(new RegisterServiceImp(mockEntityManager, mockValidationFactory, mockDonorRepo, mockTissueRepo,
                mockSampleRepo, mockSlotRepo, mockOpTypeRepo, mockBioStateRepo, mockLabwareService, mockOpService));
    }

    @Test
    public void testRegisterNoBlocks() {
        RegisterResult result = registerService.register(new RegisterRequest(List.of()), user);
        assertThat(result.getLabware()).isEmpty();
        assertThat(result.getTissue()).isEmpty();
        verifyNoInteractions(mockValidationFactory);
        verify(registerService, never()).create(any(), any(), any());
    }

    @Test
    public void testRegisterValidBlocks() {
        RegisterRequest request = new RegisterRequest(List.of(new BlockRegisterRequest()));
        when(mockValidation.validate()).thenReturn(Set.of());
        final RegisterResult result = new RegisterResult(List.of(EntityFactory.getTube()), List.of(EntityFactory.getTissue()));
        doReturn(result).when(registerService).create(any(), any(), any());

        assertSame(result, registerService.register(request, user));

        verify(mockValidationFactory).createRegisterValidation(request);
        verify(mockValidation).validate();
        verify(registerService).create(request, user, mockValidation);
    }

    @Test
    public void testRegisterInvalidBlocks() {
        RegisterRequest request = new RegisterRequest(List.of(new BlockRegisterRequest()));
        final Set<String> problems = Set.of("Everything is bad.", "I spilled my tea.");
        when(mockValidation.validate()).thenReturn(problems);

        try {
            registerService.register(request, user);
            fail("Expected validation exception.");
        } catch (ValidationException ex) {
            assertEquals(ex.getProblems(), problems);
        }

        verify(mockValidationFactory).createRegisterValidation(request);
        verify(mockValidation).validate();
        verify(registerService, never()).create(request, user, mockValidation);
    }

    @Test
    public void testCreateDonors() {
        Donor donor0 = EntityFactory.getDonor();
        Species hamster = new Species(2, "Hamster");
        Donor donor1 = new Donor(null, "Jeff", LifeStage.paediatric, hamster);
        BlockRegisterRequest block0 = new BlockRegisterRequest();
        block0.setDonorIdentifier(donor0.getDonorName());
        block0.setLifeStage(donor0.getLifeStage());
        block0.setSpecies(donor0.getSpecies().getName());
        BlockRegisterRequest block1 = new BlockRegisterRequest();
        block1.setDonorIdentifier(donor1.getDonorName());
        block1.setLifeStage(donor1.getLifeStage());
        block1.setSpecies(donor1.getSpecies().getName());

        when(mockValidation.getDonor(eqCi(donor0.getDonorName()))).thenReturn(donor0);
        when(mockValidation.getDonor(eqCi(donor1.getDonorName()))).thenReturn(donor1);

        when(mockDonorRepo.save(any())).then(invocation -> {
            Donor donor = invocation.getArgument(0);
            assertNull(donor.getId());
            donor.setId(++idCounter);
            return donor;
        });

        RegisterRequest request = new RegisterRequest(List.of(block0, block1));
        Map<String, Donor> donorMap = registerService.createDonors(request, mockValidation);
        assertEquals(donorMap, Stream.of(donor0, donor1).collect(toMap(d -> d.getDonorName().toUpperCase(), d -> d)));
        verify(mockDonorRepo).save(donor1);
        verifyNoMoreInteractions(mockDonorRepo);
    }

    @Test
    public void testCreate() {
        Species hamster = new Species(2, "Hamster");
        Donor donor1 = EntityFactory.getDonor();
        Donor donor2 = new Donor(donor1.getId()+1, "DONOR2", LifeStage.adult, hamster);
        LabwareType[] lts = {EntityFactory.getTubeType(), EntityFactory.makeLabwareType(1, 2)};
        TissueType tissueType = EntityFactory.getTissueType();
        MouldSize mouldSize = EntityFactory.getMouldSize();
        Medium medium = EntityFactory.getMedium();
        Fixative fixative = EntityFactory.getFixative();
        Hmdmc[] hmdmcs = {new Hmdmc(20000, "20/000"), new Hmdmc(20001, "20/001")};

        BlockRegisterRequest block0 = new BlockRegisterRequest();
        block0.setDonorIdentifier(donor1.getDonorName());
        block0.setLifeStage(donor1.getLifeStage());
        block0.setExternalIdentifier("TISSUE0");
        block0.setHighestSection(3);
        block0.setHmdmc("20/000");
        block0.setLabwareType(lts[0].getName());
        block0.setMedium(medium.getName());
        block0.setFixative(fixative.getName());
        block0.setMouldSize(mouldSize.getName());
        block0.setTissueType(tissueType.getName());
        block0.setReplicateNumber(2);
        block0.setSpatialLocation(1);
        block0.setSpecies(donor1.getSpecies().getName());

        BlockRegisterRequest block1 = new BlockRegisterRequest();
        block1.setDonorIdentifier(donor2.getDonorName());
        block1.setLifeStage(donor2.getLifeStage());
        block1.setReplicateNumber(5);
        block1.setTissueType(tissueType.getName());
        block1.setSpatialLocation(0);
        block1.setLabwareType(lts[1].getName());
        block1.setMedium(medium.getName().toUpperCase());
        block1.setFixative(fixative.getName().toUpperCase());
        block1.setMouldSize(mouldSize.getName().toUpperCase());
        block1.setHighestSection(0);
        block1.setExternalIdentifier("TISSUE1");
        block1.setSpecies(donor2.getSpecies().getName());


        Map<String, Donor> donorMap = Map.of(donor1.getDonorName().toUpperCase(), donor1,
                donor2.getDonorName().toUpperCase(), donor2);
        doReturn(donorMap).when(registerService).createDonors(any(), any());
        SpatialLocation[] sls = {new SpatialLocation(1, "SL0", 1, tissueType),
                new SpatialLocation(2, "SL1", 0, tissueType)};

        when(mockValidation.getSpatialLocation(eqCi(tissueType.getName()), eq(1)))
                .thenReturn(sls[0]);
        when(mockValidation.getSpatialLocation(eqCi(tissueType.getName()), eq(0)))
                .thenReturn(sls[1]);
        when(mockValidation.getMouldSize(eqCi(mouldSize.getName()))).thenReturn(mouldSize);
        when(mockValidation.getMedium(eqCi(medium.getName()))).thenReturn(medium);
        when(mockValidation.getFixative(eqCi(fixative.getName()))).thenReturn(fixative);
        Arrays.stream(lts).forEach(lt -> when(mockValidation.getLabwareType(eqCi(lt.getName()))).thenReturn(lt));
        Arrays.stream(hmdmcs).forEach(h -> when(mockValidation.getHmdmc(eqCi(h.getHmdmc()))).thenReturn(h));
        RegisterRequest request = new RegisterRequest(List.of(block0, block1));
        Labware[] lws = Arrays.stream(lts).map(EntityFactory::makeEmptyLabware).toArray(Labware[]::new);
        Arrays.stream(lws).forEach(lw -> when(mockLabwareService.create(lw.getLabwareType())).thenReturn(lw));

        Tissue[] tissues = new Tissue[]{
                new Tissue(5000, block0.getExternalIdentifier(), block0.getReplicateNumber(),
                        sls[0], donor1, mouldSize, medium, fixative, hmdmcs[0]),
                new Tissue(5001, block1.getExternalIdentifier(), block1.getReplicateNumber(),
                        sls[1], donor2, mouldSize, medium, fixative, null),
        };

        BioState bioState = EntityFactory.getBioState();
        Sample[] samples = new Sample[]{
                new Sample(6000, null, tissues[0], bioState),
                new Sample(6001, null, tissues[1], bioState),
        };

        when(mockTissueRepo.save(any())).thenReturn(tissues[0], tissues[1]);
        when(mockSampleRepo.save(any())).thenReturn(samples[0], samples[1]);

        when(mockSlotRepo.save(any())).then(invocation -> invocation.getArgument(0));

        RegisterResult result = registerService.create(request, user, mockValidation);

        assertEquals(result, new RegisterResult(Arrays.asList(lws), Arrays.asList(tissues)));

        verify(registerService).createDonors(request, mockValidation);

        List<BlockRegisterRequest> blocks = request.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            BlockRegisterRequest block = blocks.get(i);
            verify(mockTissueRepo).save(
                    new Tissue(null,
                            block.getExternalIdentifier(),
                            block.getReplicateNumber(),
                            sls[i],
                            i==0 ? donor1 : donor2,
                            mouldSize,
                            medium,
                            fixative,
                            i==0 ? hmdmcs[i] : null
                    ));
            verify(mockSampleRepo).save(new Sample(null, null, tissues[i], bioState));
            verify(mockLabwareService).create(lts[i]);
            Labware lw = lws[i];
            verify(mockEntityManager).refresh(lw);
            Slot slot = lw.getFirstSlot();
            assertEquals(slot.getBlockHighestSection(), block.getHighestSection());
            assertEquals(slot.getBlockSampleId(), samples[i].getId());
            verify(mockSlotRepo).save(slot);
            verify(mockOpService).createOperationInPlace(opType, user, slot, samples[i]);
        }
    }

    @ParameterizedTest
    @MethodSource("createArgs")
    public void testCreateProblems(Species species, Object hmdmcObj, String expectedErrorMessage) {
        Donor donor = new Donor(100, "DONOR1", LifeStage.adult, species);
        LabwareType lt = EntityFactory.getTubeType();
        TissueType tissueType = EntityFactory.getTissueType();
        MouldSize mouldSize = EntityFactory.getMouldSize();
        Medium medium = EntityFactory.getMedium();
        Fixative fixative = EntityFactory.getFixative();
        Hmdmc hmdmc;
        String hmdmcString;
        if (hmdmcObj instanceof Hmdmc) {
            hmdmc = (Hmdmc) hmdmcObj;
            hmdmcString = hmdmc.getHmdmc();
        } else if (hmdmcObj instanceof String) {
            hmdmc = null;
            hmdmcString = (String) hmdmcObj;
        } else {
            hmdmc = null;
            hmdmcString = null;
        }

        BlockRegisterRequest block = new BlockRegisterRequest();
        block.setDonorIdentifier(donor.getDonorName());
        block.setLifeStage(donor.getLifeStage());
        block.setExternalIdentifier("TISSUE");
        block.setHighestSection(3);
        block.setHmdmc(hmdmcString);
        block.setLabwareType(lt.getName());
        block.setMedium(medium.getName());
        block.setFixative(fixative.getName());
        block.setMouldSize(mouldSize.getName());
        block.setTissueType(tissueType.getName());
        block.setReplicateNumber(2);
        block.setSpatialLocation(1);
        block.setSpecies(species.getName());

        Map<String, Donor> donorMap = Map.of(donor.getDonorName().toUpperCase(), donor);
        doReturn(donorMap).when(registerService).createDonors(any(), any());
        final SpatialLocation sl = new SpatialLocation(1, "SL0", 1, tissueType);

        when(mockValidation.getSpatialLocation(eqCi(tissueType.getName()), eq(1)))
                .thenReturn(sl);
        when(mockValidation.getMouldSize(eqCi(mouldSize.getName()))).thenReturn(mouldSize);
        when(mockValidation.getMedium(eqCi(medium.getName()))).thenReturn(medium);
        when(mockValidation.getFixative(eqCi(fixative.getName()))).thenReturn(fixative);
        when(mockValidation.getLabwareType(eqCi(lt.getName()))).thenReturn(lt);
        if (hmdmc != null) {
            when(mockValidation.getHmdmc(hmdmc.getHmdmc())).thenReturn(hmdmc);
        } else if (hmdmcString!=null) {
            when(mockValidation.getHmdmc(hmdmcString)).thenReturn(null);
        }
        RegisterRequest request = new RegisterRequest(List.of(block));
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        when(mockLabwareService.create(lt)).thenReturn(lw);

        final Tissue tissue = new Tissue(5000, block.getExternalIdentifier(), block.getReplicateNumber(),
                sl, donor, mouldSize, medium, fixative, hmdmc);

        BioState bioState = EntityFactory.getBioState();
        Sample sample = new Sample(6000, null, tissue, bioState);

        when(mockTissueRepo.save(any())).thenReturn(tissue);
        when(mockSampleRepo.save(any())).thenReturn(sample);

        when(mockSlotRepo.save(any())).then(invocation -> invocation.getArgument(0));

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> registerService.create(request, user, mockValidation)))
                    .hasMessage(expectedErrorMessage);
            return;
        } else {
            registerService.create(request, user, mockValidation);
        }

        verify(registerService).createDonors(request, mockValidation);

        verify(mockTissueRepo).save(
                new Tissue(null,
                        block.getExternalIdentifier(),
                        block.getReplicateNumber(),
                        sl,
                        donor,
                        mouldSize,
                        medium,
                        fixative,
                        hmdmc
                ));
        verify(mockSampleRepo).save(new Sample(null, null, tissue, bioState));
        verify(mockLabwareService).create(lt);
        verify(mockEntityManager).refresh(lw);
        Slot slot = lw.getFirstSlot();
        assertEquals(slot.getBlockHighestSection(), block.getHighestSection());
        assertEquals(slot.getBlockSampleId(), sample.getId());
        verify(mockSlotRepo).save(slot);
        verify(mockOpService).createOperationInPlace(opType, user, slot, sample);
    }

    static Stream<Arguments> createArgs() {
        Species human = new Species(1, "Human");
        Species hamster = new Species(2, "Hamster");
        Hmdmc hmdmc = new Hmdmc(10, "20/001");
        // Species species, Hmdmc hmdmc, String expectedErrorMessage
        return Stream.of(
                Arguments.of(human, hmdmc, null),
                Arguments.of(hamster, null, null),
                Arguments.of(human, null, "No HMDMC number given for tissue TISSUE"),
                Arguments.of(hamster, hmdmc, "HMDMC number given for non-human tissue TISSUE"),
                Arguments.of(human, "20/404", "Unknown HMDMC number: 20/404")
        );
    }
}
