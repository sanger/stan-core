package uk.ac.sanger.sccp.stan.service.block;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockContent;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/** Tests {@link BlockValidatorImp} */
class TestBlockValidator {
    @Mock
    LabwareValidatorFactory mockLwValFactory;
    @Mock
    Validator<String> mockPrebarcodeValidator;
    @Mock
    Validator<String> mockReplicateValidator;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    OperationTypeRepo mockOpTypeRepo;
    @Mock
    LabwareTypeRepo mockLtRepo;
    @Mock
    BioStateRepo mockBsRepo;
    @Mock
    TissueRepo mockTissueRepo;
    @Mock
    MediumRepo mockMediumRepo;
    @Mock
    CommentValidationService mockCommentValidationService;
    @Mock
    WorkService mockWorkService;

    private AutoCloseable mocking;
    
    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    BlockValidatorImp makeVal(TissueBlockRequest request) {
        return new BlockValidatorImp(mockLwValFactory, mockPrebarcodeValidator, mockReplicateValidator,
                mockLwRepo, mockOpTypeRepo, mockLtRepo, mockBsRepo, mockTissueRepo, mockMediumRepo,
                mockCommentValidationService, mockWorkService,
                request);
    }

    @Test
    void testValidate_noLabware() {
        TissueBlockRequest request = new TissueBlockRequest();
        BlockValidatorImp val = makeVal(request);
        val.validate();
        assertProblem(val.getProblems(), "No labware specified.");
    }

    @Test
    void testValidate() {
        TissueBlockRequest request = new TissueBlockRequest();
        TissueBlockLabware rlw = new TissueBlockLabware();
        TissueBlockContent content = new TissueBlockContent();
        content.setAddresses(List.of(new Address(1,1)));
        rlw.setContents(List.of(content));
        request.setLabware(List.of(rlw));

        BlockValidatorImp val = spy(makeVal(request));
        doNothing().when(val).loadEntities();
        doNothing().when(val).checkDestAddresses();
        doNothing().when(val).checkPrebarcodes();
        doNothing().when(val).checkReplicates();
        doAnswer(addProblem(val, "Bad barcode")).when(val).checkDiscardBarcodes();

        val.validate();

        InOrder inOrder = inOrder(val);
        inOrder.verify(val).loadEntities();
        inOrder.verify(val).checkDestAddresses();
        inOrder.verify(val).checkPrebarcodes();
        inOrder.verify(val).checkReplicates();
        inOrder.verify(val).checkDiscardBarcodes();

        assertThat(val.getLwData()).containsExactly(new BlockLabwareData(request.getLabware().getFirst()));
        assertThat(val.getProblems()).containsExactly("Bad barcode");
    }

    static <X> Answer<X> addProblem(BlockValidatorImp val, String problem, X returnValue) {
        return invocation -> {
            val.getProblems().add(problem);
            return returnValue;
        };
    }

    static Answer<Void> addProblem(BlockValidatorImp val, String problem) {
        return addProblem(val, problem, null);
    }

    @Test
    void testLoadEntities() {
        Work work = EntityFactory.makeWork("SGP1");
        OperationType opType = EntityFactory.makeOperationType("Block processing", null);
        BioState bs = EntityFactory.getBioState();
        Medium medium = EntityFactory.getMedium();
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        when(mockBsRepo.findByName(any())).thenReturn(Optional.of(bs));
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.of(opType));
        when(mockMediumRepo.findByName(any())).thenReturn(Optional.of(medium));
        BlockValidatorImp val = spy(makeVal(new TissueBlockRequest()));
        doNothing().when(val).loadSources();
        doNothing().when(val).loadSourceSamples();
        doNothing().when(val).loadLabwareTypes();
        doNothing().when(val).loadComments();

