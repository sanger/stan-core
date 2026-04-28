package uk.ac.sanger.sccp.stan.service.imagedatafile;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.imagedatafile.ImageDataFileServiceImp.OpIdSampleId;
import uk.ac.sanger.sccp.utils.Zip;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ImageDataFileServiceImp}
 * @author dr6
 */
public class TestImageDataFileService {
    @Mock
    WorkRepo mockWorkRepo;
    @Mock
    OperationCommentRepo mockOpComRepo;
    @Mock
    LabwareRepo mockLwRepo;

    @InjectMocks
    ImageDataFileServiceImp service;

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

    private Operation opWithId(int id) {
        Operation op = new Operation();
        op.setId(id);
        return op;
    }

    private List<Operation> opsWithId(int... ids) {
        return IntStream.of(ids).mapToObj(this::opWithId).toList();
    }

    @Test
    void testGenerateFile() {
        List<Operation> ops = opsWithId(10,11,12);
        String filename = "returnedFilename";
        doReturn(filename).when(service).getFilename(any());
        List<ImageDataColumn> columns = List.of(ImageDataColumn.filename, ImageDataColumn.omero_name);
        doReturn(columns).when(service).getColumns();
        List<ImageDataRow> rows = List.of(new ImageDataRow("bc1", "xn1", "op1", "SGP1", "user1", "com1"));
        doReturn(rows).when(service).getRows(any());

        TsvFile<?> tsvFile = service.generateFile(ops);
        verify(service).getFilename(ops);
        verify(service).getColumns();
        verify(service).getRows(ops);
        assertEquals(filename, tsvFile.getFilename());
        assertEquals(columns, tsvFile.getColumns());
        assertEquals(rows.size(), tsvFile.getNumRows());
    }

    @Test
    void testGetFilename() {
        List<Operation> ops = opsWithId(11,10,12);
        assertEquals("imaging-qc-11-10-12.xlsx", service.getFilename(ops));
    }


    @Test
    void testGetColumns() {
        assertThat(service.getColumns()).containsExactly(ImageDataColumn.values());
    }

    @Test
    void testCompileCommentMap() {
        List<Comment> comments = IntStream.of(20,21)
                .mapToObj(i -> new Comment(i, "com"+i, "cat"))
                .toList();
        List<Integer> opIds = List.of(10,11);
        List<OperationComment> opcoms = List.of(
                new OperationComment(1, comments.get(0), 10, 30, null, null),
                new OperationComment(2, comments.get(1), 10, 30, null, null),
                new OperationComment(3, comments.get(0), 10, 31, null, null),
                new OperationComment(4, comments.get(0), 11, 30, null, null)
        );
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(opcoms);
        Map<OpIdSampleId, List<Comment>> commentMap = service.compileCommentMap(opIds);
        verify(mockOpComRepo).findAllByOperationIdIn(opIds);
        assertThat(commentMap).hasSize(3);
        assertThat(commentMap.get(new OpIdSampleId(10, 30))).containsExactlyInAnyOrderElementsOf(comments);
        assertThat(commentMap.get(new OpIdSampleId(10, 31))).containsExactly(comments.getFirst());
        assertThat(commentMap.get(new OpIdSampleId(11, 30))).containsExactly(comments.getFirst());
    }


    @Test
    void testCompileLabwareMap() {
        Sample sample = EntityFactory.getSample();
        final LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware[] lws = {
                EntityFactory.makeLabware(lt, sample, sample),
                EntityFactory.makeLabware(lt, sample, sample)
        };
        Set<Integer> lwIds = Arrays.stream(lws).map(Labware::getId).collect(toSet());
        OperationType opType = EntityFactory.makeOperationType("OpType", null);
        List<Operation> ops = Arrays.stream(lws)
                .map(List::of)
                .map(list -> EntityFactory.makeOpForLabware(opType, list, list))
                .toList();
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(Arrays.asList(lws));

        Map<Integer, Labware> map = service.compileLabwareMap(ops);

        verify(mockLwRepo).findAllByIdIn(lwIds);

        assertThat(map).hasSize(2);
        for (Labware lw : lws) {
            assertSame(lw, map.get(lw.getId()));
        }
    }

