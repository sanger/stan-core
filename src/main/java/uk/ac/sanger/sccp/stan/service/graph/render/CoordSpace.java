package uk.ac.sanger.sccp.stan.service.graph.render;

/**
 * @author dr6
 */
public class CoordSpace {
    private float zoom = 1;
    private float minZoom = 0.1f;
    private float maxZoom = 10;
    private float worldOffsetX;
    private float worldOffsetY;
    private int renderOffsetX;
    private int renderOffsetY;

    public float getZoom() {
        return this.zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.min(maxZoom, Math.max(minZoom, zoom));
    }

    public float getMinZoom() {
        return this.minZoom;
    }

    public void setMinZoom(float minZoom) {
        this.minZoom = minZoom;
    }

    public float getMaxZoom() {
        return this.maxZoom;
    }

    public void setMaxZoom(float maxZoom) {
        this.maxZoom = maxZoom;
    }

    public float getWorldOffsetX() {
        return this.worldOffsetX;
    }

    public void setWorldOffsetX(float worldOffsetX) {
        this.worldOffsetX = worldOffsetX;
    }

    public float getWorldOffsetY() {
        return this.worldOffsetY;
    }

    public void setWorldOffsetY(float worldOffsetY) {
        this.worldOffsetY = worldOffsetY;
    }

    public int getRenderOffsetX() {
        return this.renderOffsetX;
    }

    public void setRenderOffsetX(int renderOffsetX) {
        this.renderOffsetX = renderOffsetX;
    }

    public int getRenderOffsetY() {
        return this.renderOffsetY;
    }

    public void setRenderOffsetY(int renderOffsetY) {
        this.renderOffsetY = renderOffsetY;
    }

    public int toRenderX(float worldX) {
        return (int) (renderOffsetX + (worldX - worldOffsetX) * zoom);
    }

    public int toRenderY(float worldY) {
        return (int) (renderOffsetY + (worldY - worldOffsetY) * zoom);
    }

    public float toWorldX(int rx) {
        return worldOffsetX + (rx - renderOffsetX) / zoom;
    }

    public float toWorldY(int ry) {
        return worldOffsetY + (ry - renderOffsetY) / zoom;
    }

    public int toRenderScale(float worldScale) {
        return (int) (worldScale * zoom);
    }

    public Bounds toRender(Bounds worldRect) {
        return new Bounds(toRenderX(worldRect.x()), toRenderY(worldRect.y()),
                toRenderScale(worldRect.width()), toRenderScale(worldRect.height()));
    }
}
