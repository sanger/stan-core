package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.stereotype.Service;

/**
 * Drawing rooted trees in linear time.
 * Buchheim et al (2006)
 * @author dr6
 */
@Service
public class BuchheimAlgorithm {

    private final double distance = 2.0;

    private <E> void firstWalk(BuchheimNode<E> v) {
        if (!v.hasChildren()) {
            BuchheimNode<E> w = v.leftSibling();
            v.x = (w==null ? 0 : w.x + distance);
        } else {
            BuchheimNode<E> defaultAncestor = v.leftmostChild();
            for (BuchheimNode<E> w : v.children()) {
                firstWalk(w);
                defaultAncestor = apportion(w, defaultAncestor);
            }
            executeShifts(v);
            double midPoint = 0.5*(v.leftmostChild().x + v.rightmostChild().x);
            BuchheimNode<E> w = v.leftSibling();
            if (w!=null) {
                v.x = w.x + distance;
                v.mod = v.x - midPoint;
            } else {
                v.x = midPoint;
            }
        }
    }

    private <E> BuchheimNode<E> apportion(BuchheimNode<E> v, BuchheimNode<E> defaultAncestor) {
        BuchheimNode<E> w = v.leftSibling();
        if (w==null) {
            return defaultAncestor;
        }
        BuchheimNode<E> vir, vor, vil, vol;
        double sir, sor, sil, sol;
        vir = v;
        vor = v;
        vil = w;
        vol = v.leftmostSibling();
        sir = v.mod;
        sor = v.mod;
        sil = vil.mod;
        sol = vol.mod;
        while (vil.right()!=null && vir.left()!=null) {
            vil = vil.right();
            vir = vir.left();
            vol = vol.left();
            vor = vor.right();
            vor.ancestor = v;
            double shift = vil.x + sil - vir.x - sir + distance;
            if (shift > 0) {
                moveSubtree(ancestor(vil, v, defaultAncestor), v, shift);
                sir += shift;
                sor += shift;
            }
            sil += vil.mod;
            sir += vir.mod;
            sol += vol.mod;
            sor += vor.mod;
        }
        if (vil.right()!=null && vor.right()==null) {
            vor.thread = vil.right();
            vor.mod += sil-sor;
        } else if (vir.left()!=null && vol.left()==null) {
            vol.thread = vir.left();
            vol.mod += sir-sol;
            defaultAncestor = v;
        }
        return defaultAncestor;
    }

    private <E> void moveSubtree(BuchheimNode<E> wm, BuchheimNode<E> wp, double shift) {
        int subtrees = wp.getIndex() - wm.getIndex();
        wp.change -= shift/subtrees;
        wp.shift += shift;
        wm.change += shift/subtrees;
        wp.x += shift;
        wp.mod += shift;
    }

    private <E> void executeShifts(BuchheimNode<E> v) {
        double shift = 0;
        double change = 0;
        for (BuchheimNode<E> w : v.reverseChildren()) {
            w.x += shift;
            w.mod += shift;
            change += w.change;
            shift += w.shift + change;
        }
    }

    private <E> boolean siblings(BuchheimNode<E> a, BuchheimNode<E> b) {
        return (a!=null && b!=null && a.parent!=null && a.parent==b.parent);
    }

    private <E> BuchheimNode<E> ancestor(BuchheimNode<E> vil, BuchheimNode<E> v, BuchheimNode<E> defaultAncestor) {
        return (siblings(vil.ancestor, v) ? vil.ancestor : defaultAncestor);
    }

    private <E> void secondWalk(BuchheimNode<E> v, double m) {
        v.x += m;
        for (BuchheimNode<E> w : v.children()) {
            secondWalk(w, m+v.mod);
        }
    }

    <E> void run(BuchheimNode<E> root) {
        firstWalk(root);
        secondWalk(root, -root.x);
    }
}
