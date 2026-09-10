package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.AdvancedTransform;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.api.UltraEventElement;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.BoneSample;
import dev.garfield.cinefx.client.api.CinematicBackend.MeshFrame;
import dev.garfield.cinefx.client.api.UltraBackend;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Injects Ultra data into the stable premium glTF frame contract. This deliberately reuses
 * BoneSample/tint/emissive rather than coupling the loader to the Ultra API internals.
 */
final class CineFxUltraGltfEnhancer {
    private CineFxUltraGltfEnhancer() { }

    static ActorFrame actor(ActorFrame frame) {
        ArrayList<BoneSample> bones = new ArrayList<>(frame.boneOverrides());
        for (UltraBackend.ProceduralRigFrame rig : CineFxUltraState.rigsFor(frame.sceneInstanceId(), frame.elementKey())) {
            double rigWeight = clamp01(rig.globalWeight());
            for (UltraBackend.SampledIkGoal goal : rig.goals()) {
                double weight = rigWeight * clamp01(goal.weight());
                if (weight <= 0.0001) continue;
                Vec3d delta = goal.targetWorld().subtract(frame.worldPosition());
                if (delta.lengthSquared() < 1.0e-9) continue;
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
                double pitch = Math.toDegrees(-Math.atan2(delta.y, horizontal));
                yaw += parse(goal.parameters().get("yaw_offset"), 0.0);
                pitch += parse(goal.parameters().get("pitch_offset"), 0.0);
                double roll = parse(goal.parameters().get("roll"), 0.0);

                List<String> chain = boneChain(goal);
                if (chain.isEmpty()) continue;
                for (int i = 0; i < chain.size(); i++) {
                    String bone = chain.get(i);
                    double chainWeight = weight * Math.pow(0.72, chain.size() - 1 - i);
                    Vec3d rotation = switch (goal.mode()) {
                        case FOOT_PLANT -> new Vec3d(pitch * 0.35, yaw * 0.12, roll * 0.2);
                        case TWO_BONE -> new Vec3d(pitch * 0.75, yaw * 0.75, roll);
                        case CCD, FABRIK, TENTACLE -> new Vec3d(pitch, yaw, roll);
                        case LOOK_AT, AIM -> new Vec3d(pitch, yaw, roll);
                    };
                    bones.add(new BoneSample(bone,
                            new AdvancedTransform(Vec3d.ZERO, rotation, new Vec3d(1, 1, 1), Vec3d.ZERO),
                            chainWeight, ComplexElement.BlendMode.ADDITIVE));
                }
                if (!goal.poleBone().isBlank()) {
                    bones.add(new BoneSample(goal.poleBone(),
                            new AdvancedTransform(Vec3d.ZERO, new Vec3d(-pitch * 0.25, yaw * 0.25, roll),
                                    new Vec3d(1, 1, 1), Vec3d.ZERO),
                            weight * 0.55, ComplexElement.BlendMode.ADDITIVE));
                }
            }
        }

        Visual visual = visual(frame.sceneInstanceId(), frame.elementKey(), frame.worldPosition(),
                frame.tintArgb(), frame.opacity(), frame.emissive(), frame.localTick());
        return new ActorFrame(frame.sceneInstanceId(), frame.elementKey(), frame.kind(), frame.resourceId(),
                frame.profileName(), frame.skinTexture(), frame.appearance(), frame.worldMatrix(), frame.worldPosition(),
                frame.lookAt(), visual.tint, visual.opacity, visual.emissive, frame.animations(), List.copyOf(bones),
                frame.morphs(), frame.castShadow(), frame.localTick());
    }

    static MeshFrame mesh(MeshFrame frame) {
        Visual visual = visual(frame.sceneInstanceId(), frame.elementKey(), frame.worldPosition(),
                frame.tintArgb(), frame.opacity(), frame.emissive(), frame.localTick());
        return new MeshFrame(frame.sceneInstanceId(), frame.elementKey(), frame.modelId(), frame.materialId(),
                frame.worldMatrix(), frame.worldPosition(), visual.tint, visual.opacity, visual.emissive,
                frame.castShadow(), frame.parameters(), frame.localTick());
    }

