package uk.ac.sanger.sccp.stan.service.graph.render;

import uk.ac.sanger.sccp.stan.request.HistoryGraph;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Node;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

/**
 * @author dr6
 */
public class GraphRendererImp implements GraphRenderer {
    public static final int NODE_WIDTH = 160, NODE_HEIGHT = 90, MARGIN = 50;

    private static final int nodeOutline = 0xff000000, nodeFill = 0xffffffff, linkColour = 0x800000ff, dateColour = 0xffc0c0c0;
    private static final int dropShadowColour = 0x40000000;

    private final Draw draw;
    private final HistoryGraph graph;
    private final CoordSpace coords;
    private boolean datesOn = true;
    private final DrawStroke dateStroke = new DrawStroke(1, 10);
    private List<DateLine> dateLines;

    private Bounds worldBounds;

    public GraphRendererImp(Draw draw, CoordSpace coords, HistoryGraph graph) {
        this.draw = draw;
        this.coords = coords;
        this.graph = graph;
    }

    private Bounds calculateWorldBounds() {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Node node : graph.getNodes()) {
            minX = Math.min(minX, node.getX());
            maxX = Math.max(maxX, node.getX());
            minY = Math.min(minY, node.getY());
            maxY = Math.max(maxY, node.getY());
        }
        int wx0 = getNodeLeft(minX);
        int wy0 = getNodeTop(minY);
        int wx1 = getNodeLeft(maxX) + NODE_WIDTH;
        int wy1 = getNodeTop(maxY) + NODE_HEIGHT;
        return new Bounds(wx0, wy0, wx1-wx0, wy1-wy0);
    }

    public Bounds getWorldBounds() {
        if (worldBounds==null) {
            worldBounds = calculateWorldBounds();
        }
        return worldBounds;
    }

    private int getExportedDateXOffset(int fontHeight) {
        return 6 * fontHeight;
    }

    public int getNodeLeft(int nodeX) {
        return nodeX * (NODE_WIDTH + MARGIN) + MARGIN / 2;
    }

    public float getNodeCentreX(int nodeX) {
        return getNodeLeft(nodeX) + NODE_WIDTH / 2f;
    }

    public int getNodeTop(int nodeY) {
        return nodeY * (NODE_HEIGHT + MARGIN) + MARGIN / 2;
    }

    public float getNodeCentreY(int nodeY) {
        return getNodeTop(nodeY) + NODE_HEIGHT / 2f;
    }

    /**
     * Gets the bounds in render-space of the given node
     * @param node the node
     * @return the bounds of the node in render-space
     */
    public Bounds getRenderBounds(Node node) {
        return new Bounds(coords.toRenderX(getNodeLeft(node.getX())),
                coords.toRenderY(getNodeTop(node.getY())),
                coords.toRenderScale(NODE_WIDTH), coords.toRenderScale(NODE_HEIGHT));
    }

    @Override
    public Bounds getExportBounds(int fontSize) {
        Bounds drawBounds = coords.toRender(getWorldBounds());
        final int margin = coords.toRenderScale(MARGIN / 2f);
        int left = margin, top = margin;
        if (isDatesOn()) {
            top = Math.max(drawBounds.y() + 4 + fontSize - coords.toRenderY(0), top);
            left = Math.max(getExportedDateXOffset(fontSize), left);
        }
        return new Bounds(drawBounds.x() - left , drawBounds.y() - top,
                drawBounds.width() + left + margin, drawBounds.height() + top + margin);
    }

    /**
     * Are date lines drawn?
     * @return true if date lines should be drawn
     */
    public boolean isDatesOn() {
        return this.datesOn;
    }

    /**
     * Sets whether date lines are drawn
     * @param datesOn whether to draw dates
     */
    public void setDatesOn(boolean datesOn) {
        this.datesOn = datesOn;
    }

    /**
     * Gets the date to draw on the graph
     * @return the dates to draw
     */
    public List<DateLine> getDateLines() {
        if (dateLines==null) {
            Map<LocalDate, List<Node>> dateNodes = graph.getNodes().stream()
                    .collect(groupingBy(node -> node.time().toLocalDate()));
            dateLines = new ArrayList<>(dateNodes.size());
            dateNodes.forEach((date, nodes) -> {
                int y = nodes.stream().mapToInt(Node::getY).min().orElseThrow();
                dateLines.add(new DateLine(y, date));
            });
            dateLines.sort(Comparator.comparingInt(DateLine::y));
        }
        return dateLines;
    }

    @Override
    public void render() {
        graph.getNodes().forEach(this::paintDropShadow);
        if (datesOn) {
            drawDateLines();
        }
        graph.getLinks().forEach(this::drawLink);
        graph.getNodes().forEach(this::paintNode);
    }

    /**
     * Paints the dropshadow underneath the node
     * @param node the node to paint the dropshadow for
     */
    public void paintDropShadow(Node node) {
        int x = coords.toRenderX(getNodeLeft(node.getX())+5)+1;
        int y = coords.toRenderY(getNodeTop(node.getY())+5)+1;
        int wid = coords.toRenderScale(NODE_WIDTH);
        int hei = coords.toRenderScale(NODE_HEIGHT);
        draw.addRect(dropShadowColour, 0, x, y, wid, hei);
    }

    /**
     * Draws the lines indicating dates
     */
    public void drawDateLines() {
        Bounds worldBounds = getWorldBounds();
        int adjustment = getExportedDateXOffset(draw.getFontHeight(Draw.FontStyle.PLAIN));
        int rx = coords.toRenderX(worldBounds.x()) - adjustment;
        int rw = coords.toRenderScale(worldBounds.width()) + adjustment;
        final int colour = dateColour;
        final DrawStroke stroke = dateStroke;
        final int fh = draw.getFontHeight(Draw.FontStyle.PLAIN);

        for (var dateLine : getDateLines()) {
            String string = dateLine.date().toString();
            int ry = coords.toRenderY(dateLine.y() * (NODE_HEIGHT + MARGIN));
            draw.addString(colour, Draw.FontStyle.PLAIN, string, rx + 10, ry + fh);
            draw.addLine(colour, stroke, rx, ry, rx + rw, ry);
        }
    }

    /**
     * Draws a line between two linked nodes
     * @param link the link to draw
     */
    public void drawLink(Link link) {
        final int colour = linkColour;
        Node parent = graph.getNodes().get(link.src());
        Node child = graph.getNodes().get(link.dest());
        float px = getNodeCentreX(parent.getX());
        float py = getNodeCentreY(parent.getY());
        float cx = getNodeCentreX(child.getX());
        float cy = getNodeCentreY(child.getY());
        if (px==cx) {
            // line straight down
            draw.addLine(colour, coords.toRenderX(px), coords.toRenderY(py),
                    coords.toRenderX(cx), coords.toRenderY(cy));
            return;
        }
        // pick a spot to go horizontal
        float by = getNodeTop(parent.getY()) + NODE_HEIGHT + MARGIN * 0.75f;
        int rpx = coords.toRenderX(px);
        int rpy = coords.toRenderY(py);
        int rcx = coords.toRenderX(cx);
        int rcy = coords.toRenderY(cy);
        int rby = coords.toRenderY(by);
        draw.addLine(colour, rpx, rpy, rpx, rby);
        draw.addLine(colour, rpx, rby, rcx, rby);
        draw.addLine(colour, rcx, rby, rcx, rcy);
    }

    /**
     * Paints the given node (rectangle and contents)
     * @param node the node to paint
     */
    public void paintNode(Node node) {
        Bounds bds = getRenderBounds(node);
        draw.addRect(nodeFill, nodeOutline, bds.x(), bds.y(), bds.width(), bds.height());
        List<String> lines = getNodeLines(node);
        try (Draw nodeDraw = draw.withClip(bds.x(), bds.y(), bds.width(), bds.height())) {
            int yText = bds.y();
            int inset = coords.toRenderScale(5);
            for (int i = 0; i < lines.size(); i++) {
                Draw.FontStyle fontStyle = (i==0 ? Draw.FontStyle.BOLD : Draw.FontStyle.PLAIN);
                yText += nodeDraw.getFontHeight(fontStyle);
                nodeDraw.addString(nodeOutline, fontStyle, lines.get(i), bds.x() + inset, yText);
            }
        }
    }

    /**
     * Gets the lines of text that go in a node.
     * @param node the node to describe
     * @return a list of strings about the node
     */
    public List<String> getNodeLines(Node node) {
        return Stream.of(node.heading(), node.externalName(), node.bioStateDesc(), "User: "+node.user(),
                        node.destBarcode())
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * A date position at a certain y-coordinate
     * @param y the y-coordinate of the date in the graph
     * @param date the date
     */
    public record DateLine(int y, LocalDate date) {}
}
