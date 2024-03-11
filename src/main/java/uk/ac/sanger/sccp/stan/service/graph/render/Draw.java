/*
 * Copyright (c) 2016 Genome Research Ltd. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.sanger.sccp.stan.service.graph.render;

/**
 * An interface for simple drawing requirements.
 * The requirements are essentially:
 *  <ul>
 *     <li>draw lines, rectangles, circles and strings, in order
 *     <li>some support for line-width and dashed lines
 *     <li>colour (including alpha) for lines, shape-fill and shape-outlines
 *     <li>rectangular clipping
 *  </ul>
 * Colours are given in <tt>argb</tt> form. Any colour with zero in the top byte (for instance, <tt>argb==0</tt>)
 * is fully transparent.
 * @author dr6
 */
public interface Draw extends AutoCloseable {
    /**
     * A font style to draw a string with
     */
    enum FontStyle { PLAIN, BOLD }

    /**
     * Draws a normal line in the given colour
     * @param colour the colour for the line
     * @param x0 the x-coordinate of the start of the line
     * @param y0 the y-coordinate of the start of the line
     * @param x1 the x-coordinate of the end of the line
     * @param y1 the y-coordinate of the end of the line
     */
    default void addLine(int colour, int x0, int y0, int x1, int y1) {
        addLine(colour, null, x0, y0, x1, y1);
    }
    /**
     * Draws a line in the given colour with the given stroke
     * @param colour the colour for the line
     * @param stroke the stroke to draw the line with (uses the default stroke if null)
     * @param x0 the x-coordinate of the start of the line
     * @param y0 the y-coordinate of the start of the line
     * @param x1 the x-coordinate of the end of the line
     * @param y1 the y-coordinate of the end of the line
     */
    void addLine(int colour, DrawStroke stroke, int x0, int y0, int x1, int y1);

    /**
     * Draws a rectangle that may be filled in or outlined.
     * If you want a rectangle without a fill, or without an outline, use zero
     * for the {@code fill} or {@code outline} parameter respectively.
     * @param fill colour to fill in rectangle with
     * @param outline colour to draw outline of rectangle
     * @param x minimum x value of the rectangle
     * @param y minimum y value of the rectangle
     * @param w width of the rectangle
     * @param h height of the rectangle
     */
    default void addRect(int fill, int outline, int x, int y, int w, int h) {
        addRect(fill, outline, null, x, y, w, h);
    }

    /**
     * Draws a rectangle that may be filled in or outlined.
     * If you want a rectangle without a fill, or without an outline, use zero
     * for the {@code fill} or {@code outline} parameter respectively.
     * @param fill colour to fill in rectangle with
     * @param outline colour to draw outline of rectangle
     * @param stroke the stroke to draw the outline (uses the default stroke if null)
     * @param x minimum x value of the rectangle
     * @param y minimum y value of the rectangle
     * @param w width of the rectangle
     * @param h height of the rectangle
     */
    void addRect(int fill, int outline, DrawStroke stroke, int x, int y, int w, int h);

    /**
     * Draws a circle that may be filled in or outlined.
     * If you want a circle without a fill, or without an outline, use zero
     * for the {@code fill} or {@code outline} parameter respectively.
     * @param fill colour to fill in circle with
     * @param outline colour to outline the circle with
     * @param cx the x coordinate of the centre
     * @param cy the y coordinate of the centre
     * @param radius the radius of the circle
     */
    void addCircle(int fill, int outline, int cx, int cy, int radius);

    /**
     * Draws a string
     * @param colour colour to draw the string with
     * @param fontStyle the text style, plain or bold
     * @param string the string to draw
     * @param x the starting x-coordinate of the string
     * @param y the baseline y-coordinate of the string
     */
    void addString(int colour, FontStyle fontStyle, String string, int x, int y);

    /**
     * Get the font height of the given font style. For a {@link java.awt.Graphics}-based implementation,
     * this can use {@code FontMetrics} to find the drawing height of its font. Other implementations may
     * just return a field value or estimate.
     * @param fontStyle the font style to get the font height for
     * @return the (possibly approximated) height of the given font style
     */
    int getFontHeight(FontStyle fontStyle);

    /**
     * Returns a new {@code Draw} instance with the supplied clipping rectangle.
     * @param x minimum x of the clip
     * @param y minimum y of the clip
     * @param w width of the clip
     * @param h height of the clip
     * @return a new {@code Draw} with the given clipping rectangle
     */
    Draw withClip(int x, int y, int w, int h);

    @Override
    void close();
}