        val.loadEntities();
        verify(val).loadSources();
        verify(val).loadSourceSamples();
        verify(val).loadLabwareTypes();
        verify(val).loadComments();
        assertSame(work, val.getWork());
        assertSame(opType, val.getOpType());
        assertSame(bs, val.getBioState());
        assertSame(medium, val.getMedium());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadSources(boolean ok) {
        LabwareValidator lwVal = mock(LabwareValidator.class);
        BioState bs = EntityFactory.getBioState();
        when(mockLwValFactory.getValidator()).thenReturn(lwVal);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);
        List<TissueBlockContent> cons = Stream.of(ok ? lws[0].getBarcode().toLowerCase() : null, lws[0].getBarcode(), lws[1].getBarcode())
                .map(s -> {
                    TissueBlockContent c = new TissueBlockContent();
                    c.setSourceBarcode(s);
                    return c;
                }).toList();
        List<TissueBlockLabware> tbls = cons.stream().map(con -> {
            TissueBlockLabware tbl = new TissueBlockLabware();
            tbl.setContents(List.of(con));
            return tbl;
        }).toList();
        TissueBlockRequest request = new TissueBlockRequest();
        request.setLabware(tbls);
        when(lwVal.getLabware()).thenReturn(Arrays.asList(lws));
        if (!ok) {
            when(lwVal.getErrors()).thenReturn(List.of("Bad labware"));
        }

