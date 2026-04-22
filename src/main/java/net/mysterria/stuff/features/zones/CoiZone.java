package net.mysterria.stuff.features.zones;

import org.bukkit.Location;

import java.util.List;

public class CoiZone {

    private final String name;
    private final String world;
    private final int minY, maxY;
    private final double[] verticesX;
    private final double[] verticesZ;
    private final boolean alwaysNight;
    private final boolean lightningEffects;
    private final int lightningIntervalTicks;
    private final int lightningRadius;

    public CoiZone(String name, String world, int minY, int maxY,
                   List<double[]> vertices,
                   boolean alwaysNight, boolean lightningEffects,
                   int lightningIntervalTicks, int lightningRadius) {
        this.name = name;
        this.world = world;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.verticesX = new double[vertices.size()];
        this.verticesZ = new double[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            this.verticesX[i] = vertices.get(i)[0];
            this.verticesZ[i] = vertices.get(i)[1];
        }
        this.alwaysNight = alwaysNight;
        this.lightningEffects = lightningEffects;
        this.lightningIntervalTicks = lightningIntervalTicks;
        this.lightningRadius = lightningRadius;
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) return false;
        int y = location.getBlockY();
        if (y < minY || y > maxY) return false;
        return pointInPolygon(location.getX(), location.getZ());
    }

    // Ray casting algorithm — counts edge crossings along +X ray from (px, pz)
    private boolean pointInPolygon(double px, double pz) {
        int n = verticesX.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = verticesX[i], zi = verticesZ[i];
            double xj = verticesX[j], zj = verticesZ[j];
            if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    public String getName() { return name; }
    public boolean isAlwaysNight() { return alwaysNight; }
    public boolean isLightningEffects() { return lightningEffects; }
    public int getLightningIntervalTicks() { return lightningIntervalTicks; }
    public int getLightningRadius() { return lightningRadius; }
}
