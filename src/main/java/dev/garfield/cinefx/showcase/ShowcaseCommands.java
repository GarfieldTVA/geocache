package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxServer;
import dev.garfield.cinefx.api.EventDirector;
import dev.garfield.cinefx.api.SceneOptions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** /cinefxshowcase commands intentionally ship with the library for visual validation and profiling. */
public final class ShowcaseCommands {
    private static boolean initialized;

    private ShowcaseCommands() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        PremiumShowcase.register();
        UltraShowcase.register();
        MegaEventPresets.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("cinefxshowcase");
            for (Map.Entry<String, Identifier> entry : ShowcaseScenes.catalog().entrySet()) {
                String name = entry.getKey();
                Identifier sceneId = entry.getValue();
                root.then(CommandManager.literal(name).executes(context -> {
                    var source = context.getSource();
                    long now = source.getWorld().getTime();
                    Vec3d anchor = safeAnchor(source);
                    CineFxServer.playAround(source.getWorld(), sceneId,
                            new SceneOptions(anchor, now, now ^ sceneId.hashCode(), Map.of("showcase", name)), 320.0);
                    return 1;
                }));
            }
            root.then(CommandManager.literal("premium").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.INSTANCE.start(source.getServer(), source.getWorld(), PremiumShowcase.program(),
                        safeAnchor(source), 320.0, now ^ 0xC1FE_900DL, Map.of("showcase", "premium"));
                source.sendFeedback(() -> Text.literal("CineFX premium renderer showcase started."), false);
                return 1;
            }));
            root.then(CommandManager.literal("ultra").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.SessionHandle handle = EventDirector.INSTANCE.start(source.getServer(), source.getWorld(),
                        UltraShowcase.program(), safeAnchor(source), 320.0, now ^ 0xC1FE_771AL,
                        Map.of("showcase", "ultra"));
                source.sendFeedback(() -> Text.literal("CineFX Ultra showcase started. session=" + handle.sessionId()
                        + " (set signal.overload=true to branch into destruction)"), false);
                return 1;
            }));
            root.then(CommandManager.literal("ultra_overload").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.SessionHandle handle = EventDirector.INSTANCE.start(source.getServer(), source.getWorld(),
                        UltraShowcase.program(), safeAnchor(source), 320.0, now ^ 0x0A11_C0DEL,
                        Map.of("showcase", "ultra", "signal.overload", "true"));
                source.sendFeedback(() -> Text.literal("CineFX Ultra OVERLOAD showcase started. session=" + handle.sessionId()), false);
                return 1;
            }));
            root.then(CommandManager.literal("marathon").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.INSTANCE.start(source.getServer(), source.getWorld(), ShowcaseScenes.masterProgram(),
                        safeAnchor(source), 320.0, now ^ 0x51CE_F00DL, Map.of("showcase", "marathon"));
                return 1;
            }));
            for (Map.Entry<String, Identifier> entry : MegaEventPresets.catalog().entrySet()) {
                String name = entry.getKey();
                Identifier sceneId = entry.getValue();
                root.then(CommandManager.literal(name).executes(context -> {
                    var source = context.getSource();
                    long now = source.getWorld().getTime();
                    AnchorResult placement = adaptiveAnchor(source, 30, 6);
                    Map<String, String> variables = new HashMap<>();
                    variables.put("showcase", name);
                    variables.put("mega_event", "true");
                    variables.put("terrain_adaptive", "true");
                    variables.put("terrain_roughness", String.format(java.util.Locale.ROOT, "%.2f", placement.roughness()));
                    variables.put("anchor_shift", String.format(java.util.Locale.ROOT, "%.1f", placement.horizontalShift()));
                    CineFxServer.playAround(source.getWorld(), sceneId,
                            new SceneOptions(placement.position(), now + 2, now ^ sceneId.hashCode(), Map.copyOf(variables)), 420.0);
                    source.sendFeedback(() -> Text.literal("CineFX narrative event '" + name
                            + "' started. terrain roughness=" + String.format(java.util.Locale.ROOT, "%.1f", placement.roughness())
                            + " anchor shift=" + String.format(java.util.Locale.ROOT, "%.1f", placement.horizontalShift()) + " blocks"), false);
                    return 1;
                }));
            }
            root.then(CommandManager.literal("verify").executes(context -> {
                ShowcaseValidator.Report report = ShowcaseValidator.validateAll();
                var source = context.getSource();
                source.sendFeedback(() -> Text.literal("CineFX showcase QA: " + (report.ok() ? "OK" : "FAILED")
                        + " scenes=" + report.scenes() + " elements=" + report.elements()
                        + " warnings=" + report.warnings().size() + " errors=" + report.errors().size()), false);
                for (String warning : report.warnings()) {
                    source.sendFeedback(() -> Text.literal("[CineFX warning] " + warning), false);
                }
                for (String error : report.errors()) source.sendError(Text.literal("[CineFX error] " + error));
                return report.ok() ? 1 : 0;
            }));
            dispatcher.register(root);
        });
    }

    /** Finds a nearby patch whose sampled surface has the lowest height variance and usable headroom. */
    private static AnchorResult adaptiveAnchor(ServerCommandSource source, int radius, int step) {
        Vec3d raw = source.getPosition();
        ServerWorld world = source.getWorld();
        int originX = (int)Math.floor(raw.x);
        int originZ = (int)Math.floor(raw.z);
        int aroundY = (int)Math.floor(raw.y);
        Candidate best = null;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int cx = originX + dx;
                int cz = originZ + dz;
                Candidate candidate = evaluatePatch(world, cx, cz, aroundY, raw, dx, dz);
                if (candidate == null) continue;
                if (best == null || candidate.score < best.score) best = candidate;
            }
        }

        if (best == null) {
            Vec3d fallback = safeAnchor(source);
            return new AnchorResult(fallback, 99.0, horizontalDistance(fallback, raw));
        }
        Vec3d position = new Vec3d(best.x + 0.5, best.y + 1.02, best.z + 0.5);
        return new AnchorResult(position, best.roughness, horizontalDistance(position, raw));
    }

    private static Candidate evaluatePatch(ServerWorld world, int x, int z, int aroundY, Vec3d raw, int dx, int dz) {
        int[][] sampleOffsets = {
                {0, 0}, {5, 0}, {-5, 0}, {0, 5}, {0, -5},
                {4, 4}, {-4, 4}, {4, -4}, {-4, -4}
        };
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0.0;
        int center = Integer.MIN_VALUE;
        for (int i = 0; i < sampleOffsets.length; i++) {
            int sy = surfaceY(world, x + sampleOffsets[i][0], z + sampleOffsets[i][1], aroundY);
            if (sy == Integer.MIN_VALUE) return null;
            if (i == 0) center = sy;
            min = Math.min(min, sy);
            max = Math.max(max, sy);
            sum += sy;
        }
        double mean = sum / sampleOffsets.length;
        double variance = 0.0;
        for (int[] offset : sampleOffsets) {
            int sy = surfaceY(world, x + offset[0], z + offset[1], aroundY);
            double d = sy - mean;
            variance += d * d;
        }
        variance /= sampleOffsets.length;
        double roughness = (max - min) + Math.sqrt(variance);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double verticalPenalty = Math.abs((center + 1.0) - raw.y) * 0.18;
        double score = roughness * 8.0 + distance * 0.11 + verticalPenalty;
        return new Candidate(x, center, z, roughness, score);
    }

    /** Returns the top dry walkable-looking surface near aroundY, requiring two air blocks above. */
    private static int surfaceY(ServerWorld world, int x, int z, int aroundY) {
        int top = aroundY + 44;
        int bottom = aroundY - 84;
        for (int y = top; y >= bottom; y--) {
            BlockPos floor = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(floor);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (!world.getBlockState(floor.up()).isAir()) continue;
            if (!world.getBlockState(floor.up(2)).isAir()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Fallback for ordinary showcases: scan downward under the command source. */
    private static Vec3d safeAnchor(ServerCommandSource source) {
        Vec3d raw = source.getPosition();
        int x = (int)Math.floor(raw.x);
        int z = (int)Math.floor(raw.z);
        int startY = (int)Math.floor(raw.y + 0.35);
        for (int dy = 0; dy <= 72; dy++) {
            int y = startY - dy;
            BlockPos floor = new BlockPos(x, y, z);
            BlockState state = source.getWorld().getBlockState(floor);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            BlockPos above = floor.up();
            if (!source.getWorld().getBlockState(above).isAir()) continue;
            return new Vec3d(raw.x, y + 1.02, raw.z);
        }
        return raw.add(0.0, 0.08, 0.0);
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private record Candidate(int x, int y, int z, double roughness, double score) { }
    private record AnchorResult(Vec3d position, double roughness, double horizontalShift) { }
}
