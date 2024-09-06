package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareNoteRepo;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;

/** Test {@link LabwareNoteServiceImp} */
class TestLabwareNoteService {
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    LabwareNoteRepo mockLwNoteRepo;

    @InjectMocks
    LabwareNoteServiceImp service;

    AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testFindNoteValuesForBarcode() {
        Labware lw = EntityFactory.getTube();
        String bc = lw.getBarcode();
        String name = "Bananas";
        List<LabwareNote> notes = List.of(
                new LabwareNote(1, lw.getId(), 11, name, "val1"),
                new LabwareNote(2, lw.getId(), 12, name, "val2"),
                new LabwareNote(3, lw.getId(), 13, name, "val1")
        );
        when(mockLwRepo.getByBarcode(bc)).thenReturn(lw);
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), name)).thenReturn(notes);

        assertThat(service.findNoteValuesForBarcode(bc, name)).containsExactly("val1", "val2");
    }

    @Test
    void testFindNoteValuesForLabware_single() {
        Labware lw = EntityFactory.getTube();
        String name = "Bananas";
        List<LabwareNote> notes = List.of(
                new LabwareNote(1, lw.getId(), 11, name, "val1"),
                new LabwareNote(2, lw.getId(), 12, name, "val2"),
                new LabwareNote(3, lw.getId(), 13, name, "val1")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), name)).thenReturn(notes);
        assertThat(service.findNoteValuesForLabware(lw, name)).containsExactly("val1", "val2");
    }

    @Test
    void testFindNoteValueForLabware_none() {
        assertThat(service.findNoteValuesForLabware(List.of(), "Bananas")).isEmpty();
        verifyNoInteractions(mockLwNoteRepo);
    }

    @Test
    void testFindNoteValuesForLabware() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toList();
        Labware lw0 = lws.get(0);
        Labware lw1 = lws.get(1);
        Labware lw2 = lws.get(2);
        Set<Integer> lwIds = lws.stream().map(Labware::getId).collect(toSet());
        String name = "Bananas";

        List<LabwareNote> notes = List.of(
                new LabwareNote(1, lw0.getId(), 11, name, "val1"),
                new LabwareNote(2, lw0.getId(), 12, name, "val2"),
                new LabwareNote(3, lw0.getId(), 13, name, "val1"),
                new LabwareNote(4, lw1.getId(), 14, name, "val1")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(lwIds, name)).thenReturn(notes);

        var map = service.findNoteValuesForLabware(lws, name);
        assertThat(map.get(lw0.getBarcode())).containsExactlyInAnyOrder("val1", "val2");
        assertThat(map.get(lw1.getBarcode())).containsExactly("val1");
        assertThat(map.get(lw2.getBarcode())).isNullOrEmpty();
    }

    @Test
    void testCreateNotes() {
        String name = "Bananas";
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        Operation[] ops = IntStream.range(10,13).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).toArray(Operation[]::new);
        UCMap<Operation> opMap = new UCMap<>(3);
        IntStream.range(0, lws.length).forEach(i -> opMap.put(lws[i].getBarcode(), ops[i]));
        UCMap<String> valueMap = new UCMap<>(2);
        valueMap.put(lws[0].getBarcode(), "val0");
        valueMap.put(lws[1].getBarcode(), "val1");

        service.createNotes(name, lwMap, opMap, valueMap);

        verify(mockLwNoteRepo).saveAll(sameElements(IntStream.range(0,2).mapToObj(
                i -> new LabwareNote(null, lws[i].getId(), ops[i].getId(), name, "val"+i)).toList(),
                true
        ));
    }

    @Test
    void testCreateNotes_none() {
        service.createNotes("Bananas", new UCMap<>(), new UCMap<>(), new UCMap<>());
        verifyNoInteractions(mockLwNoteRepo);
    }

    @Test
    void testCreateNote() {
        String name = "Bananas";
        String value = "Alpha";
        Labware lw = EntityFactory.getTube();
        Operation op = new Operation();
        op.setId(10);
        LabwareNote createdNote = mock(LabwareNote.class);
        when(mockLwNoteRepo.save(any())).thenReturn(createdNote);
        assertSame(createdNote, service.createNote(name, lw, op, value));
        verify(mockLwNoteRepo).save(new LabwareNote(null, lw.getId(), op.getId(), name, value));
    }
}
