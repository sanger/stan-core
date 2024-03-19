package uk.ac.sanger.sccp.stan.service.graph.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.ac.sanger.sccp.stan.request.HistoryGraph;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.HistoryGraph.Node;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.graph.render.GraphRendererImp.*;

class TestGraphRenderer {

    Draw mockDraw;
    CoordSpace mockCoords;
    HistoryGraph mockGraph;

    GraphRendererImp renderer;

    @BeforeEach
    void setup() {
        mockDraw = mock(Draw.class);
        mockGraph = mock(HistoryGraph.class);
        mockCoords = mock(CoordSpace.class);

        renderer = spy(new GraphRendererImp(mockDraw, mockCoords, mockGraph));
    }

    @Test
    void testGetWorldBounds() {
        List<Node> nodes = List.of(
                nodeAt(1, -5, 100),
                nodeAt(2, 80, 100),
                nodeAt(3, 50, 300)
        );
        when(mockGraph.getNodes()).thenReturn(nodes);
        Bounds bounds = renderer.getWorldBounds();
        assertEquals(-5 * (NODE_WIDTH + MARGIN) + MARGIN/2, bounds.x());
        assertEquals(100 * (NODE_HEIGHT + MARGIN) + MARGIN/2, bounds.y());
        assertEquals(80 * (NODE_WIDTH + MARGIN) + MARGIN/2 + NODE_WIDTH, bounds.x() + bounds.width());
        assertEquals(300 * (NODE_HEIGHT + MARGIN) + MARGIN/2 + NODE_HEIGHT, bounds.y() + bounds.height());
        assertSame(bounds, renderer.getWorldBounds());
    }

    @Test
    void testGetNodeLeft() {
        assertEquals(-10 * (NODE_WIDTH + MARGIN) + MARGIN/2, renderer.getNodeLeft(-10));
        assertEquals(MARGIN / 2, renderer.getNodeLeft(0));
        assertEquals(14 * (NODE_WIDTH + MARGIN) + MARGIN/2, renderer.getNodeLeft(14));
        assertEquals(NODE_WIDTH + MARGIN, renderer.getNodeLeft(7) - renderer.getNodeLeft(6));
    }

    @Test
    void testGetNodeCentreX() {
        assertEquals(renderer.getNodeLeft(7) + NODE_WIDTH/2f, renderer.getNodeCentreX(7));
        assertEquals(renderer.getNodeLeft(-4) + NODE_WIDTH/2f, renderer.getNodeCentreX(-4));
    }

    @Test
    void testGetNodeTop() {
        assertEquals(-10 * (NODE_HEIGHT + MARGIN) + MARGIN/2, renderer.getNodeTop(-10));
        assertEquals(MARGIN / 2, renderer.getNodeTop(0));
        assertEquals(14 * (NODE_HEIGHT + MARGIN) + MARGIN/2, renderer.getNodeTop(14));
        assertEquals(NODE_HEIGHT + MARGIN, renderer.getNodeTop(7) - renderer.getNodeTop(6));
    }

    @Test
    void testGetNodeCentreY() {
        assertEquals(renderer.getNodeTop(7) + NODE_HEIGHT/2f, renderer.getNodeCentreY(7));
        assertEquals(renderer.getNodeTop(-4) + NODE_HEIGHT/2f, renderer.getNodeCentreY(-4));
    }

