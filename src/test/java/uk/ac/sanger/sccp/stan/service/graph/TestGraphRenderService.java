package uk.ac.sanger.sccp.stan.service.graph;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.request.GraphSVG;
import uk.ac.sanger.sccp.stan.request.HistoryGraph;
import uk.ac.sanger.sccp.stan.service.graph.render.*;

import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Tests {@link GraphRenderService} */
class TestGraphRenderService {

    @Test
    void makeRenderer() {
        GraphRenderService service = new GraphRenderService();
        GraphRenderer gr = service.makeRenderer(mock(Draw.class), mock(CoordSpace.class), mock(HistoryGraph.class));
        assertThat(gr).isInstanceOf(GraphRendererImp.class);
    }

    @Test
    void makeSVGDraw() {
        GraphRenderService service = new GraphRenderService();
        assertThat(service.makeSVGDraw()).isInstanceOf(SVGDraw.class);
    }

    @Test
    void makeCoordSpace() {
        GraphRenderService service = new GraphRenderService();
        assertThat(service.makeCoordSpace()).isInstanceOf(CoordSpace.class);
    }

    @Test
    void toSVG() {
        final int fh = 16;
        float zoom = 1.5f;
        String svgData = "SVG DATA\n";

        GraphRenderService service = spy(GraphRenderService.class);
        CoordSpace coords = mock(CoordSpace.class);
        GraphRenderer renderer = mock(GraphRenderer.class);
        SVGDraw draw = mock(SVGDraw.class);
        HistoryGraph graph = mock(HistoryGraph.class);

        //noinspection resource
        doReturn(draw).when(service).makeSVGDraw();
        doReturn(coords).when(service).makeCoordSpace();
        doReturn(renderer).when(service).makeRenderer(any(), any(), any());
        Bounds bounds = new Bounds(1,2,3,4);
        doReturn(bounds).when(renderer).getExportBounds(fh);
        doReturn(fh).when(draw).getFontHeight(Draw.FontStyle.PLAIN);
        doAnswer(invocation -> {
            PrintStream ps = invocation.getArgument(0);
            ps.print(svgData);
            return null;
        }).when(draw).write(any(), anyInt(), anyInt(), anyInt(), anyInt());

        GraphSVG svg = service.toSVG(graph, zoom);
        assertEquals(svgData, svg.getSvg());

        verify(service).makeRenderer(draw, coords, graph);
        verify(coords).setZoom(zoom);
        verify(renderer).render();
        verify(renderer).getExportBounds(fh);
        verify(draw).write(any(), eq(bounds.x()), eq(bounds.y()), eq(bounds.width()), eq(bounds.height()));
        verify(draw).close();
    }
}