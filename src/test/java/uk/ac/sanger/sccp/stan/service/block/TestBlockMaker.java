package uk.ac.sanger.sccp.stan.service.block;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockContent;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Test {@link BlockMakerImp} */
class TestBlockMaker {
    @Mock
    TissueRepo mockTissueRepo;
    @Mock
    SampleRepo mockSampleRepo;
    @Mock
    SlotRepo mockSlotRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    OperationCommentRepo mockOpcomRepo;
    @Mock
    LabwareService mockLwService;
    @Mock
    OperationService mockOpService;
    @Mock
    WorkService mockWorkService;
    @Mock
    BioRiskService mockBioRiskService;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    private BlockMakerImp makeBlockMaker(TissueBlockRequest request, List<BlockLabwareData> blds,
                                         Medium medium, BioState bs, Work work, OperationType opType, User user) {
        return new BlockMakerImp(mockTissueRepo, mockSampleRepo, mockSlotRepo, mockLwRepo, mockOpcomRepo,
                mockLwService, mockOpService, mockWorkService, mockBioRiskService,
                request, blds, medium, bs, work, opType, user);
    }

    @Test
    void testRecord() {
        Work work = EntityFactory.makeWork("SGP1");
        BlockMakerImp maker = spy(makeBlockMaker(null, null, null, null, work, null, null));
        List<Labware> lws = List.of(EntityFactory.getTube());
        Operation op = new Operation();
        op.setId(100);
        List<Operation> ops = List.of(op);
        doReturn(lws).when(maker).createLabware();
        doNothing().when(maker).createSamples();
        doNothing().when(maker).findSlots();
        doNothing().when(maker).fillLabware();
        doNothing().when(maker).discardSources();
        doReturn(ops).when(maker).createOperations();
        OperationResult opres = maker.record();

        verify(maker).createLabware();
        verify(maker).createSamples();
        verify(maker).findSlots();
        verify(maker).fillLabware();
        verify(maker).createOperations();
        verify(mockWorkService).link(work, ops);
        verify(mockBioRiskService).copyOpSampleBioRisks(ops);
        verify(maker).discardSources();

        assertEquals(lws, opres.getLabware());
        assertEquals(ops, opres.getOperations());
    }

    @Test
    void testCreateLabware() {
        LabwareType[] lts = { EntityFactory.getTubeType(),
                EntityFactory.makeLabwareType(1, 1)};
        List<Labware> lws = Arrays.stream(lts).map(EntityFactory::makeEmptyLabware).toList();
        List<TissueBlockLabware> tbls = List.of(new TissueBlockLabware(), new TissueBlockLabware());
        final String prebarcode = "PREB1";
        tbls.get(0).setPreBarcode(prebarcode);
        List<BlockLabwareData> lds = tbls.stream().map(BlockLabwareData::new).toList();
        Zip.of(Arrays.stream(lts), lds.stream()).forEach((lt, ld) -> ld.setLwType(lt));
        Zip.of(Arrays.stream(lts), lws.stream()).forEach((lt, lw) -> when(mockLwService.create(same(lt), any(), any())).thenReturn(lw));
        BlockMakerImp maker = makeBlockMaker(new TissueBlockRequest(tbls), lds, null, null, null, null, null);
        List<Labware> result = maker.createLabware();
        verify(mockLwService).create(lts[0], prebarcode, prebarcode);
        verify(mockLwService).create(lts[1], null, null);
        assertEquals(lws, result);
    }

    @ParameterizedTest
    @CsvSource({
            "rep1,rep1, MED,MED, false",
            ",rep1, MED,MED, true",
            "rep1,rep1, WAT,MED, true",
    })
    void testGetOrCreateTissue(String oldRep, String newRep, String oldMedium, String newMedium, boolean expectNew) {
        Medium oldMed = new Medium(1, oldMedium);
        Medium newMed = oldMedium.equalsIgnoreCase(newMedium) ? oldMed : new Medium(2, newMedium);
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue oldTissue = EntityFactory.makeTissue(donor, sl);
        oldTissue.setReplicate(oldRep);
        oldTissue.setMedium(oldMed);
        when(mockTissueRepo.save(any())).then(Matchers.returnArgument());

        BlockMakerImp maker = makeBlockMaker(null, null, null, null, null, null, null);
        Tissue newTissue = maker.getOrCreateTissue(oldTissue, newRep, newMed);
        if (expectNew) {
            verify(mockTissueRepo).save(newTissue);
            assertNotEquals(oldTissue, newTissue);
            assertNull(newTissue.getId());
            assertEquals(newRep, newTissue.getReplicate());
            assertEquals(newMed, newTissue.getMedium());
        } else {
            verifyNoInteractions(mockTissueRepo);
            assertSame(oldTissue, newTissue);
        }
    }

