package uk.ac.sanger.sccp.stan.service.graph.render;

/**
 * Int rectangular bounds, with no connection to awt
 * @param x minimum x-coordinate
 * @param y minimum y-coordinate
 * @param width width
 * @param height height
 */
public record Bounds(int x, int y, int width, int height) {
    float centreX() {
        return x + width / 2f;
    }
    float centreY() {
        return y + height / 2f;
    }
}
