package uk.ac.sanger.sccp.stan.service.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** Test {@link BuchheimAlgorithm} */
class TestBuchheimAlgorithm {
    @Test
    void testRun() {
        BuchheimAlgorithm algo = new BuchheimAlgorithm();
        BuchheimNode<String> root = new BuchheimNode<>("root", 0);
        BuchheimNode<String> c1 = child("c1", root);
        BuchheimNode<String> c2 = child("c2", root);
        BuchheimNode<String> d1 = child("d1", c1);
        BuchheimNode<String> d2 = child("d2", c1);
        BuchheimNode<String> e1 = child("e1", c2);
        BuchheimNode<String> f1 = child("f1", e1);
        BuchheimNode<String> f2 = child("f2", e1);

        algo.run(root);

        assertEquals(0, root.x);
        assertLeft(c1, root);
        assertLeft(root, c2);
        assertLeft(d1, c1);
        assertLeft(c1, d2);
        assertEquals(e1.x, c2.x);
        assertLeft(f1, e1);
        assertLeft(e1, f2);

        root.tree().forEach(node -> assertThat(node.x).isBetween(-2.5, 2.5));
    }

    private static void assertLeft(BuchheimNode<?> a, BuchheimNode<?> b) {
        if (a.x >= b.x) {
            fail(String.format("%s (%s) should be left of %s (%s)",
                    a.data, a.x, b.data, b.x));
        }
    }

    private static <E> BuchheimNode<E> child(E data, BuchheimNode<E> parent) {
        var c = new BuchheimNode<>(data, parent.y + 1);
        parent.children.add(c);
        c.parent = parent;
        return c;
    }
}