    @Test
    void testGetRenderBounds() {
        Node node = nodeAt(1, 3, 5);

        doReturn(700).when(renderer).getNodeLeft(anyInt());
        doReturn(701).when(renderer).getNodeTop(anyInt());

        when(mockCoords.toRenderX(anyFloat())).thenReturn(20);
        when(mockCoords.toRenderY(anyFloat())).thenReturn(21);
        when(mockCoords.toRenderScale(NODE_WIDTH)).thenReturn(22);
        when(mockCoords.toRenderScale(NODE_HEIGHT)).thenReturn(23);

        assertEquals(new Bounds(20,21,22,23), renderer.getRenderBounds(node));

        verify(renderer).getNodeLeft(3);
        verify(renderer).getNodeTop(5);
        verify(mockCoords).toRenderX(700);
        verify(mockCoords).toRenderY(701);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testGetExportBounds(boolean datesOn) {
        int fh = 20;
        Bounds worldBounds = new Bounds(5,6,7,8);
        doReturn(worldBounds).when(renderer).getWorldBounds();
        Bounds drawBounds = new Bounds(15,16,17,18);
        when(mockCoords.toRender(worldBounds)).thenReturn(drawBounds);
        when(mockCoords.toRenderScale(MARGIN/2f)).thenReturn(4);
        renderer.setDatesOn(datesOn);
        int topMargin = 4;
        int btmMargin = 4;
        int leftMargin = 4;
        int rightMargin = 4;
        if (datesOn) {
            when(mockCoords.toRenderY(0f)).thenReturn(7);
            leftMargin = 6*fh;
            topMargin = drawBounds.y() + 4 + fh - 7;
        }
        Bounds bds = renderer.getExportBounds(20);
        assertEquals(drawBounds.x() - leftMargin, bds.x());
        assertEquals(drawBounds.y() - topMargin, bds.y());
        assertEquals(drawBounds.x() + drawBounds.width() + rightMargin, bds.x() + bds.width());
        assertEquals(drawBounds.y() + drawBounds.height() + btmMargin, bds.y() + bds.height());
    }

    @Test
    void testIsDatesOn() {
        renderer.setDatesOn(false);
        assertFalse(renderer.isDatesOn());
        renderer.setDatesOn(true);
        assertTrue(renderer.isDatesOn());
    }

    @Test
    void testGetDateLines() {
        List<Node> nodes = List.of(
                nodeAtTime(1, 1, 1, timeAt(1, 1)),
                nodeAtTime(2, 1, 2, timeAt(1, 2)),
                nodeAtTime(3, 1, 3, timeAt(2, 1)),
                nodeAtTime(4, 1, 4, timeAt(2, 2)),
                nodeAtTime(5, 1, 5, timeAt(5, 1))
        );
        when(mockGraph.getNodes()).thenReturn(nodes);
        List<DateLine> dateLines = renderer.getDateLines();
        assertThat(dateLines).containsExactly(
                new DateLine(1, LocalDate.of(2024,1,1)),
                new DateLine(3, LocalDate.of(2024,1,2)),
                new DateLine(5, LocalDate.of(2024,1,5))
        );
        assertSame(dateLines, renderer.getDateLines());
    }

    @Test
    void testRender() {
        doNothing().when(renderer).paintDropShadow(any());
        doNothing().when(renderer).drawDateLines();
        doNothing().when(renderer).drawLink(any());
        doNothing().when(renderer).paintNode(any());

        List<Node> nodes = List.of(nodeAt(1, 2, 3), nodeAt(4,5,6));
        when(mockGraph.getNodes()).thenReturn(nodes);
        List<Link> links = List.of(new Link(1,2), new Link(3,4));
        when(mockGraph.getLinks()).thenReturn(links);

        renderer.render();

        InOrder inOrder = Mockito.inOrder(renderer);
        for (Node node : nodes) {
            inOrder.verify(renderer).paintDropShadow(node);
        }
        inOrder.verify(renderer).drawDateLines();
        for (Link link : links) {
            inOrder.verify(renderer).drawLink(link);
        }
        for (Node node : nodes) {
            inOrder.verify(renderer).paintNode(node);
        }
    }

    @Test
    void testPaintDropShadow() {
        when(mockCoords.toRenderX(anyFloat())).thenReturn(10);
        when(mockCoords.toRenderY(anyFloat())).thenReturn(20);
        when(mockCoords.toRenderScale(NODE_WIDTH)).thenReturn(40);
        when(mockCoords.toRenderScale(NODE_HEIGHT)).thenReturn(30);
        Node node = nodeAt(1, 3, 4);
        renderer.paintDropShadow(node);
        verify(mockDraw).addRect(dropShadowColour, 0, 11, 21, 40, 30);
    }

    @Test
    void testDrawDateLines() {
        int fh = 16;
        int colour = dateColour;
        doReturn(fh).when(mockDraw).getFontHeight(any());
        Bounds worldBds = new Bounds(10, 10, 500, 500);
        doReturn(worldBds).when(renderer).getWorldBounds();

        doReturn(196).when(mockCoords).toRenderX(anyFloat());
        doReturn(304).when(mockCoords).toRenderScale(worldBds.width());

        List<DateLine> dateLines = List.of(
                new DateLine(1, LocalDate.of(2024,1,1)),
                new DateLine(3, LocalDate.of(2024,1,4))
        );
        doReturn(dateLines).when(renderer).getDateLines();
        when(mockCoords.toRenderY(anyFloat())).then(invocation -> {
            float wy = invocation.getArgument(0);
            return (int) (10*wy);
        });

        renderer.drawDateLines();
        int ry = 10 * (NODE_HEIGHT+MARGIN);

        verify(mockDraw).addString(colour, Draw.FontStyle.PLAIN, "2024-01-01", 110, ry+fh);
        verify(mockDraw).addLine(eq(colour), any(), eq(100), eq(ry), eq(500), eq(ry));
        ry = 10 * 3 * (NODE_HEIGHT+MARGIN);
        verify(mockDraw).addString(colour, Draw.FontStyle.PLAIN, "2024-01-04", 110, ry+fh);
        verify(mockDraw).addLine(eq(colour), any(), eq(100), eq(ry), eq(500), eq(ry));
    }

    @Test
    void testDrawLink() {
        Link link = new Link(0,2);
        List<Node> nodes = List.of(
                nodeAt(0, 1, 2),
                nodeAt(1, 2,5),
                nodeAt(2, 5, 6)
        );
        when(mockGraph.getNodes()).thenReturn(nodes);
        int colour = linkColour;
        final float px = 100;
        final float py = 200;
        final float cx = 300;
        final float cy = 500;
        doReturn(px).when(renderer).getNodeCentreX(1);
        doReturn(py).when(renderer).getNodeCentreY(2);
        doReturn(cx).when(renderer).getNodeCentreX(5);
        doReturn(cy).when(renderer).getNodeCentreY(6);

        ToIntFunction<Float> toRx = wx -> (5 + (int) (wx*10));
        ToIntFunction<Float> toRy = wy -> (10 + (int) (wy*8));

        when(mockCoords.toRenderX(anyFloat())).then(invocation -> toRx.applyAsInt(invocation.getArgument(0)));
        when(mockCoords.toRenderY(anyFloat())).then(invocation -> toRy.applyAsInt(invocation.getArgument(0)));

        final int topY = 210;

        doReturn(topY).when(renderer).getNodeTop(2);
        final float by = topY + NODE_HEIGHT + MARGIN*0.75f;

        renderer.drawLink(link);

        int rpx = toRx.applyAsInt(px);
        int rpy = toRy.applyAsInt(py);
        int rcx = toRx.applyAsInt(cx);
        int rcy = toRy.applyAsInt(cy);
        int rby = toRy.applyAsInt(by);

        verify(mockDraw).addLine(colour, rpx, rpy, rpx, rby);
        verify(mockDraw).addLine(colour, rpx, rby, rcx, rby);
        verify(mockDraw).addLine(colour, rcx, rby, rcx, rcy);
    }

    @Test
    void testDrawLink_vertical() {
        Link link = new Link(0,2);
        List<Node> nodes = List.of(
                nodeAt(0, 1, 2),
                nodeAt(1, 2,5),
                nodeAt(2, 1, 6)
        );
        when(mockGraph.getNodes()).thenReturn(nodes);
        final float px = 100;
        final float py = 200;
        final float cy = 500;
        doReturn(px).when(renderer).getNodeCentreX(1);
        doReturn(py).when(renderer).getNodeCentreY(2);
        doReturn(cy).when(renderer).getNodeCentreY(6);

        ToIntFunction<Float> toRx = wx -> (5 + (int) (wx*10));
        ToIntFunction<Float> toRy = wy -> (10 + (int) (wy*8));

        when(mockCoords.toRenderX(anyFloat())).then(invocation -> toRx.applyAsInt(invocation.getArgument(0)));
        when(mockCoords.toRenderY(anyFloat())).then(invocation -> toRy.applyAsInt(invocation.getArgument(0)));

        renderer.drawLink(link);

        int rpx = toRx.applyAsInt(px);
        int rpy = toRy.applyAsInt(py);
        int rcy = toRy.applyAsInt(cy);

        verify(mockDraw).addLine(linkColour, rpx, rpy, rpx, rcy);
    }

    @Test
    void testPaintNode() {
        Node node = nodeAt(1, 10, 20);
        Bounds bounds = new Bounds(100, 200, 400, 300);
        doReturn(bounds).when(renderer).getRenderBounds(node);
        Draw clipDraw = mock(Draw.class);
        when(mockDraw.withClip(bounds.x(), bounds.y(), bounds.width(), bounds.height())).thenReturn(clipDraw);
        List<String> lines = List.of("Alpha", "Beta");
        doReturn(lines).when(renderer).getNodeLines(node);
        int inset = 12;
        when(mockCoords.toRenderScale(5)).thenReturn(inset);
        final int fh = 16;
        when(clipDraw.getFontHeight(any())).thenReturn(fh);

        renderer.paintNode(node);

        verify(clipDraw).addString(nodeOutline, Draw.FontStyle.BOLD, "Alpha", bounds.x() + inset, bounds.y()+fh);
        verify(clipDraw).addString(nodeOutline, Draw.FontStyle.PLAIN, "Beta", bounds.x() + inset, bounds.y() + 2*fh);
    }

    @Test
    void testGetNodeLines() {
        Node node = new Node(1, null, "Alpha", "STAN-1", "user1", "EXT", "BS");
        assertThat(renderer.getNodeLines(node)).containsExactly("Alpha", "EXT", "BS", "User: user1", "STAN-1");
    }

    private static Node nodeAt(int id, int x, int y) {
        return nodeAtTime(id, x, y, null);
    }

    private static Node nodeAtTime(int id, int x, int y, LocalDateTime time) {
        Node node = new Node(id, time, null, null, null, null, null);
        node.setX(x);
        node.setY(y);
        return node;
    }

    private static LocalDateTime timeAt(int day, int hour) {
        return LocalDateTime.of(2024,1, day, hour, 0);
    }
}