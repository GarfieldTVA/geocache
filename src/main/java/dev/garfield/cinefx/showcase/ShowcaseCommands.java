package dev.garfield.cinefx.showcase;

import dev.garfield.cinefx.api.CineFxServer;
import dev.garfield.cinefx.api.EventDirector;
import dev.garfield.cinefx.api.SceneOptions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

/** /cinefxshowcase commands intentionally ship with the library for visual validation and profiling. */
public final class ShowcaseCommands {
    private static boolean initialized;

    private ShowcaseCommands() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        PremiumShowcase.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("cinefxshowcase");
            for (Map.Entry<String, Identifier> entry : ShowcaseScenes.catalog().entrySet()) {
                String name = entry.getKey();
                Identifier sceneId = entry.getValue();
                root.then(CommandManager.literal(name).executes(context -> {
                    var source = context.getSource();
                    long now = source.getWorld().getTime();
                    CineFxServer.playAround(source.getWorld(), sceneId,
                            new SceneOptions(source.getPosition(), now, now ^ sceneId.hashCode(), Map.of("showcase", name)), 256.0);
                    return 1;
                }));
            }
            root.then(CommandManager.literal("premium").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.INSTANCE.start(source.getServer(), source.getWorld(), PremiumShowcase.program(),
                        source.getPosition(), 256.0, now ^ 0xC1FE_900DL, Map.of("showcase", "premium"));
                source.sendFeedback(() -> Text.literal("CineFX premium renderer showcase started."), false);
                return 1;
            }));
            root.then(CommandManager.literal("marathon").executes(context -> {
                var source = context.getSource();
                long now = source.getWorld().getTime();
                EventDirector.INSTANCE.start(source.getServer(), source.getWorld(), ShowcaseScenes.masterProgram(),
                        source.getPosition(), 256.0, now ^ 0x51CE_F00DL, Map.of("showcase", "marathon"));
                return 1;
            }));
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
}
