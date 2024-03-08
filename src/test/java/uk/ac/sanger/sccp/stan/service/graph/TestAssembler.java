package uk.ac.sanger.sccp.stan.service.graph;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test {@link AssemblerImp} */
class TestAssembler {
    AssemblerImp assembler = new AssemblerImp();

    @Test
    void testAssemble() {
        BuchheimNode<String> root1 = makeTree(0,0, 1,0, 2,0);
        BuchheimNode<String> root2 = makeTree(0,0, 1, -1, 1, 1);
        BuchheimNode<String> root3 = makeTree(0,2, 1,1, 1,3);
        assembler.assemble(List.of(root1, root2, root3), 2.0);
        assertEquals(0, root1.x);
        assertEquals(3, root2.x);
        assertEquals(7, root3.x);
    }

    @Test
    void testFindOverlap() {
        BuchheimNode<String> root = makeTree(0, 0, 1, -2, 1, 3, 2, 6, 2, 7, 3, -3, 3, 5);
        Map<Integer, double[]> leftBounds = new HashMap<>();
        assertEquals(0, assembler.findOverlap(leftBounds, root));
        leftBounds.put(1, new double[] {-10, 0});
        leftBounds.put(3, new double[] {-10, 0});
        assertEquals(3.0, assembler.findOverlap(leftBounds, root));
    }

    @Test
    void testShift() {
        BuchheimNode<String> root = new BuchheimNode<>(null, 0);
        var c1 = child(root, -1);
        var c2 = child(root, 1);
        var d1 = child(root, -1.5);
        var d2 = child(root, -0.5);
        assembler.shift(root, 2.0);
        assertEquals(2, root.x);
        assertEquals(1, c1.x);
        assertEquals(3, c2.x);
        assertEquals(0.5, d1.x);
        assertEquals(1.5, d2.x);
    }

    @Test
    void testAddBounds() {
        List<BuchheimNode<String>> nodes = DoubleStream.of(1, 2, 5, 4, 3, 4, 6, -4).mapToObj(x -> this.node(x, 0)).toList();
        Map<Integer, double[]> bounds = new HashMap<>(1);
        assembler.addBounds(bounds, nodes.getFirst(), 0.5);
        assertArrayEquals(new double[] {1, 1.5}, bounds.get(0));
        nodes.subList(1, 4).forEach(node -> assembler.addBounds(bounds, node, 0.5));
        assertArrayEquals(new double[] {1, 5.5}, bounds.get(0));
        nodes.subList(4, 8).forEach(node -> assembler.addBounds(bounds, node, 0.5));
        assertArrayEquals(new double[] {-4, 6.5}, bounds.get(0));
        assertThat(bounds).hasSize(1);
    }

    private BuchheimNode<String> makeTree(int... coords) {
        BuchheimNode<String> root = new BuchheimNode<>("root", coords[0]);
        root.x = coords[1];
        for (int i = 2; i < coords.length; i += 2) {
            BuchheimNode<String> child = new BuchheimNode<>(null, coords[i]);
            child.x = coords[i+1];
            root.children.add(child);
            child.parent = root;
        }
        return root;
    }

    private BuchheimNode<String> node(double x, int y) {
        BuchheimNode<String> node = new BuchheimNode<>(null, y);
        node.x = x;
        return node;
    }

    private BuchheimNode<String> child(BuchheimNode<String> parent, double x) {
        BuchheimNode<String> c = new BuchheimNode<>(null, parent.y+1);
        parent.children.add(c);
        c.parent = parent;
        c.x = x;
        return c;
    }
}