        BlockValidatorImp val = makeVal(request);
        List<BlockLabwareData> lds = tbls.stream().map(BlockLabwareData::new).toList();
        val.setLwData(lds);
        val.setProblems(new LinkedHashSet<>());
        val.setBioState(bs);
        val.loadSources();
        assertSame(ok ? lws[0] : null, lds.get(0).getBlocks().getFirst().getSourceLabware());
        assertSame(lws[0], lds.get(1).getBlocks().getFirst().getSourceLabware());
        assertSame(lws[1], lds.get(2).getBlocks().getFirst().getSourceLabware());
        if (ok) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            assertThat(val.getProblems()).containsExactlyInAnyOrder("Bad labware", "Source barcode missing.");
        }

        verify(lwVal).loadLabware(mockLwRepo, Set.of(lws[0].getBarcode(), lws[1].getBarcode()));
        verify(lwVal, never()).setSingleSample(true);
        verify(lwVal).validateSources();
        verify(lwVal).validateBioState(bs);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadSourceSamples(boolean ok) {
        Sample[] samples = EntityFactory.makeSamples(2);
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples);
        List<TissueBlockContent> cons = List.of(new TissueBlockContent(), new TissueBlockContent());
        Integer badSampleId;
        if (ok) {
            badSampleId = null;
            Zip.of(cons.stream(), Arrays.stream(samples)).forEach((con, sam) -> con.setSourceSampleId(sam.getId()));
        } else {
            badSampleId = samples[1].getId()+1;
            cons.get(0).setSourceSampleId(badSampleId);
        }
        TissueBlockLabware tbl = new TissueBlockLabware();
        tbl.setContents(cons);
        TissueBlockRequest request = new TissueBlockRequest();
        request.setLabware(List.of(tbl));
        BlockLabwareData ld = new BlockLabwareData(tbl);
        ld.getBlocks().forEach(bl -> bl.setSourceLabware(lw1));
        BlockValidatorImp val = makeVal(request);
        val.setLwData(List.of(ld));
        val.setProblems(new LinkedHashSet<>());

        val.loadSourceSamples();
        assertThat(val.getLwData()).containsExactly(ld);
        if (ok) {
            Zip.of(ld.getBlocks().stream(), Arrays.stream(samples)).forEach((bl, sam) -> assertSame(sam, bl.getSourceSample()));
            assertThat(val.getProblems()).isEmpty();
        } else {
            ld.getBlocks().forEach(bl -> assertNull(bl.getSourceSample()));
            assertThat(val.getProblems()).containsExactlyInAnyOrder("Source sample ID missing from block request.",
                    "Sample id "+badSampleId+" not present in labware "+lw1.getBarcode()+".");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadLabwareTypes(boolean ok) {
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1,"Alpha");
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1, "Beta");
        List<TissueBlockLabware> tbls = Stream.of("Alpha", "alpha", "beta", "BETA")
                .map(ln -> {
                    TissueBlockLabware tbl = new TissueBlockLabware();
                    tbl.setLabwareType(ln);
                    return tbl;
                }).toList();
        if (!ok) {
            tbls.get(1).setLabwareType("Gamma");
            tbls.get(3).setLabwareType(null);
        }
        List<BlockLabwareData> bls = tbls.stream().map(BlockLabwareData::new).toList();
        when(mockLtRepo.findAllByNameIn(any())).thenReturn(List.of(lt1, lt2));
        TissueBlockRequest request = new TissueBlockRequest(tbls);
        BlockValidatorImp val = makeVal(request);
        val.setProblems(new LinkedHashSet<>());
        val.setLwData(bls);
        val.loadLabwareTypes();
        assertThat(val.getLwData()).containsExactlyElementsOf(bls);
        assertSame(lt1, bls.get(0).getLwType());
        assertSame(lt2, bls.get(2).getLwType());
        if (ok) {
            verify(mockLtRepo).findAllByNameIn(Set.of("ALPHA", "BETA"));
            assertSame(lt1, bls.get(1).getLwType());
            assertSame(lt2, bls.get(3).getLwType());
            assertThat(val.getProblems()).isEmpty();
        } else {
            verify(mockLtRepo).findAllByNameIn(Set.of("ALPHA", "BETA", "GAMMA"));
            assertNull(bls.get(1).getLwType());
            assertNull(bls.get(3).getLwType());
            assertThat(val.getProblems()).containsExactlyInAnyOrder(
                    "Missing labware type.",
                    "Unknown labware types: [\"Gamma\"]");
        }
    }

    @Test
    void testLoadComments() {
        List<Comment> comments = List.of(new Comment(1, "com1", "cat"), new Comment(2, "com2", "cat"));
        List<TissueBlockContent> cons = comments.stream().map(comment -> {
            TissueBlockContent con = new TissueBlockContent();
            con.setCommentId(comment.getId());
            return con;
        }).toList();
        TissueBlockLabware tbl = new TissueBlockLabware();
        tbl.setContents(cons);
        BlockLabwareData bld = new BlockLabwareData(tbl);
        TissueBlockRequest request = new TissueBlockRequest(List.of(tbl));
        when(mockCommentValidationService.validateCommentIds(any(), any())).thenReturn(comments);

        BlockValidatorImp val = makeVal(request);
        val.setLwData(List.of(bld));
        List<String> problems = new ArrayList<>();
        val.setProblems(problems);
        val.loadComments();
        ArgumentCaptor<Stream<Integer>> commentIdStreamCaptor = Matchers.streamCaptor();
        verify(mockCommentValidationService).validateCommentIds(same(problems), commentIdStreamCaptor.capture());
        assertThat(commentIdStreamCaptor.getValue()).containsExactly(1,2);
        assertThat(problems).isEmpty();
        Zip.of(comments.stream(), bld.getBlocks().stream())
                .forEach((com, bl) -> assertSame(com, bl.getComment()));
    }

    private static TissueBlockContent contentForAddresses(Address... addresses) {
        TissueBlockContent con = new TissueBlockContent();
        con.setAddresses(Arrays.asList(addresses));
        return con;
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCheckDestAddresses(boolean ok) {
        LabwareType lt1 = EntityFactory.getTubeType();
        LabwareType lt2 = EntityFactory.makeLabwareType(1, 2, "lt2");
        Address A1 = new Address(1, 1), A2 = new Address(1,2);
        TissueBlockLabware tbl1 = new TissueBlockLabware();
        TissueBlockLabware tbl2 = new TissueBlockLabware();
        tbl1.setLabwareType(lt1.getName());
        tbl2.setLabwareType(lt2.getName());
        if (ok) {
            tbl1.setContents(List.of(contentForAddresses(A1)));
            tbl2.setContents(List.of(contentForAddresses(A1, A2)));
        } else {
            tbl1.setContents(List.of(contentForAddresses(A1), contentForAddresses(A1, A2)));
            tbl2.setContents(List.of(contentForAddresses(A1), new TissueBlockContent()));
        }
        TissueBlockRequest request = new TissueBlockRequest(List.of(tbl1, tbl2));

        List<BlockLabwareData> blds = request.getLabware().stream().map(BlockLabwareData::new).toList();
        blds.get(0).setLwType(lt1);
        blds.get(1).setLwType(lt2);
        BlockValidatorImp val = makeVal(request);
        val.setLwData(blds);
        val.setProblems(new LinkedHashSet<>());
        val.checkDestAddresses();
        if (ok) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            assertThat(val.getProblems()).containsExactlyInAnyOrder(
                    "Slot address A2 not valid in labware type "+lt1.getName()+".",
                    "Destination slot addresses missing from block request."
            );
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCheckPrebarcodes(boolean ok) {
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1, "lt1");
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1, "lt2");
        lt1.setPrebarcoded(false);
        lt2.setPrebarcoded(true);
        List<TissueBlockLabware> tbls = IntStream.range(0,5)
                .mapToObj(i -> new TissueBlockLabware())
                .toList();
        if (ok) {
            IntStream.range(1, tbls.size()).forEach(i -> tbls.get(i).setPreBarcode("preb"+i));
        } else {
            tbls.get(0).setPreBarcode("preb1");
            IntStream.range(2, tbls.size()).forEach(i -> tbls.get(i).setPreBarcode("preb"+i));
            tbls.get(2).setPreBarcode("wut!");
            tbls.get(4).setPreBarcode("preb3");
        }
        TissueBlockRequest request = new TissueBlockRequest(tbls);

        List<BlockLabwareData> blds = request.getLabware().stream().map(BlockLabwareData::new).toList();
        BlockValidatorImp val = makeVal(request);
        Zip.enumerate(blds.stream()).forEach((i, bld) -> bld.setLwType(i==0 ? lt1 : lt2));
        val.setLwData(blds);
        val.setProblems(new LinkedHashSet<>());
        if (!ok) {
            when(mockPrebarcodeValidator.validate(any(), any())).then(invocation -> {
                Consumer<String> problemConsumer = invocation.getArgument(1);
                String barcode = invocation.getArgument(0);
                if (barcode.indexOf('!')>=0) {
                    problemConsumer.accept("Bad barcode: " + barcode);
                    return false;
                }
                return true;
            });
            Labware lw1 = EntityFactory.makeEmptyLabware(lt1);
            lw1.setBarcode("preb3");
            when(mockLwRepo.findByBarcodeIn(any())).thenReturn(List.of(lw1));
        }
        val.checkPrebarcodes();
        if (ok) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            assertThat(val.getProblems()).containsExactlyInAnyOrder(
                    "A barcode is not expected for labware type ["+lt1.getName()+"].",
                    "A barcode is required for labware type ["+lt2.getName()+"].",
                    "Bad barcode: wut!",
                    "Barcode already in use: [preb3]",
                    "Barcode specified multiple times: [PREB3]"
            );
        }
        verify(mockPrebarcodeValidator, times(4)).validate(any(), any());
        verify(mockLwRepo).findByBarcodeIn(ok ? Set.of("PREB1", "PREB2", "PREB3", "PREB4")
                : Set.of("WUT!", "PREB1", "PREB3"));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCheckReplicates(boolean ok) {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        BioState bs = EntityFactory.getBioState();
        Tissue tis1 = EntityFactory.makeTissue(donor, sl);
        tis1.setReplicate("REP");
        Tissue tis2 = EntityFactory.makeTissue(donor, sl);
        tis2.setReplicate(null);
        Sample sam1 = new Sample(1, null, tis1, bs);
        Sample sam2 = new Sample(2, null, tis2, bs);
        List<BlockData> bds;
        if (ok) {
            bds = Zip.of(Stream.of(sam1, sam2), Stream.of("REP", "REP1"))
                    .map((sam, rep) -> {
                        BlockData bd = new BlockData(new TissueBlockContent());
                        bd.setSourceSample(sam);
                        bd.getRequestContent().setReplicate(rep);
                        return bd;
                    }).toList();
            when(mockReplicateValidator.validate(any(), any())).thenReturn(true);
        } else {
            bds = Zip.of(Stream.of(sam1, sam2, sam2, sam2, sam2, sam2),
                            Stream.of("REPX", null, "REP1", "REP1", "REP!", "REP2"))
                    .map((sam, rep) -> {
                        BlockData bd = new BlockData(new TissueBlockContent());
                        bd.setSourceSample(sam);
                        bd.getRequestContent().setReplicate(rep);
                        return bd;
                    }).toList();
            when(mockReplicateValidator.validate(any(), any())).thenAnswer(invocation -> {
                Consumer<String> problemConsumer = invocation.getArgument(1);
                String rep = invocation.getArgument(0);
                if (rep.indexOf('!') >= 0) {
                    problemConsumer.accept("Bad replicate: " + rep);
                    return false;
                }
                return true;
            });
            when(mockTissueRepo.findByDonorIdAndSpatialLocationIdAndReplicate(anyInt(), anyInt(), eqCi("REP2")))
                    .thenReturn(List.of(tis2));
        }
        BlockValidatorImp val = makeVal(new TissueBlockRequest());
        BlockLabwareData bld = new BlockLabwareData(new TissueBlockLabware());
        bld.setBlocks(bds);
        val.setLwData(List.of(bld));
        val.setProblems(new LinkedHashSet<>());
        val.checkReplicates();
        if (ok) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            String repdesc = String.format("{Donor: %s, Tissue type: %s, Spatial location: %s, Replicate: %%s}",
                    donor.getDonorName(), tis1.getTissueType().getName(), sl.getCode());
            assertThat(val.getProblems()).containsExactlyInAnyOrder(
                    "Bad replicate: REP!",
                    "Missing replicate for some blocks.",
                    "Replicate numbers must match the source replicate number where present.",
                    "Same replicate specified multiple times: ["+String.format(repdesc, "rep1")+"]",
                    "Replicate already exists in the database: ["+String.format(repdesc, "rep2")+"]"
            );
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCheckDiscardBarcodes(boolean ok) {
        List<TissueBlockContent> cons = Stream.of("STAN-1", "STAN-2")
                .map(bc -> {
                    TissueBlockContent con = new TissueBlockContent();
                    con.setSourceBarcode(bc);
                    return con;
                }).toList();
        TissueBlockLabware tbl = new TissueBlockLabware();
        tbl.setContents(cons);
        TissueBlockRequest request = new TissueBlockRequest(List.of(tbl));
        request.setDiscardSourceBarcodes(ok ? List.of("STAN-1") : List.of("STAN-1", "STAN-3", ""));
        BlockValidatorImp val = makeVal(request);
        val.setProblems(new LinkedHashSet<>());
        val.checkDiscardBarcodes();
        if (ok) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            assertThat(val.getProblems()).containsExactlyInAnyOrder(
                    "A null or empty string was supplied as a barcode to discard.",
                    "The given list of barcodes to discard includes a barcode that is not specified as a source barcode in this request: [\"STAN-3\"]"
            );
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRaiseError(boolean ok) {
        BlockValidatorImp val = makeVal(new TissueBlockRequest());
        List<String> problems = ok ? List.of() : List.of("Problem 1", "Problem 2");
        val.setProblems(problems);
        if (ok) {
            val.raiseError();
        } else {
            assertValidationException(val::raiseError, problems);
        }
    }
}