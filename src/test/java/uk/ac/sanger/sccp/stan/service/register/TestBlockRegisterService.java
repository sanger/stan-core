package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/** Tests {@link BlockRegisterServiceImp} */
class TestBlockRegisterService {
    @Mock
    EntityManager mockEntityManager;
    @Mock
    RegisterValidationFactory mockValidationFactory;
    @Mock
    DonorRepo mockDonorRepo;
    @Mock
    TissueRepo mockTissueRepo;
    @Mock
    SampleRepo mockSampleRepo;
    @Mock
    SlotRepo mockSlotRepo;
    @Mock
    BioRiskRepo mockBioRiskRepo;
    @Mock
    OperationTypeRepo mockOpTypeRepo;
    @Mock
    LabwareService mockLabwareService;
    @Mock
    OperationService mockOperationService;
    @Mock
    WorkService mockWorkService;
    @Mock
    RegisterClashChecker mockClashChecker;

    @InjectMocks
    BlockRegisterServiceImp service;

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

    @Test
    void testRegister_none() {
        RegisterResult result = service.register(EntityFactory.getUser(), new BlockRegisterRequest());
        verifyNoInteractions(mockClashChecker);
        verifyNoInteractions(mockValidationFactory);
        verify(service, never()).updateExistingTissues(any(), any());
        verify(service, never()).create(any(), any(), any());
        assertThat(result.getLabware()).isEmpty();
        assertThat(result.getClashes()).isEmpty();
        assertThat(result.getLabwareSolutions()).isEmpty();
    }

    @Test
    void testRegister_clash() {
        List<RegisterClash> clashes = List.of(new RegisterClash());
        when(mockClashChecker.findClashes(any(BlockRegisterRequest.class))).thenReturn(clashes);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(new BlockRegisterLabware()));
        RegisterResult result = service.register(EntityFactory.getUser(), request);
        verify(mockClashChecker).findClashes(request);
        verifyNoInteractions(mockValidationFactory);
        verify(service, never()).updateExistingTissues(any(), any());
        verify(service, never()).create(any(), any(), any());

