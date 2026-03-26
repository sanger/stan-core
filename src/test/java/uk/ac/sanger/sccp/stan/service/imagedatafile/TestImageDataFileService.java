package uk.ac.sanger.sccp.stan.service.imagedatafile;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.Zip;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** Test {@link ImageDataFileServiceImp} */
class TestImageDataFileService {
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

    @Test
    void testGenerateFile() {
        Operation op = new Operation();
        op.setId(10);
        String filename = "filename.xlsx";
        doReturn(filename).when(service).getFilename(op);
        List<ImageDataColumn> columns = List.of(ImageDataColumn.filename, ImageDataColumn.omero_name);
        doReturn(columns).when(service).getColumns();
        List<ImageDataRow> rows = List.of(new ImageDataRow("bc1", "xn1", "op1", "SGP1", "user1", "com1"));
        doReturn(rows).when(service).getRows(op);

        TsvFile<?> tsvFile = service.generateFile(op);

        verify(service).getFilename(op);
        verify(service).getColumns();
        verify(service).getRows(op);

        assertEquals(filename, tsvFile.getFilename());
        assertEquals(columns, tsvFile.getColumns());
        assertEquals(rows.size(), tsvFile.getNumRows());
    }

    @Test
    void testGetFilename() {
        Operation op = new Operation();
        op.setId(17);
        assertEquals("imaging-qc-17.xlsx", service.getFilename(op));
    }

    @Test
    void testGetColumns() {
        assertThat(service.getColumns()).containsExactly(ImageDataColumn.values());
    }

    @Test
    void testCompileCommentMap() {
        Comment com1 = new Comment(1, "Alpha", "a");
        Comment com2 = new Comment(2, "Beta", "a");
        Operation op = new Operation();
        op.setId(10);
        List<OperationComment> opcoms = List.of(
                new OperationComment(1, com2, 10, 50, 60, null),
                new OperationComment(2, com1, 10, 50, 61, null),
                new OperationComment(3, com1, 10, 51, 61, null)
        );
        when(mockOpComRepo.findAllByOperationIdIn(any())).thenReturn(opcoms);

        Map<Integer, List<Comment>> map = service.compileCommentMap(op.getId());

        verify(mockOpComRepo).findAllByOperationIdIn(List.of(op.getId()));

        assertThat(map).hasSize(2);
        assertThat(map.get(50)).containsExactly(com1, com2);
        assertThat(map.get(51)).containsExactly(com1);
    }

    @Test
    void testCompileLabwareMap() {
        Sample sample = EntityFactory.getSample();
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1, 2), sample, sample);
        List<Labware> lws = List.of(lw);
        OperationType opType = EntityFactory.makeOperationType("OpType", null);
        Operation op = EntityFactory.makeOpForLabware(opType, lws, lws);
        when(mockLwRepo.findAllByIdIn(any())).thenReturn(lws);

        Map<Integer, Labware> map = service.compileLabwareMap(op);

        verify(mockLwRepo).findAllByIdIn(Set.of(lw.getId()));

        assertThat(map).hasSize(1);
        assertSame(lw, map.get(lw.getId()));
    }

    @Test
    void testGetRows() {
        Operation op = new Operation();
        op.setId(10);
        User user = EntityFactory.getUser();
        op.setUser(user);
        Work work = EntityFactory.makeWork("SGP1");
        work.setOmeroProject(new OmeroProject(1, "omero1"));
        when(mockWorkRepo.findWorksForOperationId(any())).thenReturn(List.of(work));
        Map<Integer, List<Comment>> commentMap = Map.of(2, List.of(new Comment(1, "Alpha", "a")));
        Map<Integer, Labware> lwMap = Map.of(1, EntityFactory.getTube());
        doReturn(commentMap).when(service).compileCommentMap(any());
        doReturn(lwMap).when(service).compileLabwareMap(any());

        List<ImageDataRow> rows = List.of(new ImageDataRow("bc1", "xn1", "op1", "SGP1", "user1", "com1"));
        doReturn(rows).when(service).compileRows(any(), any(), any(), any(), any(), any());

        assertSame(rows, service.getRows(op));

        verify(mockWorkRepo).findWorksForOperationId(op.getId());
        verify(service).compileCommentMap(op.getId());
        verify(service).compileLabwareMap(op);
        verify(service).compileRows(op, "omero1", "SGP1", user.getUsername(), lwMap, commentMap);
    }

    @Test
    void testCompileRows() {
        Operation op = new Operation();
        op.setId(1);
        String omeroProject = "omero1";
        String workNumber = "SGP1";
        String username = "user1";
        Map<Integer, Labware> lwMap = Map.of(1, EntityFactory.getTube());
        Map<Integer, List<Comment>> commentMap = Map.of(2, List.of(new Comment(1, "Alpha", "a")));

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
    void testMakeRow() {
        Action ac = new Action();
        Labware lw = EntityFactory.getTube();
        Slot slot = lw.getFirstSlot();
        Sample sample = slot.getSamples().getFirst();
        ac.setDestination(slot);
        ac.setSample(sample);
        Map<Integer, Labware> lwMap = Map.of(lw.getId(), lw);
        String workNumber = "SGP1";
        String username = "user1";
        String omeroProject = "omero1";
        Map<Integer, List<Comment>> commentMap = Map.of(sample.getId(),
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
}