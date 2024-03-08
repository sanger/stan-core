package uk.ac.sanger.sccp.stan.service.graph;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Node;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests {@link BuchheimLayoutServiceImp} */
class TestBuchheimLayoutService {
    @Mock
    BuchheimAlgorithm mockAlgo;
    @Mock
    Assembler mockAssembler;

    @InjectMocks
    BuchheimLayoutServiceImp service;

    AutoCloseable mocking;

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
    void testLayout() {
        doNothing().when(service).setY(any());
        doNothing().when(service).setX(any(), any());
        List<Node> nodes = List.of(new Node(1, null, null, null, null, null,null));
        List<Link> links = List.of(new Link(1,1));
        service.layout(nodes, links);
        InOrder inorder = Mockito.inOrder(service);
        inorder.verify(service).setY(nodes);
        inorder.verify(service).setX(nodes, links);
    }

    @Test
    void testSetY() {
        List<Node> nodes = List.of(
                nodeAtTime(1, 1),
                nodeAtTime(2, 2),
                nodeAtTime(3, 2),
                nodeAtTime(4, 3),
                nodeAtTime(5,3),
                nodeAtTime(6,10)
        );
        service.setY(nodes);
        int[] expectedY = {0,1,1,2,2,3};
        for (int i = 0; i < expectedY.length; ++i) {
            assertEquals(expectedY[i], nodes.get(i).getY());
        }
    }

    @Test
    void testSetX() {
        doNothing().when(service).realign(any());
        List<Node> nodes = IntStream.range(0,4).mapToObj(TestBuchheimLayoutService::node).toList();
        List<Link> links = List.of(new Link(0,1), new Link(2,3));
        Map<Integer, Integer> childParent = Map.of(1,0, 3,2);
        List<BuchheimNode<Node>> nds = new ArrayList<>(5);
        for (int i = 0; i < nodes.size(); ++i) {
            nds.add(new BuchheimNode<>(nodes.get(i), i));
        }
        nds.add(new BuchheimNode<>(null, 4));
        double[] xs = {0.0, 3.1, 4.9, -3.1, 1.0};
        for (int i = 0; i < nds.size(); ++i) {
            if (i != 2 && i!=0) {
                nds.get(i).parent = nds.get(i-1);
            }
            nds.get(i).x = xs[i];
        }

        doReturn(nds).when(service).makeBuchheimNodes(any(), any());
        List<BuchheimNode<Node>> roots = IntStream.of(0,2).mapToObj(nds::get).toList();

        service.setX(nodes, links);
        int[] expectedX = {0, 3, 5, -3};
        for (int i = 0; i < expectedX.length; ++i) {
            assertEquals(expectedX[i], nodes.get(i).getX());
        }

        verify(service).findParents(links);
        verify(service).makeBuchheimNodes(nodes, childParent);
        for (BuchheimNode<Node> root : roots) {
            verify(mockAlgo).run(root);
            verify(service).realign(root);
        }
        verifyNoMoreInteractions(mockAlgo);
        verify(mockAssembler).assemble(roots, 2.0);
    }

    @Test
    void testFindParents() {
        List<Link> links = List.of(new Link(1,2), new Link(1,3), new Link(2,4), new Link(3,4));
        Map<Integer, Integer> map = service.findParents(links);
        assertEquals(Map.of(2,1, 3,1, 4,3), map);
    }

    @Test
    void testMakeBuchheimNodes() {
        List<Node> nodes = IntStream.range(0,4).mapToObj(TestBuchheimLayoutService::node).toList();
        int[] ys = {1,2,2,5};
        for (int i = 0; i < ys.length; ++i) {
            nodes.get(i).setY(ys[i]);
        }
        Map<Integer, Integer> parentMap = Map.of(1,0, 2,0, 3,1);
        Collection<BuchheimNode<Node>> bns = service.makeBuchheimNodes(nodes, parentMap);
        assertThat(bns.stream().map(bn -> bn.data)).containsExactlyInAnyOrderElementsOf(nodes);
        List<BuchheimNode<Node>> bnList = bns.stream().sorted(Comparator.comparingInt(bn -> bn.data.id())).toList();
        assertSame(bnList.get(0), bnList.get(1).parent);
        assertSame(bnList.get(0), bnList.get(2).parent);
        assertSame(bnList.get(1), bnList.get(3).parent.parent.parent);
        bnList.forEach(bn -> {
            if (bn.parent!=null) {
                assertThat(bn.parent.children).contains(bn);
            }
        });
    }

    @Test
    void testRealign() {
        //TODO
    }


    private static Node nodeAtTime(int id, int time) {
        return new Node(id, time(time), null, null, null, null, null);
    }

    private static Node node(int id) {
        return new Node(id, null, null, null, null, null, null);
    }

    private static BuchheimNode<String> bnode(BuchheimNode<String> parent, double x, int y) {
        BuchheimNode<String> bn = new BuchheimNode<>(null, y);
        bn.x = x;
        if (parent!=null) {
            bn.parent = parent;
            parent.children.add(bn);
        }
        return bn;
    }

    private static LocalDateTime time(int n) {
        return LocalDateTime.of(2024,1,1,12,n);
    }
}