        assertThat(result.getLabware()).isEmpty();
        assertEquals(clashes, result.getClashes());
        assertThat(result.getLabwareSolutions()).isEmpty();
    }

    @Test
    void testRegister_problems() {
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(new BlockRegisterLabware()));
        when(mockClashChecker.findClashes(any(BlockRegisterRequest.class))).thenReturn(List.of());
        RegisterValidation validation = mock(RegisterValidation.class);
        when(mockValidationFactory.createBlockRegisterValidation(any())).thenReturn(validation);
        List<String> problems = List.of("Bad thing");
        when(validation.validate()).thenReturn(problems);
        assertValidationException(() -> service.register(EntityFactory.getUser(), request), problems);
        verify(mockClashChecker).findClashes(request);
        verify(mockValidationFactory).createBlockRegisterValidation(request);
        verify(validation).validate();
        verify(service, never()).updateExistingTissues(any(), any());
        verify(service, never()).create(any(), any(), any());
    }

    @Test
    void testRegister_ok() {
        User user = EntityFactory.getUser();
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(new BlockRegisterLabware()));
        when(mockClashChecker.findClashes(any(BlockRegisterRequest.class))).thenReturn(List.of());
        RegisterValidation validation = mock(RegisterValidation.class);
        when(mockValidationFactory.createBlockRegisterValidation(any())).thenReturn(validation);
        when(validation.validate()).thenReturn(List.of());
        doNothing().when(service).updateExistingTissues(any(), any());
        RegisterResult result = new RegisterResult(List.of(EntityFactory.getTube()));
        doReturn(result).when(service).create(any(), any(), any());
        assertSame(result, service.register(user, request));
        verify(mockClashChecker).findClashes(request);
        verify(mockValidationFactory).createBlockRegisterValidation(request);
        verify(validation).validate();
        verify(service).updateExistingTissues(request, validation);
        verify(service).create(request, user, validation);
    }

    @Test
    void testUpdateExistingTissues_none() {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setSamples(List.of(
                brsForExistingTissue("EXT1", LocalDate.of(2024,1,1), false),
                brsForExistingTissue("EXT2", null, true)
        ));
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        RegisterValidation validation = mock(RegisterValidation.class);

        service.updateExistingTissues(request, validation);
        verifyNoInteractions(validation);
        verifyNoInteractions(mockTissueRepo);
    }

    @Test
    void testUpdateExistingTissues_some() {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        LocalDate newDate = LocalDate.of(2024,1,1);
        brl.setSamples(List.of(
                brsForExistingTissue("EXT1", LocalDate.of(2024,1,1), true),
                brsForExistingTissue("EXT2", LocalDate.of(2023,1,1), false),
                brsForExistingTissue("EXT3", null, true)
        ));
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        RegisterValidation validation = mock(RegisterValidation.class);
        Tissue tissue = new Tissue();
        tissue.setExternalName("EXT1");
        when(validation.getTissue("EXT1")).thenReturn(tissue);

        service.updateExistingTissues(request, validation);
        verify(mockTissueRepo).saveAll(List.of(tissue));
        verifyNoMoreInteractions(mockTissueRepo);
        assertEquals(newDate, tissue.getCollectionDate());
    }

    static BlockRegisterSample brsForExistingTissue(String externalName, LocalDate date, boolean existing) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(externalName);
        brs.setExistingTissue(existing);
        brs.setSampleCollectionDate(date);
        return brs;
    }

    @Test
    void testCreateDonors() {
        List<BlockRegisterSample> brss = List.of(
                brsForDonor("DONOR1"),
                brsForDonor("DONOR2"),
                brsForDonor("Donor1"),
                brsForDonor("DONORA")
        );
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setSamples(brss);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));

        Donor donor1 = makeDonor(null, "DONOR1");
        Donor donor2 = makeDonor(null, "DONOR2");
        Donor donorA = makeDonor(1, "DONORA");
        List<Donor> donors = List.of(donor1, donor2, donorA);
        RegisterValidation val = mock(RegisterValidation.class);
        donors.forEach(d -> when(val.getDonor(eqCi(d.getDonorName()))).thenReturn(d));

        final int[] idCounter = {5};
        when(mockDonorRepo.save(any())).then(invocation -> {
            Donor d = invocation.getArgument(0);
            assertNull(d.getId());
            d.setId(++idCounter[0]);
            return d;
        });

        UCMap<Donor> donorMap = service.createDonors(request, val);
        assertThat(donorMap).hasSize(donors.size());
        donors.forEach(d -> assertSame(d, donorMap.get(d.getDonorName())));
        verify(mockDonorRepo).save(donor1);
        verify(mockDonorRepo).save(donor2);
        verifyNoMoreInteractions(mockDonorRepo);
        assertEquals(1, donorA.getId());
        assertNotNull(donor1.getId());
        assertNotNull(donor2.getId());
        assertNotEquals(donor1.getId(), donor2.getId());
    }

    static BlockRegisterSample brsForDonor(String donorName) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setDonorIdentifier(donorName);
        return brs;
    }

    @Test
    void testCreateTissues() {
        List<Donor> donors = IntStream.of(1,2).mapToObj(i -> makeDonor(i, "DONOR"+i)).toList();
        UCMap<Donor> donorMap = UCMap.from(donors, Donor::getDonorName);
        doReturn(donorMap).when(service).createDonors(any(), any());
        List<Hmdmc> hmdmcs = List.of(new Hmdmc(1, "2000/1"), new Hmdmc(2, "2000/2"));
        RegisterValidation val = mock(RegisterValidation.class);
        hmdmcs.forEach(h -> when(val.getHmdmc(h.getHmdmc())).thenReturn(h));
        List<CellClass> cellClasses = List.of(new CellClass(1, "CC1", false, true), new CellClass(2, "CC2", false, true));
        cellClasses.forEach(cc -> when(val.getCellClass(eqCi(cc.getName()))).thenReturn(cc));
        TissueType tt = new TissueType(1, "TT1", "TT1");
        tt.setSpatialLocations(List.of(new SpatialLocation(10, "SL0", 0, tt),
                new SpatialLocation(11, "SL1", 1, tt)));
        tt.getSpatialLocations().forEach(sl -> when(val.getSpatialLocation("TT1", sl.getCode())).thenReturn(sl));
        List<Medium> mediums = List.of(new Medium(1, "med1"), new Medium(2, "med2"));
        mediums.forEach(m -> when(val.getMedium(eqCi(m.getName()))).thenReturn(m));
        List<Fixative> fixs = List.of(new Fixative(1, "fix1"), new Fixative(2, "fix2"));
        fixs.forEach(f -> when(val.getFixative(eqCi(f.getName()))).thenReturn(f));

        BlockRegisterSample brs1 = new BlockRegisterSample();
        brs1.setExternalIdentifier("EXT1");
        brs1.setHmdmc("2000/1");
        brs1.setCellClass("CC1");
        brs1.setReplicateNumber("R1");
        brs1.setSampleCollectionDate(LocalDate.of(2026,1,1));
        brs1.setTissueType("TT1");
        brs1.setSpatialLocation(0);
        brs1.setDonorIdentifier("DONOR1");

        BlockRegisterSample brs2 = new BlockRegisterSample();
        brs2.setExternalIdentifier("EXT2");
        brs2.setHmdmc("2000/2");
        brs2.setCellClass("CC2");
        brs2.setReplicateNumber("R2");
        brs2.setSampleCollectionDate(LocalDate.of(2026,1,2));
        brs2.setTissueType("TT1");
        brs2.setSpatialLocation(1);
        brs2.setDonorIdentifier("DONOR2");

        BlockRegisterSample brs3 = new BlockRegisterSample();
        brs3.setExternalIdentifier("EXT3");
        brs3.setHmdmc("2000/1");
        brs3.setCellClass("CC1");
        brs3.setReplicateNumber("R1");
        brs3.setSampleCollectionDate(LocalDate.of(2026,1,3));
        brs3.setTissueType("TT1");
        brs3.setSpatialLocation(0);
        brs3.setDonorIdentifier("DONOR1");

        Tissue existingTissue = new Tissue();
        existingTissue.setId(1);
        existingTissue.setExternalName("EXT3");
        when(val.getTissue(eqCi("EXT3"))).thenReturn(existingTissue);

        final int[] idCounter = {5};
        when(mockTissueRepo.save(any())).then(invocation -> {
            Tissue t = invocation.getArgument(0);
            assertNull(t.getId());
            t.setId(++idCounter[0]);
            return t;
        });

        BlockRegisterLabware brl1 = new BlockRegisterLabware();
        brl1.setSamples(List.of(brs1));
        brl1.setMedium("med1");
        brl1.setFixative("fix1");
        BlockRegisterLabware brl2 = new BlockRegisterLabware();
        brl2.setSamples(List.of(brs2, brs3));
        brl2.setMedium("med2");
        brl2.setFixative("fix2");
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl1, brl2));

        UCMap<Tissue> tissueMap = service.createTissues(request, val);

        assertThat(tissueMap).hasSize(3);
        assertSame(existingTissue, tissueMap.get("EXT3"));
        assertEquals(1, existingTissue.getId());

        List<Tissue> newTissues = Stream.of("EXT1", "EXT2").map(tissueMap::get).toList();
        for (int i = 0; i < newTissues.size(); i++) {
            Tissue tis = newTissues.get(i);
            assertNotNull(tis);
            assertNotNull(tis.getId());
            assertSame(mediums.get(i), tis.getMedium());
            assertSame(fixs.get(i), tis.getFixative());
            assertSame(donors.get(i), tis.getDonor());
            assertSame(hmdmcs.get(i), tis.getHmdmc());
            assertSame(cellClasses.get(i), tis.getCellClass());
            assertSame(tt, tis.getTissueType());
            assertSame(tt.getSpatialLocations().get(i), tis.getSpatialLocation());
            assertEquals(request.getLabware().get(i).getSamples().getFirst().getSampleCollectionDate(), tis.getCollectionDate());
            assertEquals("EXT"+(i+1), tis.getExternalName());
            assertEquals("r"+(i+1), tis.getReplicate());
        }
        assertNotEquals(newTissues.get(0).getId(), newTissues.get(1).getId());
        verify(mockTissueRepo, times(2)).save(any());
    }

    @Test
    void testCreate() {
        User user = EntityFactory.getUser();
        List<Work> works = List.of(EntityFactory.makeWork("SGP1"));
        RegisterValidation val = mock(RegisterValidation.class);
        when(val.getWorks()).thenReturn(works);
        Tissue[] tissues = IntStream.of(1,2,3).mapToObj(i -> makeTissue(i, "EXT"+i)).toArray(Tissue[]::new);
        doReturn(UCMap.from(Tissue::getExternalName, tissues)).when(service).createTissues(any(), any());
        OperationType opType = EntityFactory.makeOperationType("Register", EntityFactory.getBioState());
        when(mockOpTypeRepo.getByName(eqCi(opType.getName()))).thenReturn(opType);
        LabwareType lt = new LabwareType(1, "lt", 1, 2, null, false);
        when(val.getLabwareType(eqCi(lt.getName()))).thenReturn(lt);
        BioRisk[] bioRisks = IntStream.of(1,2,3).mapToObj(i -> new BioRisk(i, "BR"+i)).toArray(BioRisk[]::new);
        Arrays.stream(bioRisks).forEach(br -> when(val.getBioRisk(eqCi(br.getCode()))).thenReturn(br));
        Labware[] lws = Stream.of("XB1", "XB2").map(xb -> {
            Labware lw = EntityFactory.makeEmptyLabware(lt);
            lw.setExternalBarcode(xb);
            lw.setBarcode(xb);
            when(mockLabwareService.create(any(), eqCi(xb), eqCi(xb))).thenReturn(lw);
            return lw;
        }).toArray(Labware[]::new);

        final int[] idCounter = {20};
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample sam = invocation.getArgument(0);
            assertNull(sam.getId());
            sam.setId(++idCounter[0]);
            return sam;
        });

        Operation[] ops = IntStream.of(100,101).mapToObj(i -> {
            Operation op = new Operation();
            op.setOperationType(opType);
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        when(mockOperationService.createOperation(any(), any(), any(), isNull())).thenReturn(ops[0], ops[1]);

        final Address A1 = new Address(1,1), A2 = new Address(1,2);

        List<BlockRegisterSample> brs1 = List.of(
                brsForCreate("EXT1", 31, "BR1", List.of(A1, A2))
        );
        List<BlockRegisterSample> brs2 = List.of(
                brsForCreate("EXT2", 32, "BR2", List.of(A1)),
                brsForCreate("EXT3", 33, "BR3", List.of(A2))
        );
        BlockRegisterLabware brl1 = brlForCreate(lt.getName(), lws[0].getExternalBarcode(), brs1);
        BlockRegisterLabware brl2 = brlForCreate(lt.getName(), lws[1].getExternalBarcode(), brs2);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl1, brl2));

        RegisterResult result = service.create(request, user, val);

        verify(service).createTissues(request, val);
        verify(mockOpTypeRepo).getByName(eqCi(opType.getName()));
        for (Labware lw : lws) {
            verify(mockLabwareService).create(lt, lw.getExternalBarcode(), lw.getBarcode());
        }
        verifyNoMoreInteractions(mockLabwareService);
        verify(mockSampleRepo, times(3)).save(any());
        Arrays.stream(lws).forEach(lw -> verify(mockEntityManager).refresh(lw));
        verify(mockSlotRepo).saveAll(Set.of(lws[0].getSlot(A1), lws[0].getSlot(A2)));
        verify(mockSlotRepo).saveAll(Set.of(lws[1].getSlot(A1), lws[1].getSlot(A2)));
        verifyNoMoreInteractions(mockSlotRepo);
        ArgumentCaptor<List<Action>> actionsCaptor = genericCaptor(List.class);
        verify(mockOperationService, times(2)).createOperation(same(opType), same(user),
                actionsCaptor.capture(), isNull());
        verifyNoMoreInteractions(mockOperationService);
        verify(mockWorkService).link(same(works), eq(Arrays.asList(ops)));
        verifyNoMoreInteractions(mockWorkService);
        List<List<Action>> actionses = actionsCaptor.getAllValues();
        assertThat(actionses).hasSize(2);
        List<Action> actions = actionses.getFirst();
        assertThat(actions).hasSize(2);
        List<Sample> samples = new ArrayList<>(3);
        Sample sam1 = actions.getFirst().getSample();
        samples.add(sam1);
        Zip.of(actions.stream(), lws[0].getSlots().stream()).forEach((a, slot) -> {
            assertSame(slot, a.getSource());
            assertSame(slot, a.getDestination());
            assertSame(sam1, a.getSourceSample());
            assertSame(sam1, a.getSample());
        });
        actions = actionses.get(1);
        assertThat(actions).hasSize(2);
        Zip.of(actions.stream(), lws[1].getSlots().stream()).forEach((a, slot) -> {
            assertSame(slot, a.getSource());
            assertSame(slot, a.getDestination());
            assertSame(a.getSample(), a.getSourceSample());
            samples.add(a.getSample());
        });

        int[] opIds = {100, 101, 101};
        Zip.enumerate(samples.stream()).forEach((i, sam) -> {
            assertNotNull(sam.getId());
            assertEquals(31+i, sam.getBlockHighestSection());
            assertSame(tissues[i], sam.getTissue());
            assertSame(opType.getNewBioState(), sam.getBioState());
            verify(mockBioRiskRepo).recordBioRisk(sam, bioRisks[i], opIds[i]);
        });
        verifyNoMoreInteractions(mockBioRiskRepo);
        assertThat(result.getLabware()).containsExactly(lws);
    }

    static BlockRegisterLabware brlForCreate(String lt, String xb, List<BlockRegisterSample> samples) {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setLabwareType(lt);
        brl.setExternalBarcode(xb);
        brl.setSamples(samples);
        return brl;
    }

    static BlockRegisterSample brsForCreate(String extName, Integer highestSection, String bioRiskCode,
                                            List<Address> addresses) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(extName);
        brs.setHighestSection(highestSection);
        brs.setBioRiskCode(bioRiskCode);
        brs.setAddresses(addresses);
        return brs;
    }

    static Tissue makeTissue(Integer id, String extName) {
        Tissue tis = new Tissue();
        tis.setId(id);
        tis.setExternalName(extName);
        return tis;
    }

    static Donor makeDonor(Integer id, String name) {
        return new Donor(id, name, LifeStage.adult, EntityFactory.getHuman());
    }
}