    @Test
    void testGetRows() {
        List<Operation> ops = opsWithId(10,11);
        List<Work> works = Stream.of("SGP1", "SGP2").map(EntityFactory::makeWork).toList();
        works.get(0).setOmeroProject(new OmeroProject(80, "om80"));
        works.get(1).setOmeroProject(new OmeroProject(81, "om81"));
        Map<Integer, List<Work>> opWorks = Map.of(10, works, 11, List.of(works.getFirst()));
        doReturn(opWorks).when(service).loadOpWorks(any());
        Map<OpIdSampleId, List<Comment>> opSampleComments = Map.of(
                new OpIdSampleId(10, 20), List.of(new Comment(1, "com1", "cat")),
                new OpIdSampleId(11, 21), List.of(new Comment(2, "com2", "cat"))
        );
        doReturn(opSampleComments).when(service).compileCommentMap(any());
        Map<Integer, Labware> lwMap = Map.of(40, EntityFactory.getTube());
        doReturn(lwMap).when(service).compileLabwareMap(any());
        List<ImageDataRow> rows = List.of(new ImageDataRow("BC", "XN", "OM", "WN", "UN", "com"));
        doReturn(rows).when(service).compileAllRows(anyCollection(), anyMap(), anyMap(), anyMap(), anyMap());

        assertSame(rows, service.getRows(ops));
        final List<Integer> opIds = List.of(10, 11);
        verify(service).loadOpWorks(opIds);
        verify(service).compileCommentMap(opIds);
        verify(service).compileLabwareMap(ops);
        Map<Integer, String> opOmero = Map.of(10, "om80, om81", 11, "om80");
        Map<Integer, String> opWorkNumber = Map.of(10, "SGP1, SGP2", 11, "SGP1");
        verify(service).compileAllRows(ops, opOmero, opWorkNumber, lwMap, opSampleComments);
    }

    @Test
    void testCompileRows() {
        Operation op = new Operation();
        op.setId(1);
        String omeroProject = "omero1";
        String workNumber = "SGP1";
        String username = "user1";
        Map<Integer, Labware> lwMap = Map.of(1, EntityFactory.getTube());
        Map<OpIdSampleId, List<Comment>> commentMap = Map.of(new OpIdSampleId(1, 2), List.of(new Comment(1, "Alpha", "a")));

        Sample[] samples = EntityFactory.makeSamples(2);
        List<Action> actions = List.of(new Action(), new Action(), new Action());
        actions.get(0).setId(10);
        actions.get(1).setId(11);
        actions.get(2).setId(12);
        Zip.of(Stream.of(samples[0], samples[1], samples[1]), actions.stream()).forEach((sam, ac) -> ac.setSample(sam));
        op.setActions(actions);

        ImageDataRow row = new ImageDataRow("bc1", "xn1", "op1", "SGP1", "user1", "com1");
        doReturn(row).when(service).makeRow(any(), any(), any(), any(), any(), any());

        List<ImageDataRow> rows = service.compileRows(op, omeroProject, workNumber, username, lwMap, commentMap);

        verify(service, times(2)).makeRow(any(), any(), any(), any(), any(), any());
        actions.subList(0,2).forEach(ac -> verify(service).makeRow(same(ac), eq(omeroProject), eq(workNumber), eq(username), same(lwMap), same(commentMap)));

        assertThat(rows).containsExactly(row);
    }

