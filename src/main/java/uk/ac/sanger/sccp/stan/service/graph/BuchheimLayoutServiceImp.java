package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Node;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * @author dr6
 */
@Service
public class BuchheimLayoutServiceImp implements BuchheimLayoutService {
    private final BuchheimAlgorithm algo;
    private final Assembler assembler;

    @Autowired
    public BuchheimLayoutServiceImp(BuchheimAlgorithm algo, Assembler assembler) {
        this.algo = algo;
        this.assembler = assembler;
    }

    @Override
    public void layout(List<Node> nodes, List<Link> links) {
        setY(nodes);
        setX(nodes, links);
    }

    public void setY(List<Node> nodes) {
        // Nodes should already be ordered by time
        int y = 0;
        LocalDateTime lastTime = nodes.getFirst().time();
        for (Node node : nodes) {
            if (node.time().isAfter(lastTime)) {
                y += 1;
                lastTime = node.time();
            }
            node.setY(y);
        }
    }

    public void setX(List<Node> nodes, List<Link> links) {
        Map<Integer, Integer> nodeParents = findParents(links);
        Collection<BuchheimNode<Node>> buchheimNodes = makeBuchheimNodes(nodes, nodeParents);
        List<BuchheimNode<Node>> roots = buchheimNodes.stream()
                .filter(bn -> bn.parent==null)
                .toList();
        for (var root : roots) {
            algo.run(root);
            realign(root);
        }
        if (roots.size() > 1) {
            assembler.assemble(roots, 2.0);
        }
        for (BuchheimNode<Node> buchheimNode : buchheimNodes) {
            if (buchheimNode.data != null) {
                buchheimNode.data.setY(buchheimNode.getY());
                buchheimNode.data.setX((int) Math.round(buchheimNode.getX()));
            }
        }
    }

    /**
     * Makes a map from each node id to a parent node id.
     */
    public Map<Integer, Integer> findParents(List<Link> links) {
        Map<Integer, Set<Integer>> childToParents = new HashMap<>();
        for (Link link : links) {
            childToParents.computeIfAbsent(link.dest(), k -> new HashSet<>()).add(link.src());
        }
        Map<Integer, Integer> bestParents = new HashMap<>(childToParents.size());
        for (var e : childToParents.entrySet()) {
            Integer parent = e.getValue().stream()
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            bestParents.put(e.getKey(), parent);
        }
        return bestParents;
    }

    public Collection<BuchheimNode<Node>> makeBuchheimNodes(List<Node> nodes, Map<Integer, Integer> parents) {
        Map<Integer, BuchheimNode<Node>> idToBuchheim = nodes.stream()
                .collect(toMap(Node::id, node -> new BuchheimNode<>(node, node.getY())));
        idToBuchheim.values().forEach(node -> node.y = node.data.getY());
        parents.forEach((childId, parId) -> {
            BuchheimNode<Node> parent = idToBuchheim.get(parId);
            BuchheimNode<Node> child = idToBuchheim.get(childId);
            while (child.getY() > parent.getY() + 1) {
                BuchheimNode<Node> phantom = new BuchheimNode<>(null, child.getY()-1);
                child.parent = phantom;
                phantom.children.add(child);
                child = phantom;
            }
            child.parent = parent;
            parent.children.add(child);
        });
        return idToBuchheim.values();
    }

    /**
     * Converts the floating point x-positions to int x positions.
     * Two distinct adjacent x-positions may be merged if they would not intersect.
     * The x-positions used in the graph will end up being every integer in a range [-M, +N],
     * with the root at zero.
     * @param root the root of the tree
     */
    private <N> void realign(BuchheimNode<N> root) {
        Map<Double, List<BuchheimNode<N>>> xNodes = new HashMap<>();
        for (BuchheimNode<N> node : root.tree()) {
            xNodes.computeIfAbsent(node.getX(), k -> new ArrayList<>()).add(node);
        }
        List<Double> xKeys = new ArrayList<>(xNodes.keySet());
        Collections.sort(xKeys);
        Map<Double, Double> reKey = new HashMap<>(); // nodes at [key] are being repositioned at [value]
        Double lastKey = null;
        Set<Integer> curYs = null;
        for (Double key : xKeys) {
            List<BuchheimNode<N>> nodes = xNodes.get(key);
            if (lastKey==null) {
                lastKey = key;
                curYs = nodes.stream().map(BuchheimNode::getY).collect(Collectors.toSet());
                continue;
            }
            Set<Integer> newYs = new HashSet<>(nodes.size());
            boolean hit = false;
            for (BuchheimNode<N> node : nodes) {
                if (curYs.contains(node.y)) {
                    hit = true;
                }
                newYs.add(node.y);
            }
            if (hit) {
                lastKey = key;
                curYs = newYs;
                continue;
            }
            curYs.addAll(newYs);
            reKey.put(key, lastKey);
        }
        xKeys.removeAll(reKey.keySet());
        int offset = xKeys.indexOf(reKey.getOrDefault(root.x, root.x));
        if (offset < 0) {
            offset = 0;
        }
        for (BuchheimNode<N> node : root.tree()) {
            node.x = xKeys.indexOf(reKey.getOrDefault(node.x, node.x)) - offset;
        }
    }
}
