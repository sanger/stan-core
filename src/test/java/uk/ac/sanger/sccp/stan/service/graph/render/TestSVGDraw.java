package uk.ac.sanger.sccp.stan.service.graph.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSVGDraw {
    final int red = 0xffff0000;
    final int blue = 0xff0000ff;
    SVGDraw draw;

    @BeforeEach
    void setup() {
        draw = new SVGDraw(16);
    }

    @Test
    void testAddLine() {
        draw.addLine(red, 10, 20, 30, 40);
        draw.addLine(blue, new DrawStroke(2, 5,5), 20, 20, 40, 40);
        assertSVG("<line x1=\"10\" y1=\"20\" x2=\"30\" y2=\"40\" style=\"stroke: rgb(255,0,0); fill: none; \" />",
                "<line x1=\"20\" y1=\"20\" x2=\"40\" y2=\"40\" style=\"stroke: rgb(0,0,255); stroke-width: 2; stroke-dasharray: 5 5; fill: none; \" />");
    }

    @Test
    void testAddRect() {
        draw.addRect(red, blue, 10,20,30,40);
        draw.addRect(0, red, new DrawStroke(2, 5, 5), 60,70,80,90);
        assertSVG("<rect x=\"10\" y=\"20\" width=\"30\" height=\"40\" style=\"stroke: rgb(0,0,255); fill: rgb(255,0,0); \" />",
                "<rect x=\"60\" y=\"70\" width=\"80\" height=\"90\" style=\"stroke: rgb(255,0,0); stroke-width: 2; stroke-dasharray: 5 5; fill: none; \" />");
    }

    @Test
    void testAddCircle() {
        draw.addCircle(blue, red, 100,200,50);
        assertSVG("<circle cx=\"100\" cy=\"200\" r=\"50\" style=\"stroke: rgb(255,0,0); fill: rgb(0,0,255); \" />");
    }

    @Test
    void testAddString() {
        draw.addString(blue, Draw.FontStyle.PLAIN, "Alpha", 50,100);
        draw.addString(red, Draw.FontStyle.BOLD, "Beta", 150, 200);
        assertSVG("<text x=\"50\" y=\"100\" font-size=\"16\" style=\"stroke: none; fill: rgb(0,0,255); \">Alpha</text>",
                "<text x=\"150\" y=\"200\" font-size=\"16\" font-weight=\"bold\" style=\"stroke: none; fill: rgb(255,0,0); \">Beta</text>");
    }

    @Test
    void testGetFontHeight() {
        assertEquals(16, draw.getFontHeight(Draw.FontStyle.PLAIN));
        assertEquals(16, draw.getFontHeight(Draw.FontStyle.BOLD));
    }

    @Test
    void testWithClip() {
        try (Draw sub = draw.withClip(100,200,300,400)) {
            sub.addLine(blue, 10, 20, 30, 40);
        }
        draw.addLine(red, 5,6,7,8);
        assertSVG("<defs>", "<clipPath id=\"clip0\">",
                "<rect x=\"100\" y=\"200\" width=\"300\" height=\"400\" stroke=\"none\" fill=\"none\"/>",
                "</clipPath>", "</defs>",
                "<line x1=\"10\" y1=\"20\" x2=\"30\" y2=\"40\" style=\"stroke: rgb(0,0,255); fill: none; clip-path: url(#clip0); \" />",
                "<line x1=\"5\" y1=\"6\" x2=\"7\" y2=\"8\" style=\"stroke: rgb(255,0,0); fill: none; \" />"
        );
    }

    @Test
    void testWrite() {
        assertSVG("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2004\" height=\"2004\" viewBox=\"-1002 -1002 2004 2004\" >",
                "</svg>");
    }

    String content() {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(os)) {
            draw.write(ps, -1000, -1000, 2000, 2000);
            return os.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void assertSVG(String... parts) {
        String svg = content();
        assertThat(svg).containsSubsequence(parts);
    }
}