    @Test
    void testCompileAllRows() {
        List<Operation> ops = opsWithId(10, 11);
        List<User> users = IntStream.of(1,2).mapToObj(i -> new User(i, "user"+i, null)).toList();
        Zip.of(ops.stream(), users.stream()).forEach(Operation::setUser);
        Map<Integer, String> opOmero = Map.of(10, "om10");
        Map<Integer, String> opWork = Map.of(10, "SGP10");
        Map<Integer, Labware> lwMap = Map.of(50, EntityFactory.getTube());
        Map<OpIdSampleId, List<Comment>> opSampleComments = Map.of(
                new OpIdSampleId(10, 20), List.of(new Comment(1, "Alpha", "a"))
        );
        List<ImageDataRow> rows = List.of(
                new ImageDataRow("BC1", "XN1", "OM1", "SGP1", "UN", "com1"),
                new ImageDataRow("BC1", "XN2", "OM1", "SGP2", "UN", "com2"),
                new ImageDataRow("BC2", "XN1", null, null, "UN", "com1")
        );
        doReturn(rows.subList(0,2)).when(service).compileRows(same(ops.getFirst()), any(), any(), any(), any(), any());
        doReturn(rows.subList(2,3)).when(service).compileRows(same(ops.getLast()), any(), any(), any(), any(), any());

        assertEquals(rows, service.compileAllRows(ops, opOmero, opWork, lwMap, opSampleComments));
        verify(service).compileRows(ops.getFirst(), "om10", "SGP10", "user1", lwMap, opSampleComments);
        verify(service).compileRows(ops.get(1), null, null, "user2", lwMap, opSampleComments);
    }

    @Test
    void testMakeRow() {
        Action ac = new Action();
        ac.setOperationId(400);
        Labware lw = EntityFactory.getTube();
        Slot slot = lw.getFirstSlot();
        Sample sample = slot.getSamples().getFirst();
        ac.setDestination(slot);
        ac.setSample(sample);
        Map<Integer, Labware> lwMap = Map.of(lw.getId(), lw);
        String workNumber = "SGP1";
        String username = "user1";
        String omeroProject = "omero1";
        Map<OpIdSampleId, List<Comment>> commentMap = Map.of(new OpIdSampleId(400, sample.getId()),
                List.of(new Comment(1, "Alpha", "a"),
                        new Comment(2, "Beta.", "a")));
        ImageDataRow row = service.makeRow(ac, omeroProject, workNumber, username, lwMap, commentMap);
        assertEquals(lw.getBarcode(), row.getBarcode());
        assertEquals(sample.getTissue().getExternalName(), row.getExternalName());
        assertEquals(workNumber, row.getWorkNumber());
        assertEquals(omeroProject, row.getOmeroProject());
        assertEquals(username, row.getUserName());
        assertEquals("Alpha. Beta.", row.getComments());
    }

    @Test
    void testLoadOpWorks() {
        List<Integer> opIds = List.of(10,11,12);
        List<Work> works = Arrays.stream(EntityFactory.makeWorks("SGP1", "SGP2", "SGP3")).toList();
        List<Integer> workIds = works.stream().map(Work::getId).toList();
        when(mockWorkRepo.findWorkIdsForOperationId(10)).thenReturn(workIds.subList(0,2));
        when(mockWorkRepo.findWorkIdsForOperationId(11)).thenReturn(workIds.subList(1,3));
        when(mockWorkRepo.findWorkIdsForOperationId(12)).thenReturn(List.of());
        when(mockWorkRepo.findAllById(any())).thenReturn(works);

        Map<Integer, List<Work>> opWorks = service.loadOpWorks(opIds);

        opIds.forEach(id -> verify(mockWorkRepo).findWorkIdsForOperationId(id));
        verify(mockWorkRepo).findAllById(new HashSet<>(workIds));
        assertThat(opWorks.keySet()).containsExactlyInAnyOrderElementsOf(opIds);
        assertThat(opWorks.get(10)).containsExactlyInAnyOrderElementsOf(works.subList(0,2));
        assertThat(opWorks.get(11)).containsExactlyInAnyOrderElementsOf(works.subList(1,3));
        assertThat(opWorks.get(12)).isEmpty();
    }
}
