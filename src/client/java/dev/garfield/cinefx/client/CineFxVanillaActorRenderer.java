package dev.garfield.cinefx.client;

import com.mojang.authlib.GameProfile;
import dev.garfield.cinefx.api.ComplexElement;
import dev.garfield.cinefx.client.api.CinematicBackend.ActorFrame;
import dev.garfield.cinefx.client.api.CinematicBackend.AnimationSample;
import dev.garfield.cinefx.client.api.CinematicBackend.BoneSample;
import dev.garfield.cinefx.client.api.CinematicBackend.SceneRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.registry.Registries;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Built-in render-only fallback for PLAYER and ENTITY actors. The cached actor objects are never
 * added to ClientWorld, never tracked and never networked; they exist only so vanilla can extract
 * its normal model render state, which CineFX then submits through the 1.21.11 command queue.
 */
final class CineFxVanillaActorRenderer {
    private static final double CACHE_GRACE_TICKS = 80.0;
    private static final Map<String, CachedActor> CACHE = new HashMap<>();

    private CineFxVanillaActorRenderer() { }

    static void clear() { CACHE.clear(); }

    static void render(SceneRenderContext context, List<ActorFrame> frames) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || frames.isEmpty()) return;
        EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);

        for (ActorFrame frame : frames) {
            if (frame.kind() == ComplexElement.ActorKind.CUSTOM_MODEL || frame.opacity() <= 0.001) continue;
            String cacheKey = frame.sceneInstanceId() + ":" + frame.elementKey();
            String signature = signature(frame);
            CachedActor cached = CACHE.get(cacheKey);
            if (cached == null || !cached.signature.equals(signature) || cached.entity.getEntityWorld() != client.world) {
                Entity entity = create(client, frame);
                if (entity == null) continue;
                cached = new CachedActor(signature, entity, context.absoluteGameTick());
                CACHE.put(cacheKey, cached);
            }
            cached.lastSeenTick = context.absoluteGameTick();

            Entity entity = cached.entity;
            Vec3d worldPos = frame.worldPosition();
            entity.setPosition(worldPos.x, worldPos.y, worldPos.z);
            entity.setYaw(0.0F);
            entity.setPitch(0.0F);

            EntityRenderState state;
            try {
                state = dispatcher.getAndUpdateRenderState(entity, tickProgress);
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Failed to extract vanilla actor " + frame.elementKey() + ": " + exception.getMessage());
                continue;
            }

            state.age = (float)frame.localTick();
            state.displayName = null;
            state.nameLabelPos = null;
            state.outlineColor = EntityRenderState.NO_OUTLINE;
            state.onFire = false;
            state.shadowPieces.clear();
            state.shadowRadius = 0.0F;
            state.light = frame.emissive() >= 0.999
                    ? 0xF000F0
                    : WorldRenderer.getLightmapCoordinates(client.world, entity.getBlockPos());

            if (state instanceof PlayerEntityRenderState playerState) applyPlayerAppearance(playerState, frame);
            if (state instanceof LivingEntityRenderState living) {
                applyVanillaAnimation(living, frame.animations());
                applyLookAt(living, frame);
                applySimpleBoneOverrides(living, frame.boneOverrides());
            }

            Matrix4f relative = new Matrix4f()
                    .translation((float)-context.cameraPosition().x,
                            (float)-context.cameraPosition().y,
                            (float)-context.cameraPosition().z)
                    .mul(frame.worldMatrix());

            context.matrices().push();
            context.matrices().multiplyPositionMatrix(relative);
            try {
                dispatcher.render(state, context.worldContext().worldState().cameraRenderState,
                        0.0, 0.0, 0.0, context.matrices(), context.commandQueue());
            } catch (RuntimeException exception) {
                System.err.println("[CineFX] Failed to render vanilla actor " + frame.elementKey() + ": " + exception.getMessage());
            } finally {
                context.matrices().pop();
            }
        }

        double now = context.absoluteGameTick();
        CACHE.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > CACHE_GRACE_TICKS);
    }

    private static Entity create(MinecraftClient client, ActorFrame frame) {
        if (frame.kind() == ComplexElement.ActorKind.PLAYER) {
            String name = safeName(frame.profileName(), frame.elementKey());
            UUID uuid = UUID.nameUUIDFromBytes(("CineFX:" + name + ':' + frame.elementKey()).getBytes(StandardCharsets.UTF_8));
            return new RenderOnlyPlayer(client.world, new GameProfile(uuid, name));
        }

        Identifier id = frame.resourceId();
        if (id == null) return null;
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        if (type == null) return null;
        try {
            return type.create(client.world, SpawnReason.EVENT);
        } catch (RuntimeException exception) {
            System.err.println("[CineFX] Could not create render-only actor type " + id + ": " + exception.getMessage());
            return null;
        }
    }

    private static void applyPlayerAppearance(PlayerEntityRenderState state, ActorFrame frame) {
        if (frame.skinTexture() == null) return;
        PlayerSkinType skinType = skinType(frame.appearance());
        state.skinTextures = SkinTextures.create(
                new AssetInfo.TextureAssetInfo(frame.skinTexture()), null, null, skinType);
    }

    private static PlayerSkinType skinType(Map<String, String> appearance) {
        String raw = appearance == null ? null : appearance.get("skin_type");
        if (raw == null && appearance != null) raw = appearance.get("model");
        if (raw == null) return PlayerSkinType.WIDE;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "slim", "alex" -> PlayerSkinType.SLIM;
            default -> PlayerSkinType.WIDE;
        };
    }

    private static void applyVanillaAnimation(LivingEntityRenderState state, List<AnimationSample> animations) {
        state.bodyYaw = 0.0F;
        state.relativeHeadYaw = 0.0F;
        state.pitch = 0.0F;
        state.limbSwingAmplitude = 0.0F;
        state.pose = EntityPose.STANDING;

        for (AnimationSample animation : animations) {
            if (animation.weight() <= 0.001) continue;
            String namespace = animation.clipId().getNamespace();
            String path = animation.clipId().getPath();
            if (!"minecraft".equals(namespace) && !"cinefx".equals(namespace)) continue;
            float weight = (float)Math.max(0.0, Math.min(1.0, animation.weight()));
            float time = (float)animation.timeTicks();
            switch (path) {
                case "walk" -> {
                    state.limbSwingAnimationProgress = time * 0.65F;
                    state.limbSwingAmplitude = Math.max(state.limbSwingAmplitude, weight * 0.75F);
                }
                case "run" -> {
                    state.limbSwingAnimationProgress = time * 1.15F;
                    state.limbSwingAmplitude = Math.max(state.limbSwingAmplitude, weight);
                }
                case "crouch", "sneak" -> {
                    if (weight >= 0.5F) state.pose = EntityPose.CROUCHING;
                }
                case "swim" -> {
                    if (weight >= 0.5F) state.pose = EntityPose.SWIMMING;
                }
                case "hurt" -> state.hurt |= weight >= 0.5F;
                case "shake" -> state.shaking |= weight >= 0.5F;
                case "death" -> state.deathTime = Math.max(state.deathTime, time * weight);
                default -> { }
            }
        }
    }

    private static void applyLookAt(LivingEntityRenderState state, ActorFrame frame) {
        Vec3d target = frame.lookAt();
        if (target == null) return;
        Vec3d origin = frame.worldPosition();
        Vec3d direction = target.subtract(origin);
        if (direction.lengthSquared() < 1.0e-8) return;

        Vec3d forwardPoint = point(frame.worldMatrix(), new Vec3d(0.0, 0.0, 1.0));
        Vec3d forward = forwardPoint.subtract(origin);
        float rootYaw = forward.horizontalLengthSquared() < 1.0e-8
                ? 0.0F
                : (float)Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float targetYaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(direction.y, horizontal));

        state.relativeHeadYaw = MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - rootYaw), -85.0F, 85.0F);
        state.pitch = MathHelper.clamp(targetPitch, -90.0F, 90.0F);
    }

    private static void applySimpleBoneOverrides(LivingEntityRenderState state, List<BoneSample> bones) {
        for (BoneSample bone : bones) {
            double weight = Math.max(0.0, Math.min(1.0, bone.weight()));
            if (weight <= 0.0001) continue;
            String name = bone.bone().toLowerCase(Locale.ROOT);
            Vec3d rotation = bone.transform().rotationDegrees();
            float wx = (float)(rotation.x * weight);
            float wy = (float)(rotation.y * weight);
            if (name.equals("head") || name.equals("neck")) {
                state.pitch = MathHelper.clamp(state.pitch + wx, -90.0F, 90.0F);
                state.relativeHeadYaw = MathHelper.clamp(state.relativeHeadYaw + wy, -90.0F, 90.0F);
            } else if (name.equals("body") || name.equals("root") || name.equals("torso")) {
                state.bodyYaw = MathHelper.wrapDegrees(state.bodyYaw + wy);
            }
        }
    }

    private static Vec3d point(Matrix4fc matrix, Vec3d local) {
        return new Vec3d(
                matrix.m00() * local.x + matrix.m10() * local.y + matrix.m20() * local.z + matrix.m30(),
                matrix.m01() * local.x + matrix.m11() * local.y + matrix.m21() * local.z + matrix.m31(),
                matrix.m02() * local.x + matrix.m12() * local.y + matrix.m22() * local.z + matrix.m32());
    }

    private static String signature(ActorFrame frame) {
        return frame.kind() + "|" + frame.resourceId() + "|" + frame.profileName() + "|" + frame.skinTexture();
    }

    private static String safeName(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested;
        String clean = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (clean.isBlank()) clean = "CineFxActor";
        return clean.length() <= 16 ? clean : clean.substring(0, 16);
    }

    private static final class CachedActor {
        final String signature;
        final Entity entity;
        double lastSeenTick;

        CachedActor(String signature, Entity entity, double lastSeenTick) {
            this.signature = signature;
            this.entity = entity;
            this.lastSeenTick = lastSeenTick;
        }
    }

    private static final class RenderOnlyPlayer extends OtherClientPlayerEntity {
        RenderOnlyPlayer(net.minecraft.client.world.ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public boolean shouldRenderName() { return false; }
    }
}
