package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

/**
 * The svg data of a history graph
 * @author dr6
 */
public class GraphSVG {
    private String svg;

    public GraphSVG(String svg) {
        setSvg(svg);
    }

    public GraphSVG() {}

    /** The SVG data describing the graph */
    public String getSvg() {
        return this.svg;
    }

    public void setSvg(String svg) {
        this.svg = svg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphSVG that = (GraphSVG) o;
        return (Objects.equals(this.svg, that.svg));
    }

    @Override
    public int hashCode() {
        return (this.svg==null ? 0 : this.svg.hashCode());
    }

    @Override
    public String toString() {
        return String.format("HistoryGraphSVG(length=%s)", this.svg.length());
    }
}
