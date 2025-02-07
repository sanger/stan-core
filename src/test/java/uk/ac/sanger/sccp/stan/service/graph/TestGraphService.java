package uk.ac.sanger.sccp.stan.service.graph;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.GraphSVG;
import uk.ac.sanger.sccp.stan.request.history.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.history.HistoryGraph.Node;
import uk.ac.sanger.sccp.stan.service.graph.GraphServiceImp.NodeData;
import uk.ac.sanger.sccp.stan.service.graph.GraphServiceImp.NodeKey;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link GraphServiceImp} */
class TestGraphService {

    @Mock
    BuchheimLayoutService mockLayoutService;
    @Mock
    GraphRenderService mockRenderService;

    @InjectMocks
    GraphServiceImp service;

    AutoCloseable mocking;

    @BeforeEach
    void setUp() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocking.close();
    }

    @Test
    void testCreateGraph() {
        Tissue tissue = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        List<Sample> samples = IntStream.range(0,2)
                .mapToObj(i -> new Sample(i, null, tissue, bs))
                .toList();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = samples.stream()
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .toList();
        History history = new History(List.of(), samples, labware);
        List<NodeData> nds = List.of(new NodeData(List.of()), new NodeData(List.of()));

        doNothing().when(service).analyseActions(any());
        doReturn(nds).when(service).compileNodeData(any(), any());

        HistoryGraph graph = new HistoryGraph(List.of(), List.of());
        doReturn(graph).when(service).createHistoryGraph(any(), any(), any());

        assertSame(graph, service.createGraph(history));
        Map<Integer, Labware> lwMap = Map.of(labware.get(0).getId(), labware.get(0),
                labware.get(1).getId(), labware.get(1));
        Map<Integer, Sample> sampleMap = Map.of(samples.get(0).getId(), samples.get(0),
                samples.get(1).getId(), samples.get(1));
        verify(service).compileNodeData(history, sampleMap);
        for (int i = 0; i < nds.size(); i++) {
            NodeData nd = nds.get(i);
            verify(service).analyseActions(nd);
            assertEquals(i, nd.nodeId);
        }
        verify(service).createHistoryGraph(nds, lwMap, sampleMap);
    }

    @Test
    void testRender() {
        HistoryGraph graph = mock(HistoryGraph.class);
        float zoom = 1.5f;
        Integer fontSize = 21;
        GraphSVG graphSVG = mock(GraphSVG.class);
        when(mockRenderService.toSVG(graph, zoom, fontSize)).thenReturn(graphSVG);
        assertSame(graphSVG, service.render(graph, zoom, fontSize));
        verify(mockRenderService).toSVG(graph, zoom, fontSize);
    }

    @Test
    void testCreateHistoryGraph() {
        List<Link> links = List.of(new Link(1,2));
        List<Node> nodes = List.of(new Node(1, null, null, null, null, null, null));

        List<NodeData> nds = List.of(new NodeData(List.of()));
        Map<Integer, Labware> lwMap = Map.of(1, EntityFactory.getTube());
        Map<Integer, Sample> sampleMap = Map.of(2, EntityFactory.getSample());

        doReturn(links).when(service).createLinks(any());
        doReturn(nodes).when(service).createNodes(any(), any(), any());

        HistoryGraph graph = service.createHistoryGraph(nds, lwMap, sampleMap);
        assertSame(nodes, graph.getNodes());
        assertSame(links, graph.getLinks());

        verify(service).createLinks(nds);
        verify(service).createNodes(nds, lwMap, sampleMap);
        verify(mockLayoutService).layout(nodes, links);
    }

    @Test
    void testCreateNodes() {
        Map<Integer, Labware> lwMap = Map.of(1, EntityFactory.getTube());
        Map<Integer, Sample> sampleMap = Map.of(2, EntityFactory.getSample());
        List<NodeData> nds = List.of(new NodeData(List.of()), new NodeData(List.of()));
        List<Node> nodes = List.of(nodeWithId(1), nodeWithId(2));
        for (int i = 0; i < nds.size(); ++i) {
            doReturn(nodes.get(i)).when(service).createNode(same(nds.get(i)), same(lwMap), same(sampleMap));
        }
        assertEquals(nodes, service.createNodes(nds, lwMap, sampleMap));
    }

    @Test
    void testCreateNode() {
        final Labware lw = EntityFactory.getTube();
        Map<Integer, Labware> lwMap = Map.of(lw.getId(), lw);
        Tissue tis = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        List<Sample> samples = IntStream.rangeClosed(1,2).mapToObj(i -> new Sample(10+i, i, tis, bs)).toList();
        Map<Integer, Sample> sampleMap = samples.stream().collect(inMap(Sample::getId));
        Sample sample = samples.getFirst();
        HistoryEntry entry = new HistoryEntry(10, "eventname", LocalDateTime.of(2024,3,11,12,0), -5, lw.getId(),
                sample.getId(), "user1", "SGP1");
        HistoryEntry entry2 = new HistoryEntry(11, entry.getType(), entry.getTime(), -6, lw.getId(),
                samples.getLast().getId(), "user1", "SGP1");
        NodeData nd = new NodeData(List.of(entry, entry2));
        nd.nodeId = 6;
        String bioDesc = "bio desc";
        doReturn(bioDesc).when(service).describeBio(any());

        Node node = service.createNode(nd, lwMap, sampleMap);
        assertEquals(new Node(nd.nodeId, entry.getTime(), entry.getType(), lw.getBarcode(),
                entry.getUsername(), sample.getTissue().getExternalName(), bioDesc), node);
        verify(service).describeBio(new HashSet<>(samples));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testAnalyseActions_nodeData(boolean isOp) {
        List<HistoryEntry> entries = List.of(new HistoryEntry(), new HistoryEntry());
        entries.get(0).setEventId(10);
        entries.get(1).setEventId(11);
        if (isOp) {
            entries.getFirst().setOperation(mock(Operation.class));
        }
        NodeData nd = new NodeData(entries);
        LabwareType lt = EntityFactory.getTubeType();
        Slot[] slots = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt).getFirstSlot()).toArray(Slot[]::new);
        Tissue tis = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        Sample[] samples = IntStream.range(0,2).mapToObj(i -> new Sample(10+i, null, tis, bs)).toArray(Sample[]::new);
        SlotSample[] ss = IntStream.range(0,4).mapToObj(i -> new SlotSample(slots[i/2], samples[i%2])).toArray(SlotSample[]::new);
        doAnswer(invocation -> {
            HistoryEntry entry = invocation.getArgument(0);
            Set<SlotSample> sourceSs = invocation.getArgument(1);
            Set<SlotSample> destSs = invocation.getArgument(2);
            sourceSs.add(entry==entries.getFirst() ? ss[0] : ss[1]);
            destSs.add(entry==entries.getFirst() ? ss[2] : ss[3]);
            return null;
        }).when(service).analyseActions(any(), any(), any());

        service.analyseActions(nd);
        if (!isOp) {
            assertNull(nd.sourceSs);
            assertNull(nd.destSs);
            verify(service, never()).analyseActions(any(), any(), any());
            return;
        }
        assertNotNull(nd.sourceSs);
        assertNotNull(nd.destSs);
        assertThat(nd.sourceSs).containsExactlyInAnyOrder(ss[0], ss[1]);
        assertThat(nd.destSs).containsExactlyInAnyOrder(ss[2], ss[3]);
        for (HistoryEntry entry : entries) {
            verify(service).analyseActions(entry, nd.sourceSs, nd.destSs);
        }
    }

    @Test
    void testAnalyseActions_historyEntry() {
        Sample sam = EntityFactory.getSample();
        Sample sam2 = new Sample(sam.getId()+1, null, sam.getTissue(), sam.getBioState());
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw0 = EntityFactory.makeLabware(lt, sam);
        Labware lw1 = EntityFactory.makeLabware(lt, sam);
        lw1.getFirstSlot().addSample(sam2);
        Labware lw2 = EntityFactory.makeLabware(lt, sam);
        Slot[] slots = Stream.of(lw0, lw1, lw2).map(Labware::getFirstSlot).toArray(Slot[]::new);
        int opId = 200;
        List<Action> actions = List.of(
                makeAction(201, opId, slots[0], slots[1], sam2),
                makeAction(202, opId, slots[0], slots[1], sam),
                makeAction(203, opId, slots[0], slots[2], sam),
                makeAction(204, opId, slots[0], slots[1], sam)
        );
        Operation op = mock(Operation.class);
        when(op.getActions()).thenReturn(actions);
        HistoryEntry entry = new HistoryEntry();
        entry.setOperation(op);
        entry.setSampleId(sam.getId());
        entry.setSourceLabwareId(lw0.getId());
        entry.setDestinationLabwareId(lw1.getId());
        final Set<SlotSample> sourceSs = new HashSet<>();
        final Set<SlotSample> destSs = new HashSet<>();
        service.analyseActions(entry, sourceSs, destSs);
        assertThat(sourceSs).containsExactly(new SlotSample(slots[0], sam));
        assertThat(destSs).containsExactly(new SlotSample(slots[1], sam));
    }

    @Test
    void testAnalyseActions_noOp() {
        HistoryEntry entry = new HistoryEntry();
        final Set<SlotSample> sourceSs = new HashSet<>();
        final Set<SlotSample> destSs = new HashSet<>();
        service.analyseActions(entry, sourceSs, destSs);
        assertThat(sourceSs).isEmpty();
        assertThat(destSs).isEmpty();
    }

    @Test
    void testCompileNodeData() {
        Sample sample = EntityFactory.getSample();
        Map<Integer, Sample> sampleMap = Map.of(sample.getId(), sample);
        List<HistoryEntry> entries = List.of(
                makeEntryAtTime(1,1),
                makeEntryAtTime(3,3),
                makeEntryAtTime(2,2),
                makeEntryAtTime(1,1)
        );
        History history = new History(entries, List.of(), List.of());
        List<NodeData> nds = service.compileNodeData(history, sampleMap);
        assertThat(nds).hasSize(3);
        assertThat(nds.get(0).entries).containsExactly(entries.get(0), entries.get(3));
        assertThat(nds.get(1).entries).containsExactly(entries.get(2));
        assertThat(nds.get(2).entries).containsExactly(entries.get(1));

        entries.forEach(e -> verify(service).keyForEntry(same(e), same(sampleMap)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            ":",
            "null,null:BS",
            "2,1,null,3,2:BS; s1-3"
    }, delimiter=':')
    void testDescribeBio(String secString, String expected) {
        List<Integer> secs;
        if (secString==null) {
            secs = List.of();
        } else {
            secs = Arrays.stream(secString.split(","))
                    .map(s -> s.equals("null") ? null : Integer.valueOf(s))
                    .toList();
        }
        BioState bs = new BioState(10, "BS");
        Tissue tis = EntityFactory.getTissue();
        List<Sample> samples = IntStream.range(0, secs.size())
                .mapToObj(i -> new Sample(10+i, secs.get(i), tis, bs))
                .toList();
        assertEquals(expected, service.describeBio(samples));
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";",
            "2;2",
            "2 3; 2,3",
            "2 3 4; 2-4",
            "2 3 4 6; 2,3,4,6",
    }, delimiter = ';')
    void testSummariseSections(String secString, String expected) {
        List<Integer> secs;
        if (secString==null) {
            secs = List.of();
        } else {
            secs = Arrays.stream(secString.split("\\s+"))
                    .map(Integer::valueOf)
                    .toList();
        }
        assertEquals(expected, service.summariseSections(secs));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testKeyForEntry(final boolean hasSample) {
        Sample sam = hasSample ? EntityFactory.getSample() : null;
        Integer samId = hasSample ? sam.getId() : null;
        HistoryEntry entry = new HistoryEntry(10, "entryname", null, 15, 16,
                samId, null, null);
        Integer tissueId = (sam==null ? null : sam.getTissue().getId());
        Integer bsId = (sam==null ? null : sam.getBioState().getId());
        Map<Integer, Sample> sampleMap = (hasSample ? Map.of(samId, sam) : Map.of());
        assertEquals(new NodeKey("entryname", 10, 16, tissueId, bsId),
                service.keyForEntry(entry, sampleMap));
    }

    @Test
    void testCreateLinks() {
        LabwareType lt1 = EntityFactory.getTubeType();
        Tissue tis = EntityFactory.getTissue();
        BioState bs = EntityFactory.getBioState();
        Sample sam = new Sample(10, null, tis, bs);
        Labware lw1 = EntityFactory.makeLabware(lt1, sam);
        Set<SlotSample> regSs = Set.of(new SlotSample(lw1.getFirstSlot(), sam));
        NodeData regNd = makeNodeData(1, makeTime(1), lw1.getId(), lw1.getId(), regSs, regSs);

        List<Sample> sections = IntStream.rangeClosed(1,2)
                .mapToObj(i -> new Sample(10+i, i, tis, bs))
                .toList();

        List<Labware> secLw = sections.stream()
                .map(sec -> EntityFactory.makeLabware(lt1, sec))
                .toList();
        LocalDateTime secTime = makeTime(2);
        List<NodeData> secNd = IntStream.range(0, secLw.size())
                .mapToObj(i -> makeNodeData(2+i, secTime, lw1.getId(), secLw.get(i).getId(),
                        regSs, Set.of(new SlotSample(secLw.get(i).getFirstSlot(), sections.get(i)))))
                .toList();

        Sample extractSample = new Sample(20, 2, tis, bs);
        Labware extractLw = EntityFactory.makeLabware(lt1, extractSample);
        NodeData extractNd = makeNodeData(5, makeTime(3), secLw.getLast().getId(), extractLw.getId(),
                Set.of(new SlotSample(secLw.getLast().getFirstSlot(), sections.getLast())),
                Set.of(new SlotSample(extractLw.getFirstSlot(), extractSample)));

        NodeData regRelease = makeNodeData(6, makeTime(4), lw1.getId());

        LabwareType lt2 = EntityFactory.makeLabwareType(1,2);
        Labware plateLw = EntityFactory.makeLabware(lt2, sections.getFirst(), extractSample);
        final Address A2 = new Address(1,2);
        LocalDateTime plateTime = makeTime(5);
        List<HistoryEntry> plateEntries = List.of(
                makeOpEntry(plateTime, secLw.getFirst().getId(), plateLw.getId()),
                makeOpEntry(plateTime, extractLw.getId(), plateLw.getId())
        );
        NodeData plateNd = makeNodeData(7, plateEntries,
                Set.of(new SlotSample(secLw.getFirst().getFirstSlot(), sections.getFirst()),
                        new SlotSample(extractLw.getFirstSlot(), extractSample)),
                Set.of(new SlotSample(plateLw.getFirstSlot(), sections.getFirst()),
                        new SlotSample(plateLw.getSlot(A2), extractSample)));

        NodeData secRelease = makeNodeData(8, makeTime(6), secLw.getFirst().getId());

        List<NodeData> nds = List.of(regNd, secNd.getFirst(), secNd.getLast(), extractNd, regRelease, plateNd, secRelease);
        List<Link> links = service.createLinks(nds);

        assertThat(links).containsExactlyInAnyOrder(
                new Link(1,2), // reg to sec 1
                new Link(1,3), // reg to sec 2
                new Link(3,5), // sec 2 to extract
                new Link(1,6), // reg to release
                new Link(2,7), // sec 1 to plate
                new Link(5,7), // extract to plate
                new Link(2,8)  // sec 1 to release
        );
    }

    static HistoryEntry makeOpEntry(LocalDateTime time, int sourceLwId, int destLwId) {
        HistoryEntry entry = new HistoryEntry();
        entry.setTime(time);
        entry.setSourceLabwareId(sourceLwId);
        entry.setDestinationLabwareId(destLwId);
        entry.setOperation(mock(Operation.class));
        return entry;
    }

    static NodeData makeNodeData(int id, List<HistoryEntry> entries, Set<SlotSample> sourceSs, Set<SlotSample> destSs) {
        NodeData nd = new NodeData(entries);
        nd.nodeId = id;
        nd.sourceSs = sourceSs;
        nd.destSs = destSs;
        return nd;
    }

    static NodeData makeNodeData(int id, LocalDateTime time, int sourceLwId, int destLwId,
                                 Set<SlotSample> sourceSs, Set<SlotSample> destSs) {
        HistoryEntry entry = makeOpEntry(time, sourceLwId, destLwId);
        return makeNodeData(id, List.of(entry), sourceSs, destSs);
    }

    static NodeData makeNodeData(int id, LocalDateTime time, int lwId) {
        HistoryEntry entry = new HistoryEntry();
        entry.setTime(time);
        entry.setSourceLabwareId(lwId);
        entry.setDestinationLabwareId(lwId);
        NodeData nd = new NodeData(List.of(entry));
        nd.nodeId = id;
        return nd;
    }

    static Node nodeWithId(int id) {
        return new Node(id, null, null, null, null, null, null);
    }

    static Action makeAction(int acId, int opId, Slot src, Slot dst, Sample sam) {
        return new Action(acId, opId, src, dst, sam, sam);
    }

    static HistoryEntry makeEntryAtTime(int id, int timeIndex) {
        HistoryEntry entry = new HistoryEntry();
        entry.setTime(makeTime(timeIndex));
        entry.setEventId(id);
        return entry;
    }

    static LocalDateTime makeTime(int timeIndex) {
        return LocalDateTime.of(2024,3,timeIndex, 12, 0);
    }
}