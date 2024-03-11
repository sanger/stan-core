package uk.ac.sanger.sccp.stan.service.graph;

import java.util.ArrayList;
import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.reverseIter;

/**
 * A node used in the Buchheim layout
 * @author dr6
 */
public class BuchheimNode<V> {
    V data;
    BuchheimNode<V> parent;
    List<BuchheimNode<V>> children;
    double x;
    int y;
    double mod;
    BuchheimNode<V> thread;
    BuchheimNode<V> ancestor;
    double change;
    double shift;
    int index = -1;

    BuchheimNode(V data, int y) {
        this.data = data;
        this.parent = null;
        this.children = new ArrayList<>();
        this.y = y;
        this.mod = 0;
        this.ancestor = this;
        this.change = 0;
        this.shift = 0;
    }

    public boolean hasChildren() {
        return !this.children.isEmpty();
    }

    public Iterable<BuchheimNode<V>> children() {
        return this.children;
    }

    public Iterable<BuchheimNode<V>> reverseChildren() {
        return reverseIter(this.children);
    }

    public Iterable<BuchheimNode<V>> tree() {
        return () -> new TreeIterator<>(this);
    }

    public int getIndex() {
        if (this.index < 0) {
            this.index = (this.parent==null ? 0 : this.parent.children.indexOf(this));
        }
        return this.index;
    }

    public BuchheimNode<V> left() {
        return (hasChildren() ? children.getFirst() : this.thread);
    }
    public BuchheimNode<V> right() {
        return (hasChildren() ? children.getLast() : this.thread);
    }

    public BuchheimNode<V> leftSibling() {
        int i = getIndex();
        return (i>0 ? this.parent.children.get(i-1) : null);
    }
    public BuchheimNode<V> leftmostSibling() {
        return (parent==null ? null : parent.children.getFirst());
    }
    public BuchheimNode<V> leftmostChild() {
        return (hasChildren() ? children.getFirst() : null);
    }
    public BuchheimNode<V> rightmostChild() {
        return (hasChildren() ? children.getLast() : null);
    }

    public double getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}
