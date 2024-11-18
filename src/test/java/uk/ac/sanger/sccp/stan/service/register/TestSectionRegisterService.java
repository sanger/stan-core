package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SectionRegisterServiceImp}
 * @author dr6
 */
public class TestSectionRegisterService {
    @Mock private RegisterValidationFactory mockValidationFactory;
    @Mock private DonorRepo mockDonorRepo;
    @Mock private TissueRepo mockTissueRepo;
    @Mock private SampleRepo mockSampleRepo;
    @Mock private MeasurementRepo mockMeasurementRepo;
    @Mock private OperationTypeRepo mockOpTypeRepo;
    @Mock private SlotRepo mockSlotRepo;
    @Mock private SamplePositionRepo mockSamplePositionRepo;

    @Mock private OperationService mockOpService;
    @Mock private LabwareService mockLwService;
    @Mock private WorkService mockWorkService;
    @Mock private BioRiskService mockBioRiskService;

    private User user;

    private SectionRegisterServiceImp regService;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);

        user = EntityFactory.getUser();

        regService = spy(new SectionRegisterServiceImp(mockValidationFactory, mockDonorRepo, mockTissueRepo, mockSampleRepo,
                mockMeasurementRepo, mockOpTypeRepo, mockSlotRepo, mockSamplePositionRepo,
                mockOpService, mockLwService, mockWorkService, mockBioRiskService));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }


    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testRegister(boolean valid) {
        final String workNumber = "SGP1";
        SectionRegisterValidation mockValidation = mock(SectionRegisterValidation.class);
        when(mockValidationFactory.createSectionRegisterValidation(any())).thenReturn(mockValidation);
        SectionRegisterRequest request = new SectionRegisterRequest(List.of(new SectionRegisterLabware()), workNumber);

        if (!valid) {
            doThrow(ValidationException.class).when(mockValidation).throwError();
            assertThrows(ValidationException.class, () -> regService.register(user, request));
            verify(mockValidationFactory).createSectionRegisterValidation(request);
            verify(mockValidation).validate();
            verify(regService, never()).execute(any(), any(), any());
            return;
        }
        ValidatedSections valSec = new ValidatedSections(new UCMap<>(), new UCMap<>(), new UCMap<>(), new UCMap<>(), new UCMap<>(), new Work());
        when(mockValidation.validate()).thenReturn(valSec);

        RegisterResult result = new RegisterResult(List.of());
        doReturn(result).when(regService).execute(any(), any(), any());

        assertSame(result, regService.register(user, request));

        verify(mockValidationFactory).createSectionRegisterValidation(request);
        verify(mockValidation).validate();
        verify(mockValidation).throwError();
        verify(regService).execute(user, request, valSec);
    }

    @Test
    public void testExecute() {
        final String workNumber = "SGP1";
        SectionRegisterRequest request = new SectionRegisterRequest(List.of(), workNumber);
        Work work = new Work();
        work.setId(15);
        UCMap<SlotRegion> regionMap = UCMap.from(SlotRegion::getName, EntityFactory.getSlotRegion());
        UCMap<BioRisk> riskMap = UCMap.from(BioRisk::getCode, new BioRisk(100, "risk1"));
        ValidatedSections valSec = new ValidatedSections(new UCMap<>(), new UCMap<>(), new UCMap<>(), regionMap, riskMap, work);
        UCMap<Donor> donorMap = UCMap.from(Donor::getDonorName, EntityFactory.getDonor());
        UCMap<Tissue> tissueMap = UCMap.from(Tissue::getExternalName, EntityFactory.getTissue());
        UCMap<Sample> sampleMap = UCMap.from(sam -> sam.getTissue().getExternalName(), EntityFactory.getSample());
        UCMap<Labware> lwMap = UCMap.from(Labware::getExternalBarcode, EntityFactory.getTube());
        RegisterResult regResult = new RegisterResult(List.of());

        doReturn(donorMap).when(regService).createDonors(valSec.donorMap().values());
        doReturn(tissueMap).when(regService).createTissues(valSec.sampleMap().values(), donorMap);
        doReturn(sampleMap).when(regService).createSamples(valSec.sampleMap().values(), tissueMap);
        doReturn(lwMap).when(regService).createAllLabware(request, valSec.labwareTypes(), sampleMap);
        doReturn(List.of()).when(regService).recordOperations(user, request, lwMap, sampleMap, regionMap, riskMap, work);
        doReturn(regResult).when(regService).assembleResult(request, lwMap, tissueMap);

        assertSame(regResult, regService.execute(user, request, valSec));

        verify(regService).recordOperations(user, request, lwMap, sampleMap, regionMap, riskMap, work);
    }

    @Test
    public void testAssembleResult() {
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = new Tissue(tissue1.getId() + 1, "TISSUE 2", "10", tissue1.getSpatialLocation(), tissue1.getDonor(),
                tissue1.getMedium(), tissue1.getFixative(), tissue1.getHmdmc(), null, null);
        Tissue tissue3 = new Tissue(tissue1.getId() + 2, "TISSUE 3", "10", tissue1.getSpatialLocation(), tissue1.getDonor(),
                tissue1.getMedium(), tissue1.getFixative(), tissue1.getHmdmc(), null, null);
        UCMap<Tissue> tissueMap = UCMap.from(Tissue::getExternalName, tissue1, tissue2, tissue3);

        BioState bs = EntityFactory.getBioState();
        Sample sample1 = new Sample(101, 1, tissue1, bs);
        Sample sample2 = new Sample(102, 2, tissue2, bs);
        Sample sample3 = new Sample(103, 3, tissue3, bs);
        LabwareType lt = EntityFactory.makeLabwareType(2, 2);
        Labware lw1 = EntityFactory.makeLabware(lt, sample1, sample2);
        lw1.setExternalBarcode("EXT-1");
        Labware lw2 = EntityFactory.makeLabware(lt, sample3);
        lw2.setExternalBarcode("EXT-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getExternalBarcode, lw1, lw2);

        List<SectionRegisterLabware> srls = Stream.of(lw1, lw2)
                .map(lw -> new SectionRegisterLabware(lw.getExternalBarcode(), lt.getName(), lw.getSlots().stream()
                        .flatMap(slot ->
                                slot.getSamples().stream()
                                        .map(sam -> {
                                            SectionRegisterContent content = new SectionRegisterContent();
                                            content.setExternalIdentifier(sam.getTissue().getExternalName());
                                            return content;
                                        })
                        ).collect(toList()))).collect(toList());
        SectionRegisterRequest request = new SectionRegisterRequest(srls, "SGP1");

        RegisterResult result = regService.assembleResult(request, lwMap, tissueMap);
        assertThat(result.getLabware()).containsExactly(lw1, lw2);
    }

    @Test
    public void testCreateDonors() {
        final List<Donor> createdDonors = new ArrayList<>(2);
        final int[] idCounter = {100};
        Species human = EntityFactory.getHuman();
        Donor oldDonor = new Donor(1, "DONOR0", LifeStage.adult, human);
        Donor newDonor1 = new Donor(null, "DONOR1", LifeStage.adult, human);
        Donor newDonor2 = new Donor(null, "DONOR2", LifeStage.adult, human);
        when(mockDonorRepo.saveAll(any())).then(invocation -> {
            Collection<Donor> unsaved = invocation.getArgument(0);
            List<Donor> saved = unsaved.stream()
                    .map(donor -> new Donor(++idCounter[0], donor.getDonorName(), donor.getLifeStage(), donor.getSpecies()))
                    .collect(toList());
            createdDonors.addAll(saved);
            return saved;
        });

        UCMap<Donor> donorMap = regService.createDonors(List.of(oldDonor, newDonor1, newDonor2));

        verify(mockDonorRepo).saveAll(List.of(newDonor1, newDonor2));
        UCMap<Donor> expectedDonorMap = UCMap.from(createdDonors, Donor::getDonorName);
        expectedDonorMap.put(oldDonor.getDonorName(), oldDonor);
        assertEquals(expectedDonorMap, donorMap);
    }

    @Test
    public void testCreateTissues() {
        Donor donor1 = EntityFactory.getDonor();
        Species human = EntityFactory.getHuman();
        Donor donor2 = new Donor(donor1.getId()+1, "DONOR2", LifeStage.adult, human);
        Donor unsavedDonor2 = new Donor(null, "DONOR2", LifeStage.adult, human);
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        final List<Tissue> savedTissues = List.of(EntityFactory.makeTissue(donor1, sl), EntityFactory.makeTissue(donor2, sl));
        final List<Tissue> unsavedTissues = savedTissues.stream()
                .map(savedTissue -> {
                    Donor donor = savedTissue.getDonor();
                    if (donor==donor2) {
                        donor = unsavedDonor2;
                    }
                    Tissue unsavedTissue = EntityFactory.makeTissue(donor, sl);
                    unsavedTissue.setId(null);
                    unsavedTissue.setExternalName(savedTissue.getExternalName());
                    return unsavedTissue;
                }).collect(toList());
        final BioState bs = EntityFactory.getBioState();
        List<Sample> samples = unsavedTissues.stream()
                .map(tis -> new Sample(null, 4, tis, bs))
                .collect(toList());
        when(mockTissueRepo.saveAll(any())).thenReturn(savedTissues);

        UCMap<Tissue> tissueMap = regService.createTissues(samples, UCMap.from(Donor::getDonorName, donor1, donor2));

        assertEquals(UCMap.from(savedTissues, Tissue::getExternalName), tissueMap);
        verify(mockTissueRepo).saveAll(unsavedTissues);
    }

    @Test
    public void testCreateSamples() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        final List<Tissue> tissues = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeTissue(donor, sl))
                .collect(toList());
        BioState bs = EntityFactory.getBioState();
        final List<Sample> savedSamples = tissues.stream()
                .map(t -> new Sample(20*t.getId(), 1, t, bs))
                .collect(toList());
        final List<Sample> unsavedSamples = savedSamples.stream()
                .map(sam -> {
                    Tissue unsavedTissue = new Tissue();
                    unsavedTissue.setExternalName(sam.getTissue().getExternalName());
                    return new Sample(null, 1, unsavedTissue, bs);
                })
                .collect(toList());
        when(mockSampleRepo.saveAll(any())).thenReturn(savedSamples);

        UCMap<Sample> sampleMap = regService.createSamples(unsavedSamples, UCMap.from(tissues, Tissue::getExternalName));

        verify(mockSampleRepo).saveAll(unsavedSamples);
        assertEquals(tissues.get(0), unsavedSamples.get(0).getTissue());
        assertEquals(tissues.get(1), unsavedSamples.get(1).getTissue());
        assertEquals(UCMap.from(savedSamples, sam -> sam.getTissue().getExternalName()), sampleMap);
    }

    @Test
    public void testCreateAllLabware() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.setExternalBarcode("EXT"+i);
                    return lw;
                }).collect(toList());
        List<SectionRegisterLabware> srls = labware.stream()
                .map(lw -> new SectionRegisterLabware(lw.getExternalBarcode(), lt.getName(), null))
                .collect(toList());

        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, lt);
        UCMap<Sample> sampleMap = UCMap.from(sam -> sam.getTissue().getExternalName(), EntityFactory.getSample());
        IntStream.range(0,2).forEach(i -> doReturn(labware.get(i)).when(regService).createLabware(srls.get(i), lwTypes, sampleMap));

        UCMap<Labware> lwMap = regService.createAllLabware(new SectionRegisterRequest(srls, "SGP1"), lwTypes, sampleMap);

        srls.forEach(srl -> verify(regService).createLabware(srl, lwTypes, sampleMap));
        assertEquals(UCMap.from(labware, Labware::getExternalBarcode), lwMap);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testCreateLabware(boolean prebarcode) {
        LabwareType lt = EntityFactory.makeLabwareType(2, 2);
        lt.setPrebarcoded(prebarcode);
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lt);
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        Tissue tissue3 = EntityFactory.makeTissue(tissue1.getDonor(), tissue1.getSpatialLocation());
        BioState bs = EntityFactory.getBioState();
        Sample sample1 = new Sample(1, 1, tissue1, bs);
        Sample sample2 = new Sample(2, 1, tissue2, bs);
        Sample sample3 = new Sample(3, 1, tissue3, bs);
        String xb = "EXT1";
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setExternalBarcode(xb);
        when(mockLwService.create(any(), any(), any())).thenReturn(lw);
        final Address A1 = new Address(1, 1);
        final Address B2 = new Address(2,2);
        SectionRegisterLabware srl = new SectionRegisterLabware(xb, lt.getName(), List.of(
                content(A1, tissue1.getExternalName()),
                content(A1, tissue2.getExternalName()),
                content(B2, tissue3.getExternalName())
        ));

        Labware result = regService.createLabware(srl, ltMap, UCMap.from(sam -> sam.getTissue().getExternalName(), sample1, sample2, sample3));

        verify(mockLwService).create(lt, prebarcode ? xb : null, xb);

        verify(mockSlotRepo, times(2)).save(lw.getSlot(A1));
        verify(mockSlotRepo).save(lw.getSlot(B2));

        assertEquals(lw, result);
        assertThat(lw.getSlot(A1).getSamples()).containsExactlyInAnyOrder(sample1, sample2);
        assertThat(lw.getSlot(B2).getSamples()).containsExactly(sample3);
        assertThat(lw.getSlot(new Address(1,2)).getSamples()).isEmpty();
    }

    private SectionRegisterContent content(Address address, String extName) {
        return content(address, extName, (Integer) null);
    }

    private SectionRegisterContent content(Address address, String extName, String regionName) {
        SectionRegisterContent src = new SectionRegisterContent();
        src.setAddress(address);
        src.setExternalIdentifier(extName);
        src.setRegion(regionName);
        return src;
    }

    private SectionRegisterContent content(Address address, String extName, Integer thickness) {
        SectionRegisterContent content = new SectionRegisterContent();
        content.setAddress(address);
        content.setExternalIdentifier(extName);
        content.setSectionThickness(thickness);
        return content;
    }

    @Test
    public void testRecordOperations() {
        final OperationType opType = new OperationType(1, "Register");
        when(mockOpTypeRepo.getByName("Register")).thenReturn(opType);
        Operation[] ops = IntStream.range(0,2)
                .mapToObj(i -> new Operation(1, opType, null, null, null))
                .toArray(Operation[]::new);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,2)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.setExternalBarcode("EXT"+i);
                    return lw;
                })
                .toArray(Labware[]::new);
        SectionRegisterLabware[] srls = Arrays.stream(labware)
                .map(lw -> new SectionRegisterLabware(lw.getExternalBarcode(), lt.getName(), null))
                .toArray(SectionRegisterLabware[]::new);
        UCMap<Sample> sampleMap = UCMap.from(sam -> sam.getTissue().getExternalName(), EntityFactory.getSample());
        UCMap<SlotRegion> regionMap = UCMap.from(SlotRegion::getName, EntityFactory.getSlotRegion());
        SectionRegisterRequest request = new SectionRegisterRequest(Arrays.asList(srls), "SGP1");
        UCMap<Labware> lwMap = UCMap.from(Labware::getExternalBarcode, labware);
        UCMap<BioRisk> riskMap = UCMap.from(BioRisk::getCode, new BioRisk(1, "risk1"));
        for (int i = 0; i < labware.length; ++i) {
            doReturn(ops[i]).when(regService).createOp(user, opType, labware[i]);
        }
        doReturn(List.of()).when(regService).createMeasurements(any(), any(), any(), any());
        doReturn(List.of()).when(regService).createSamplePositions(any(), any(), any(), any(), any());
        Work work = new Work();
        work.setId(15);

        List<Operation> operations = regService.recordOperations(user, request, lwMap, sampleMap, regionMap, riskMap, work);

        for (int i = 0; i < labware.length; i++) {
            Labware lw = labware[i];
            verify(regService).createOp(user, opType, lw);
            verify(regService).createMeasurements(srls[i], lw, ops[i], sampleMap);
            verify(regService).createSamplePositions(srls[i], lw, ops[i].getId(), sampleMap, regionMap);
            verify(regService).linkBioRisks(srls[i], ops[i].getId(), sampleMap, riskMap);
        }
        verify(mockWorkService).link(work, operations);
        assertThat(operations).containsExactly(ops);
    }

    @Test
    public void testCreateOp() {
        OperationType opType = new OperationType(1, "Register");
        Operation op = new Operation(10, opType, null, null, user);
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Address A1 = new Address(1,1);
        Address B2 = new Address(2,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final Donor donor = EntityFactory.getDonor();
        final SpatialLocation sl = EntityFactory.getSpatialLocation();
        final BioState bs = EntityFactory.getBioState();
        Sample[] samples = IntStream.range(0,3)
                .mapToObj(i -> {
                    Tissue tissue = EntityFactory.makeTissue(donor, sl);
                    return new Sample(10+i, 5+i, tissue, bs);
                })
                .toArray(Sample[]::new);
        final Slot slotA1 = lw.getSlot(A1);
        final Slot slotB2 = lw.getSlot(B2);
        slotA1.getSamples().addAll(List.of(samples[0], samples[1]));
        slotB2.getSamples().add(samples[2]);

        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);

        assertSame(op, regService.createOp(user, opType, lw));

        List<Action> expectedActions = List.of(
                new Action(null, null, slotA1, slotA1, samples[0], samples[0]),
                new Action(null, null, slotA1, slotA1, samples[1], samples[1]),
                new Action(null, null, slotB2, slotB2, samples[2], samples[2])
        );
        verify(mockOpService).createOperation(opType, user, expectedActions, null);
    }

    @Test
    public void testCreateMeasurements() {
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Address A1 = new Address(1,1);
        Address B1 = new Address(1, 2);
        Address B2 = new Address(2,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample[] samples = makeSamples(4);
        String[] xns = Arrays.stream(samples)
                .map(sam -> sam.getTissue().getExternalName())
                .toArray(String[]::new);
        final Slot slotA1 = lw.getSlot(A1);
        final Slot slotB1 = lw.getSlot(B1);
        final Slot slotB2 = lw.getSlot(B2);
        slotA1.getSamples().addAll(List.of(samples[0], samples[1]));
        slotB1.getSamples().add(samples[2]);
        slotB2.getSamples().add(samples[3]);

        Operation op = new Operation(200, new OperationType(1, "Register", 0, null), null, null, null);

        List<SectionRegisterContent> contents = List.of(
                content(A1, xns[0], 14),
                content(A1, xns[1], 15),
                content(B1, xns[2]),
                content(B2, xns[3])
        );
        contents.get(1).setDateSectioned(LocalDate.of(2024,1,13));
        contents.get(3).setDateSectioned(LocalDate.of(2024,2,14));

        UCMap<Sample> sampleMap = UCMap.from((Sample sam) -> sam.getTissue().getExternalName(), samples);
        SectionRegisterLabware srl = new SectionRegisterLabware(lw.getExternalBarcode(), lt.getName(), contents);

        final Integer opId = op.getId();
        List<Measurement> savedMeasurements = List.of(
                new Measurement(10, "Thickness", "14", samples[0].getId(), opId, slotA1.getId())
        );

        when(mockMeasurementRepo.saveAll(any())).thenReturn(savedMeasurements);

        assertEquals(savedMeasurements, regService.createMeasurements(srl, lw, op, sampleMap));

        verify(mockMeasurementRepo).saveAll(List.of(
                new Measurement(null, "Thickness", "14", samples[0].getId(), opId, slotA1.getId()),
                new Measurement(null, "Thickness", "15", samples[1].getId(), opId, slotA1.getId()),
                new Measurement(null, "Date sectioned", "2024-01-13", samples[1].getId(), opId, slotA1.getId()),
                new Measurement(null, "Date sectioned", "2024-02-14", samples[3].getId(), opId, slotB2.getId())
        ));
    }


    @Test
    public void testCreateSamplePositions() {
        final SlotRegion top = new SlotRegion(1, "Top");
        final SlotRegion bottom = new SlotRegion(2, "Bottom");
        UCMap<SlotRegion> regionMap = UCMap.from(SlotRegion::getName, top, bottom);
        Sample[] samples = makeSamples(3);
        String[] xns = Arrays.stream(samples).map(sam -> sam.getTissue().getExternalName()).toArray(String[]::new);
        UCMap<Sample> sampleMap = UCMap.from((Sample sam) -> sam.getTissue().getExternalName(), samples);
        final Address A1 = new Address(1,1),
                A2 = new Address(1,2),
                A3 = new Address(1,3);
        Integer opId = 17;
        List<SamplePosition> savedSamps = List.of(new SamplePosition(100, 200, top, opId));
        when(mockSamplePositionRepo.saveAll(any())).thenReturn(savedSamps);

        List<SectionRegisterContent> srcs = List.of(
                content(A1, xns[0], "top"),
                content(A1, xns[1], "bottom"),
                content(A2, xns[2], ""),
                content(A3, xns[0], "BOTTOM")
        );
        LabwareType lt = EntityFactory.makeLabwareType(1,3);

        Labware lw = EntityFactory.makeLabware(lt);
        lw.getSlot(A1).setSamples(List.of(samples[0], samples[1]));
        lw.getSlot(A2).setSamples(List.of(samples[2]));
        lw.getSlot(A3).setSamples(List.of(samples[0]));

        SectionRegisterLabware srl = new SectionRegisterLabware("xbc", "lwt", srcs);

        assertSame(savedSamps, regService.createSamplePositions(srl, lw, opId, sampleMap, regionMap));

        verify(mockSamplePositionRepo).saveAll(List.of(
                new SamplePosition(lw.getSlot(A1).getId(), samples[0].getId(), top, opId),
                new SamplePosition(lw.getSlot(A1).getId(), samples[1].getId(), bottom, opId),
                new SamplePosition(lw.getSlot(A3).getId(), samples[0].getId(), bottom, opId)
        ));
    }


    private static Sample[] makeSamples(int numSamples) {
        final Donor donor = EntityFactory.getDonor();
        final SpatialLocation sl = EntityFactory.getSpatialLocation();
        BioState bs = EntityFactory.getBioState();
        return IntStream.range(0,numSamples)
                .mapToObj(i -> {
                    Tissue tissue = EntityFactory.makeTissue(donor, sl);
                    return new Sample(10+i, 5+i, tissue, bs);
                })
                .toArray(Sample[]::new);
    }
}
