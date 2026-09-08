package dev.garfield.cinefx.api;

/** Adaptive client quality tiers used by CineFX fallbacks and exposed to renderer backends. */
public enum QualityTier {
    SAFE(0.20, 20, 0.10, 0.35, false, false),
    LOW(0.40, 40, 0.25, 0.50, false, false),
    MEDIUM(0.65, 80, 0.50, 0.70, true, false),
    HIGH(0.85, 140, 0.75, 0.90, true, true),
    ULTRA(1.00, 240, 1.00, 1.00, true, true);

    private final double particleScale;
    private final int maxCrowdActors;
    private final double instanceFraction;
    private final double trailResolutionScale;
    private final boolean volumes;
    private final boolean geometryShadows;

    QualityTier(double particleScale, int maxCrowdActors, double instanceFraction,
                double trailResolutionScale, boolean volumes, boolean geometryShadows) {
        this.particleScale = particleScale;
        this.maxCrowdActors = maxCrowdActors;
        this.instanceFraction = instanceFraction;
        this.trailResolutionScale = trailResolutionScale;
        this.volumes = volumes;
        this.geometryShadows = geometryShadows;
    }

    public double particleScale() { return particleScale; }
    public int maxCrowdActors() { return maxCrowdActors; }
    public double instanceFraction() { return instanceFraction; }
    public double trailResolutionScale() { return trailResolutionScale; }
    public boolean volumes() { return volumes; }
    public boolean geometryShadows() { return geometryShadows; }

    public QualityTier lower() {
        int index = ordinal();
        return index <= 0 ? this : values()[index - 1];
    }

    public QualityTier higher() {
        int index = ordinal();
        return index >= values().length - 1 ? this : values()[index + 1];
    }
}
