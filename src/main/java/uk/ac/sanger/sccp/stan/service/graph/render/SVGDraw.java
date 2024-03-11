package uk.ac.sanger.sccp.stan.service.graph.render;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SVG-based {@link Draw} implementation.
 * Accepts the drawing instructions specified by the interface, and
 * provides a method to {@link #write} SVG for all the instructions.
 * @author dr6
 */
public class SVGDraw implements Draw {
    private final List<String> definitions;
    private final List<String> elements;
    private final String clipId;
    private final int fontHeight;

    public SVGDraw(int fontHeight) {
        this.definitions = new ArrayList<>();
        this.elements = new ArrayList<>();
        this.fontHeight = fontHeight;
        this.clipId = null;
    }

    private SVGDraw(SVGDraw other, String clipId) {
        this.definitions = other.definitions;
        this.elements = other.elements;
        this.clipId = clipId;
        this.fontHeight = other.fontHeight;
    }

    @Override
    public void addLine(int colour, DrawStroke stroke, int x0, int y0, int x1, int y1) {
        elements.add(String.format(qp("  <line x1=%q y1=%q x2=%q y2=%q %s />"),
                x0, y0, x1, y1, styleString(0, colour, stroke)));
    }

    @Override
    public void addRect(int fill, int outline, DrawStroke stroke, int x, int y, int w, int h) {
        elements.add(String.format(qp("  <rect x=%q y=%q width=%q height=%q %s />"),
                x, y, w, h, styleString(fill, outline, stroke)));
    }

    @Override
    public void addCircle(int fill, int outline, int cx, int cy, int radius) {
        elements.add(String.format(qp("  <circle cx=%q cy=%q r=%q %s />"),
                cx, cy, radius, styleString(fill, outline, null)));
    }

    @Override
    public void addString(int colour, FontStyle fontStyle, String string, int x, int y) {
        string = string.replace("\u00d7", "&times;");
        String modifiers = (fontStyle == FontStyle.BOLD ? "font-weight=\"bold\" " : "");
        elements.add(String.format(qp("  <text x=%q y=%q font-size=%q %s%s>%s</text>"),
                x, y, getFontHeight(fontStyle), modifiers, styleString(colour, 0, null), string));
    }

    @Override
    public int getFontHeight(FontStyle fontStyle) {
        return fontHeight;
    }

    @Override
    public Draw withClip(int x, int y, int w, int h) {
        SVGDraw d = new SVGDraw(this, "clip"+definitions.size());
        definitions.add(String.format(qp("    <clipPath id=%q>%n"
                        + "      <rect x=%q y=%q width=%q height=%q stroke=\"none\" fill=\"none\"/>%n"
                        + "    </clipPath>"),
                d.clipId, x, y, w, h));
        return d;
    }

    @Override
    public void close() {
    }

    private static String qp(String s) {
        return s.replace("%q", "\"%s\"");
    }

    private static String colourString(int argb) {
        if ((argb&0xff000000)==0xff000000) {
            return String.format("rgb(%s,%s,%s)", (argb>>>16)&0xff, (argb>>>8)&0xff, argb&0xff);
        }
        return String.format("rgba(%s,%s,%s,%.2f)", (argb>>>16)&0xff, (argb>>>8)&0xff, argb&0xff, ((argb>>>24)&0xff)/255f);
    }

    private String styleString(int fillColour, int strokeColour, DrawStroke stroke) {
        StringBuilder sb = new StringBuilder("style=\"");
        if (strokeColour!=0) {
            sb.append("stroke: ")
                    .append(colourString(strokeColour))
                    .append("; ");
            if (stroke!=null) {
                sb.append("stroke-width: ")
                        .append(stroke.getWidth())
                        .append("; ");
                int[] dashes = stroke.getDashArray();
                if (dashes.length > 0) {
                    sb.append("stroke-dasharray:");
                    for (int dash : dashes) {
                        sb.append(' ').append(dash);
                    }
                    sb.append("; ");
                }
            }
        } else {
            sb.append("stroke: none; ");
        }
        if (fillColour!=0) {
            sb.append("fill: ")
                    .append(colourString(fillColour))
                    .append("; ");
        } else {
            sb.append("fill: none; ");
        }
        if (this.clipId!=null) {
            sb.append("clip-path: url(#")
                    .append(this.clipId)
                    .append("); ");
        }
        sb.append('"');
        return sb.toString();
    }

    public void write(PrintStream out, int boundsX, int boundsY, int boundsW, int boundsH) {
        final int margin = 2;
        final int w = boundsW + 2*margin;
        final int h = boundsH + 2*margin;
        final int xOffset = boundsX - margin;
        final int yOffset = boundsY - margin;
        out.printf(qp("<svg xmlns=\"http://www.w3.org/2000/svg\" width=%q height=%q viewBox=\"%s %s %s %s\" >%n"),
                w, h, xOffset, yOffset, w, h);
        if (!definitions.isEmpty()) {
            out.println("  <defs>");
            for (String d : definitions) {
                out.println(d);
            }
            out.println("  </defs>");
        }
        for (String e : elements) {
            out.println(e);
        }
        out.println("</svg>");
    }

}
