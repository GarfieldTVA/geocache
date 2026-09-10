package dev.garfield.cinefx.client.api;

/** Fully sampled grade values for one frame. 1 is neutral saturation/contrast; 0 is neutral exposure/vignette. */
public record GradeFrame(int tintArgb, double saturation, double exposure, double contrast, double vignette) {
    public static final GradeFrame NEUTRAL = new GradeFrame(0x00000000, 1.0, 0.0, 1.0, 0.0);
}
