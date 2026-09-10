package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.SceneOptions;

import java.util.Locale;
import java.util.Map;

/** Allocation-light-enough string templating intended for labels/timers, not a scripting language. */
final class TemplateEngine {
    private TemplateEngine() { }

    static String render(String template, double sceneTick, double durationTicks, SceneOptions options) {
        if (template.indexOf('{') < 0) return template;
        double remainingTicks = Math.max(0.0, durationTicks - sceneTick);
        String out = template
                .replace("{ticks}", Long.toString(Math.max(0L, Math.round(remainingTicks))))
                .replace("{elapsed_ticks}", Long.toString(Math.max(0L, Math.round(sceneTick))))
                .replace("{time}", String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0))
                .replace("{elapsed}", String.format(Locale.ROOT, "%.1f", Math.max(0.0, sceneTick) / 20.0))
                .replace("{time:mm:ss}", clock(remainingTicks))
                .replace("{seed}", Long.toString(options.seed()));
        for (Map.Entry<String, String> entry : options.variables().entrySet()) {
            out = out.replace("{var:" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private static String clock(double ticks) {
        long totalSeconds = Math.max(0L, (long)Math.ceil(ticks / 20.0));
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }
}