    @Test
    void testCreateSamples() {
        BioState bs = EntityFactory.getBioState();
        Medium medium = EntityFactory.getMedium();
        Sample[] sourceSamples = EntityFactory.makeSamples(2);
        Tissue altTissue = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        String[] reps = {"rep1", "rep2"};
        List<BlockData> bds = IntStream.range(0, 2).mapToObj(i -> new BlockData(new TissueBlockContent())).toList();
        for (int i = 0; i < sourceSamples.length; ++i) {
            bds.get(i).setSourceSample(sourceSamples[i]);
            bds.get(i).getRequestContent().setReplicate(reps[i]);
        }
        List<BlockLabwareData> bls = List.of(new BlockLabwareData(new TissueBlockLabware()));
        bls.getFirst().setBlocks(bds);
        BlockMakerImp maker = spy(makeBlockMaker(null, bls, medium, bs, null, null, null));
        doAnswer(Matchers.returnArgument()).when(maker).getOrCreateTissue(any(), eq(reps[0]), any());
        doReturn(altTissue).when(maker).getOrCreateTissue(any(), eq(reps[1]), any());
        when(mockSampleRepo.save(any())).then(Matchers.returnArgument());

        maker.createSamples();
        Sample[] newSamples = bds.stream().map(BlockData::getSample).toArray(Sample[]::new);
        for (int i = 0; i < newSamples.length; i++) {
            verify(maker).getOrCreateTissue(same(sourceSamples[i].getTissue()), eq(reps[i]), same(medium));
            Sample sam = newSamples[i];
            assertNull(sam.getId());
            verify(mockSampleRepo).save(sam);
            assertSame(i==0 ? sourceSamples[0].getTissue() : altTissue, sam.getTissue());
            assertEquals(0, sam.getBlockHighestSection());
            assertSame(bs, sam.getBioState());
            assertSame(medium, sam.getTissue().getMedium());
        }
    }

