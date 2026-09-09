package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxServer;
import dev.garfield.cinefx.api.EventDirector;
import dev.garfield.cinefx.api.SceneOptions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
                    Vec3d anchor = safeAnchor(source);
                    CineFxServer.playAround(source.getWorld(), sceneId,
                            new SceneOptions(anchor, now + 2, now ^ sceneId.hashCode(),
                                    Map.of("showcase", name, "mega_event", "true")), 384.0);
                    source.sendFeedback(() -> Text.literal("CineFX mega event '" + name + "' started at a ground-safe anchor."), false);
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

    /**
     * Command-source Y is not guaranteed to be the top surface (spectator, flight, slopes, water,
     * command blocks). Scan downward from the source and anchor just above the first floor so large
     * showcase geometry never starts half a block underground.
     */
    private static Vec3d safeAnchor(ServerCommandSource source) {
        Vec3d raw = source.getPosition();
        int x = (int)Math.floor(raw.x);
        int z = (int)Math.floor(raw.z);
        int startY = (int)Math.floor(raw.y + 0.35);
        for (int dy = 0; dy <= 64; dy++) {
            int y = startY - dy;
            BlockPos floor = new BlockPos(x, y, z);
            if (source.getWorld().getBlockState(floor).isAir()) continue;
            BlockPos above = floor.up();
            if (!source.getWorld().getBlockState(above).isAir()) continue;
            return new Vec3d(raw.x, y + 1.02, raw.z);
        }
        return raw.add(0.0, 0.08, 0.0);
    }
}
