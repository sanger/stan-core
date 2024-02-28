package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.model.HistoryGraph.Node;
import uk.ac.sanger.sccp.stan.request.History;
import uk.ac.sanger.sccp.stan.request.HistoryEntry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/**
 * @author dr6
 */
@Service
public class GraphServiceImp implements GraphService {
    private final BuchheimLayoutService layoutService;

    @Autowired
    public GraphServiceImp(BuchheimLayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @Override
    public HistoryGraph createGraph(History history) {
        Map<Integer, Labware> lwMap = history.getLabware().stream()
                .collect(inMap(Labware::getId));
        Map<Integer, Sample> sampleMap = history.getSamples().stream()
                .collect(inMap(Sample::getId));
        List<NodeData> nodeData = compileNodeData(history, sampleMap);
        nodeData.forEach(this::analyseActions);
        for (int i = 0; i < nodeData.size(); ++i) {
            nodeData.get(i).nodeId = i;
        }
        return createHistoryGraph(nodeData, lwMap, sampleMap);
    }

    /**
     * Creates a history graph object from the given node data
     * @param nodeData data to convert to a history graph
     * @param lwMap map to look up labware from its ids
     * @param sampleMap map to look up samples from their ids
     * @return a history graph of the given node data
     */
    public HistoryGraph createHistoryGraph(List<NodeData> nodeData, Map<Integer, Labware> lwMap, Map<Integer, Sample> sampleMap) {
        List<Link> links = createLinks(nodeData);
        List<Node> nodes = createNodes(nodeData, lwMap, sampleMap);
        layoutService.layout(nodes, links);
        return new HistoryGraph(nodes, links);
    }

    /**
     * Creates nodes from the given node data
     * @param nodeData the node data to convert to nodes
     * @param lwMap map to look up labware from its ids
     * @param sampleMap map to look up samples from their ids
     * @return a list of nodes representing the given node data
     */
    public List<Node> createNodes(List<NodeData> nodeData, Map<Integer, Labware> lwMap, Map<Integer, Sample> sampleMap) {
        return nodeData.stream()
                .map(nd -> createNode(nd, lwMap, sampleMap))
                .toList();
    }

    /**
     * Creates a node from the given node data
     * @param nd the node data to convert to a node
     * @param lwMap map to look up labware from its ids
     * @param sampleMap map to look up samples from their ids
     * @return a node
     */
    public Node createNode(NodeData nd, Map<Integer, Labware> lwMap, Map<Integer, Sample> sampleMap) {
        HistoryEntry entry = nd.entries.getFirst();
        Labware lw = lwMap.get(entry.getDestinationLabwareId());
        Set<Sample> samples = nd.entries.stream()
                .map(e -> sampleMap.get(e.getSampleId()))
                .filter(Objects::nonNull)
                .collect(toSet());
        Sample sample = samples.isEmpty() ? null : samples.iterator().next();
        return new Node(nd.nodeId, entry.getTime(), entry.getType(), lw.getBarcode(),
                entry.getUsername(), sample==null ? null : sample.getTissue().getExternalName(),
                describeBio(samples));
    }

    /**
     * Finds slot/sample information for each entry in the nodedata and stores it in the nodedata.
     * @param nodeData the nodeData to analyse
     */
    public void analyseActions(final NodeData nodeData) {
        if (nodeData.isOperation()) {
            nodeData.sourceSs = new HashSet<>();
            nodeData.destSs = new HashSet<>();
            for (HistoryEntry entry : nodeData.entries) {
                analyseActions(entry, nodeData.sourceSs, nodeData.destSs);
            }
        }
    }

    /**
     * Finds slot/samples from the sources and samples of actions in the history entry.
     * Adds then to the provided sets.
     * @param entry a history entry to analyse
     * @param sourceSs set to receive source information
     * @param destSs set to receive destination information
     */
    public void analyseActions(final HistoryEntry entry, final Set<SlotSample> sourceSs, final Set<SlotSample> destSs) {
        final Operation op = entry.getOperation();
        final Integer sampleId = entry.getSampleId();
        if (op==null || sampleId==null) {
            return;
        }
        final int sourceLwId = entry.getSourceLabwareId();
        final int destLwId = entry.getDestinationLabwareId();
        op.getActions().stream()
                .filter(ac -> ac.getSample().getId().equals(sampleId)
                        && ac.getSource().getLabwareId()==sourceLwId
                        && ac.getDestination().getLabwareId()==destLwId)
                .forEach(ac -> {
                    sourceSs.add(new SlotSample(ac.getSource(), ac.getSourceSample()));
                    destSs.add(new SlotSample(ac.getDestination(), ac.getSample()));
                });
    }

    /**
     * Creates NodeData for the given history
     * @param history the history to convert to node data
     * @param sampleMap map to look up referenced samples from their ids
     * @return a list of node data
     */
    public List<NodeData> compileNodeData(History history, Map<Integer, Sample> sampleMap) {
        Map<NodeKey, List<HistoryEntry>> groups = history.getEntries().stream()
                .collect(Collectors.groupingBy(e -> keyForEntry(e, sampleMap)));
        return groups.values().stream()
                .map(NodeData::new)
                .sorted(Comparator.comparing(NodeData::time).thenComparing(NodeData::entryId))
                .toList();
    }

    /**
     * Describes the bio state/section numbers of the given samples
     * @param samples the samples to describe
     * @return a string describing the state of the samples
     */
    public String describeBio(Collection<Sample> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        String desc = samples.iterator().next().getBioState().getName();
        List<Integer> sections = samples.stream()
                .map(Sample::getSection)
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .toList();
        if (!sections.isEmpty()) {
            desc += "; s"+summariseSections(sections);
        }
        return desc;
    }

    /**
     * Creates a description of the section numbers of the given samples
     * @param sections a monotonic sequence of ints
     * @return a string describing the given sections
     */
    public String summariseSections(List<Integer> sections) {
        if (sections.isEmpty()) {
            return null;
        }
        if (sections.size()==1) {
            return sections.getFirst().toString();
        }
        if (sections.size()==2) {
            return sections.getFirst().toString()+","+sections.getLast().toString();
        }
        if (sections.getLast()==sections.getFirst()+sections.size()-1) {
            return sections.getFirst()+"-"+sections.getLast();
        }
        return sections.stream()
                .map(Object::toString)
                .collect(joining(","));
    }

    /**
     * Creates a key used to group history entries
     * @param entry the history entry
     * @param sampleMap map to look up referenced samples from their ids
     * @return the key for this entry
     */
    public NodeKey keyForEntry(HistoryEntry entry, Map<Integer, Sample> sampleMap) {
        Sample sample = sampleMap.get(entry.getSampleId());
        return new NodeKey(entry.getType(), entry.getEventId(), entry.getDestinationLabwareId(),
                sample==null ? null : sample.getTissue().getId(),
                sample==null ? null : sample.getBioState().getId());
    }

    /**
     * Finds links between nodes.
     * Nodes are linked in parent/child relationships, indicating that one operation was the immediate
     * predecessor of another. A node can have multiple parents and multiple children.
     * @param nodeData the data used to create nodes
     * @return links between node ids
     */
    public List<Link> createLinks(List<NodeData> nodeData) {
        List<Link> links = new ArrayList<>();
        for (int i = nodeData.size()-1; i > 0; --i) {
            NodeData child = nodeData.get(i);
            int lwId = child.sourceLabwareId();
            if (!child.isOperation()) {
                for (int j = i-1; j >= 0; --j) {
                    NodeData parent = nodeData.get(j);
                    if (parent.destLabwareId()==lwId) {
                        links.add(new Link(parent.nodeId, child.nodeId));
                        break;
                    }
                }
                continue;
            }
            Set<SlotSample> sources = new HashSet<>(child.sourceSs);
            for (int j = i-1; j >= 0; --j) {
                NodeData parent = nodeData.get(j);
                if (parent.destLabwareId()!=lwId) {
                    continue;
                }
                if (!parent.isOperation() || sources.removeAll(parent.destSs)) {
                    links.add(new Link(parent.nodeId, child.nodeId));
                }
                if (sources.isEmpty()) {
                    break;
                }
            }
        }
        return links;
    }

    /**
     * A key used to group similar history entries.
     * If they have the same type, (dest) labware id, (op/event) id, tissue id and bio state id,
     * they can show in the same graph node
     * @param heading the event heading (e.g. op type)
     * @param id the op id or event id
     * @param labwareId the destination labware id
     * @param tissueId the id of the tissue
     * @param bsId the id of the bio state
     */
    public record NodeKey(String heading, Integer id, Integer labwareId, Integer tissueId, Integer bsId) {}

    /**
     * The data used to create a node
     */
    public static class NodeData {
        // The history entries that the node describes
        private final List<HistoryEntry> entries;
        // The slot/samples used to find links between nodes
        private Set<SlotSample> sourceSs, destSs;
        // The arbitrary id given to nodes in a graph
        private Integer nodeId;

        public NodeData(List<HistoryEntry> entries) {
            this.entries = entries;
        }

        /** Does this node represent an operation? */
        public boolean isOperation() {
            return (!this.entries.isEmpty() && this.entries.getFirst().getOperation()!=null);
        }

        /** The time this op/event took place */
        public LocalDateTime time() {
            return this.entries.getFirst().getTime();
        }

        /** The id of the op or event */
        public int entryId() {
            return this.entries.getFirst().getEventId();
        }

        /** The destination labware id of the op/event */
        public int destLabwareId() {
            return this.entries.getFirst().getDestinationLabwareId();
        }
        /** The source labware id of the op/event */
        public int sourceLabwareId() {
            return this.entries.getFirst().getSourceLabwareId();
        }
    }
}