    @Test
    void testFindSlots() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 3);
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware sourceLw = EntityFactory.makeEmptyLabware(lt);
        List<Slot> sourceSlots = sourceLw.getSlots();
        sourceSlots.get(0).addSample(samples[0]);
        sourceSlots.get(1).addSample(samples[1]);
        sourceSlots.get(2).addSample(samples[1]);
        sourceSlots.get(2).addSample(samples[0]);
        Labware destLw = EntityFactory.makeEmptyLabware(lt);
        TissueBlockContent con = new TissueBlockContent();
        Address A1 = new Address(1,1), A2 = new Address(1,2);
        con.setAddresses(List.of(A1, A2));
        BlockData bd = new BlockData(con);
        bd.setSourceLabware(sourceLw);
        bd.setSourceSample(samples[0]);
        BlockLabwareData bl = new BlockLabwareData(new TissueBlockLabware());
        bl.setLabware(destLw);
        bl.setBlocks(List.of(bd));
        BlockMakerImp maker = makeBlockMaker(null, List.of(bl), null, null, null, null, null);
        maker.findSlots();
        assertThat(bd.getSourceSlots()).containsExactlyInAnyOrder(sourceSlots.get(0), sourceSlots.get(2));
        assertThat(bd.getDestSlots()).containsExactlyInAnyOrder(destLw.getSlot(A1), destLw.getSlot(A2));
    }

    @Test
    void testCreateOperations() {
        List<BlockLabwareData> bls = IntStream.range(0,2).mapToObj(i -> new BlockLabwareData(new TissueBlockLabware())).toList();
        Operation[] ops = IntStream.range(10,12).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        BlockMakerImp maker = spy(makeBlockMaker(null, bls, null, null, null, null, null));
        doReturn(ops[0], ops[1]).when(maker).createOperation(any());
        doNothing().when(maker).recordComments(any(), any());
        assertThat(maker.createOperations()).containsExactly(ops);
        bls.forEach(bl -> verify(maker).createOperation(same(bl)));
        Zip.of(bls.stream(), Arrays.stream(ops)).forEach((bl, op) -> verify(maker).recordComments(bl, op.getId()));
    }

    @Test
    void testCreateOperation() {
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        User user = EntityFactory.getUser();
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Sample[] samples = EntityFactory.makeSamples(4);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);
        BlockData bd1 = new BlockData(new TissueBlockContent());
        bd1.setSourceSample(samples[0]);
        bd1.setSourceSlots(List.of(lws[0].getSlot(A1), lws[0].getSlot(A2)));
        bd1.setDestSlots(List.of(lws[2].getSlot(A1)));
        bd1.setSample(samples[2]);
        BlockData bd2 = new BlockData(new TissueBlockContent());
        bd2.setSourceSample(samples[1]);
        bd2.setSample(samples[3]);
        bd2.setSourceSlots(List.of(lws[1].getSlot(A1)));
        bd2.setDestSlots(List.of(lws[2].getSlot(A1), lws[2].getSlot(A2)));
        BlockLabwareData ld = new BlockLabwareData(new TissueBlockLabware());
        ld.setBlocks(List.of(bd1, bd2));

        Operation op = new Operation();
        op.setId(100);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);
        BlockMakerImp maker = makeBlockMaker(null, List.of(ld), null, null, null, opType, user);
        assertThat(maker.createOperations()).containsExactly(op);
        ArgumentCaptor<List<Action>> acCaptor = Matchers.genericCaptor(List.class);
        verify(mockOpService).createOperation(same(opType), same(user), acCaptor.capture(), isNull());
        List<Action> actions = acCaptor.getValue();
        assertThat(actions).containsExactlyInAnyOrder(
                new Action(null, null, lws[0].getSlot(A1), lws[2].getSlot(A1), samples[2], samples[0]),
                new Action(null, null, lws[0].getSlot(A2), lws[2].getSlot(A1), samples[2], samples[0]),
                new Action(null, null, lws[1].getSlot(A1), lws[2].getSlot(A1), samples[3], samples[1]),
                new Action(null, null, lws[1].getSlot(A1), lws[2].getSlot(A2), samples[3], samples[1])
        );
    }

    @Test
    void testRecordComments() {
        final Integer opId = 100;

        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1, 2));
        BlockData bd = new BlockData(new TissueBlockContent());
        Comment com = new Comment(1, "com1", "cat");
        bd.setComment(com);
        Sample sam = EntityFactory.getSample();
        bd.setSample(sam);
        bd.setDestSlots(lw.getSlots());
        BlockData bd0 = new BlockData(new TissueBlockContent());
        bd0.setComment(null);
        BlockLabwareData ld = new BlockLabwareData(new TissueBlockLabware());
        ld.setBlocks(List.of(bd0, bd));

        BlockMakerImp maker = makeBlockMaker(null, List.of(ld), null, null, null, null, null);
        maker.recordComments(ld, opId);
        ArgumentCaptor<List<OperationComment>> comCaptor = Matchers.genericCaptor(List.class);
        verify(mockOpcomRepo).saveAll(comCaptor.capture());
        List<OperationComment> opcoms = comCaptor.getValue();
        assertThat(opcoms).hasSize(2);
        for (OperationComment opcom : opcoms) {
            assertNull(opcom.getId());
            assertSame(com, opcom.getComment());
            assertEquals(opId, opcom.getOperationId());
            assertSame(sam.getId(), opcom.getSampleId());
            assertNull(opcom.getLabwareId());
        }
        Integer[] slotIds = lw.getSlots().stream().map(Slot::getId).toArray(Integer[]::new);
        assertThat(opcoms.stream().map(OperationComment::getSlotId)).containsExactlyInAnyOrder(slotIds);
    }

    @Test
    void testFillLabware() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        LabwareType lt = EntityFactory.makeLabwareType(1,4);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample[] samples = EntityFactory.makeSamples(2);
        List<BlockData> bds = IntStream.range(0,2).mapToObj(i -> {
            BlockData bd = new BlockData(new TissueBlockContent());
            bd.setSample(samples[i]);
            bd.setDestSlots(i==0 ? List.of(lw.getSlot(A1)) : List.of(lw.getSlot(A2), lw.getSlot(A3)));
            return bd;
        }).toList();
        BlockLabwareData bl = new BlockLabwareData(new TissueBlockLabware());
        bl.setBlocks(bds);
        BlockMakerImp maker = makeBlockMaker(null, List.of(bl), null, null, null, null, null);
        maker.fillLabware();
        assertThat(lw.getSlot(A1).getSamples()).containsExactly(samples[0]);
        assertThat(lw.getSlot(A2).getSamples()).containsExactly(samples[1]);
        assertThat(lw.getSlot(A3).getSamples()).containsExactly(samples[1]);
        verify(mockSlotRepo).saveAll(Set.of(lw.getSlot(A1), lw.getSlot(A2), lw.getSlot(A3)));
    }

    @Test
    void testDiscardSources() {
        TissueBlockRequest request = new TissueBlockRequest();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lws = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toList();
        List<BlockData> bds = lws.stream().map(lw -> {
            BlockData bd = new BlockData(new TissueBlockContent());
            bd.setSourceLabware(lw);
            return bd;
        }).toList();
        BlockLabwareData ld = new BlockLabwareData(new TissueBlockLabware());
        ld.setBlocks(bds);
        lws.get(1).setDiscarded(true);
        request.setDiscardSourceBarcodes(lws.subList(0,3).stream().map(Labware::getBarcode).toList());
        BlockMakerImp maker = makeBlockMaker(request, List.of(ld), null, null, null, null, null);
        maker.discardSources();

        Zip.enumerate(lws.stream()).forEach((i, lw) -> assertEquals(i != 3, lw.isDiscarded(), ""+i));
        verify(mockLwRepo).saveAll(Set.of(lws.get(0), lws.get(2)));
    }
}