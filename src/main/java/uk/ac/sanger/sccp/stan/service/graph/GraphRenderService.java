package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.GraphSVG;
import uk.ac.sanger.sccp.stan.request.HistoryGraph;
import uk.ac.sanger.sccp.stan.service.graph.render.*;

import java.io.*;

/**
 * Service to render a history graph to SVG
 * @author dr6
 */
@Service
public class GraphRenderService {
    /**
     * Makes a new graph renderer
     * @param draw the draw object to use in the renderer
     * @param coords the coordinate space to use in the renderer
     * @param graph the graph to render
     * @return a new graph renderer
     */
    public GraphRenderer makeRenderer(Draw draw, CoordSpace coords, HistoryGraph graph) {
        return new GraphRendererImp(draw, coords, graph);
    }

    /**
     * Makes an SVG draw object
     * @return a new SVG draw object
     */
    public SVGDraw makeSVGDraw() {
        return new SVGDraw(16);
    }

    /**
     * Makes a coord space object
     * @return a new coord space object
     */
    public CoordSpace makeCoordSpace() {
        return new CoordSpace();
    }

    /**
     * Renders the given history graph to SVG
     * @param graph the graph to render
     * @return a new object containing SVG data
     */
    public GraphSVG toSVG(HistoryGraph graph, float zoom) {
        try (SVGDraw draw = makeSVGDraw()) {
            CoordSpace coords = makeCoordSpace();
            coords.setZoom(zoom);
            GraphRenderer renderer = makeRenderer(draw, coords, graph);
            renderer.render();
            Bounds bounds = renderer.getExportBounds(draw.getFontHeight(Draw.FontStyle.PLAIN));
            try (ByteArrayOutputStream os = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(os)) {
                draw.write(ps, bounds.x(), bounds.y(), bounds.width(), bounds.height());
                return new GraphSVG(os.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