    private static Visual visual(long sceneId, String elementKey, Vec3d position,
                                 int tint, double opacity, double emissive, double localTick) {
        int outTint = tint;
        double outOpacity = opacity;
        double outEmissive = emissive;

        // Frame-level integration of Ultra cinematic lights. The glTF renderer still performs its
        // normal per-vertex PBR approximation; this adds local coloured rig contribution on top.
        double totalLight = 0.0;
        double lr = 0.0, lg = 0.0, lb = 0.0;
        for (UltraBackend.LightRigFrame rig : CineFxUltraState.lights()) {
            for (UltraBackend.SampledRigLight light : rig.lights()) {
                double radius = Math.max(0.001, light.radius());
                double distance = position.distanceTo(light.position());
                if (distance >= radius) continue;
                double attenuation = Math.pow(1.0 - distance / radius, 2.0) * Math.max(0.0, light.intensity());
                if (light.kind() != UltraEventElement.LightKind.POINT) {
                    Vec3d toTarget = position.subtract(light.position());
                    if (toTarget.lengthSquared() > 1.0e-9) {
                        double dot = light.direction().normalize().dotProduct(toTarget.normalize());
                        double outerCos = Math.cos(Math.toRadians(Math.max(0.1, light.outerConeDegrees())));
                        if (dot < outerCos) continue;
                        attenuation *= clamp01((dot - outerCos) / Math.max(0.001, 1.0 - outerCos));
                    }
                }
                int color = light.colorArgb();
                lr += ((color >>> 16) & 255) / 255.0 * attenuation;
                lg += ((color >>> 8) & 255) / 255.0 * attenuation;
                lb += (color & 255) / 255.0 * attenuation;
                totalLight += attenuation;
            }
        }
        if (totalLight > 0.0001) {
            int lightColor = 0xFF000000
                    | ((int)(255 * clamp01(lr / totalLight)) << 16)
                    | ((int)(255 * clamp01(lg / totalLight)) << 8)
                    | (int)(255 * clamp01(lb / totalLight));
            outTint = blendColor(outTint, lightColor, clamp01(totalLight * 0.13));
            outEmissive = Math.min(2.0, outEmissive + totalLight * 0.025);
        }

        for (UltraBackend.MaterialEffectFrame effect : CineFxUltraState.materialsFor(sceneId, elementKey)) {
            double a = clamp01(effect.amount());
            double pulse = 0.5 + 0.5 * Math.sin(localTick * 0.18 * Math.max(0.01, effect.speed()));
            switch (effect.mode()) {
                case DISSOLVE -> {
                    outOpacity *= Math.max(0.0, 1.0 - a * 0.96);
                    outTint = blendColor(outTint, effect.edgeColorArgb(), clamp01(effect.edgeWidth() * 3.0 + a * 0.18));
                    outEmissive += a * effect.edgeWidth() * 1.5;
                }
                case CORRUPTION -> {
                    int corruption = pulse > 0.5 ? 0xFF7D20D8 : 0xFF38D874;
                    outTint = blendColor(outTint, corruption, a * (0.35 + pulse * 0.35));
                    outEmissive += a * pulse * 0.45;
                }
                case FREEZE -> {
                    outTint = blendColor(outTint, 0xFF9FEAFF, a * 0.78);
                    outEmissive += a * 0.15;
                }
                case BURN -> {
                    outTint = blendColor(outTint, pulse > 0.45 ? 0xFFFF6A18 : 0xFF821800, a * 0.75);
                    outEmissive += a * (0.3 + pulse * 0.8);
                }
                case HOLOGRAM -> {
                    outTint = blendColor(outTint, 0xFF51E9FF, a * 0.85);
                    outOpacity *= 1.0 - a * (0.18 + pulse * 0.18);
                    outEmissive += a * 0.7;
                }
                case SCAN -> {
                    double scan = 0.5 + 0.5 * Math.sin(localTick * 0.35 * Math.max(0.01, effect.speed()));
                    outTint = blendColor(outTint, effect.edgeColorArgb(), a * scan * 0.7);
                    outEmissive += a * scan * 0.9;
                }
                case PHASE -> {
                    outOpacity *= 1.0 - a * (0.25 + pulse * 0.35);
                    outTint = blendColor(outTint, effect.edgeColorArgb(), a * 0.45);
                }
                case CLOAK -> outOpacity *= Math.max(0.03, 1.0 - a * 0.94);
            }
        }
        return new Visual(outTint, clamp01(outOpacity), Math.max(0.0, outEmissive));
    }

    private static List<String> boneChain(UltraBackend.SampledIkGoal goal) {
        ArrayList<String> result = new ArrayList<>();
        String chain = goal.chain();
        if (chain != null && !chain.isBlank() && !"default".equalsIgnoreCase(chain)) {
            for (String value : chain.split("[,>/]")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
        }
        if (result.isEmpty() && goal.endBone() != null && !goal.endBone().isBlank()) result.add(goal.endBone());
        return List.copyOf(result);
    }

    private static int blendColor(int a, int b, double amount) {
        amount = clamp01(amount);
        int aa = (a >>> 24) & 255; if (aa == 0) aa = 255;
        int ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        int r = (int)Math.round(ar + (br - ar) * amount);
        int g = (int)Math.round(ag + (bg - ag) * amount);
        int bl = (int)Math.round(ab + (bb - ab) * amount);
        return (aa << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
    }

    private static double parse(String raw, double fallback) {
        if (raw == null) return fallback;
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int clamp255(int value) { return Math.max(0, Math.min(255, value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private record Visual(int tint, double opacity, double emissive) { }
}
