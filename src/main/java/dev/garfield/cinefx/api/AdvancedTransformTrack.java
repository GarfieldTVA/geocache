package dev.garfield.cinefx.api;

import net.minecraft.util.math.Vec3d;

/** Independent translation / rotation / scale / pivot animation channels. */
public record AdvancedTransformTrack(
        Vec3Track translation,
        Vec3Track rotationDegrees,
        Vec3Track scale,
        Vec3Track pivot
) {
    private static final Vec3d ONE = new Vec3d(1.0, 1.0, 1.0);

    public AdvancedTransformTrack {
        translation = translation == null ? Vec3Track.constant(Vec3d.ZERO) : translation;
        rotationDegrees = rotationDegrees == null ? Vec3Track.angles(Keyframe.at(0.0, Vec3d.ZERO)) : rotationDegrees;
        scale = scale == null ? Vec3Track.constant(ONE) : scale;
        pivot = pivot == null ? Vec3Track.constant(Vec3d.ZERO) : pivot;
    }

    public static AdvancedTransformTrack identity() {
        return new AdvancedTransformTrack(null, null, null, null);
    }

    public static AdvancedTransformTrack from(TransformTrack track) {
        if (track == null) return identity();
        return new AdvancedTransformTrack(
                Vec3Track.of(track.keyframes().stream()
                        .map(key -> new Keyframe<>(key.tick(), key.value().translation(), key.easingToNext()))
                        .toArray(Keyframe[]::new)),
                Vec3Track.angles(track.keyframes().stream()
                        .map(key -> new Keyframe<>(key.tick(), key.value().rotationDegrees(), key.easingToNext()))
                        .toArray(Keyframe[]::new)),
                Vec3Track.of(track.keyframes().stream()
                        .map(key -> new Keyframe<>(key.tick(), key.value().scale(), key.easingToNext()))
                        .toArray(Keyframe[]::new)),
                Vec3Track.constant(Vec3d.ZERO));
    }

    public AdvancedTransform sample(double tick) {
        return new AdvancedTransform(
                translation.sample(tick),
                rotationDegrees.sample(tick),
                scale.sample(tick),
                pivot.sample(tick));
    }
}
