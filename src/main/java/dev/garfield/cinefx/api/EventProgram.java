package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative event flow definition. A Program is intentionally data-light:
 * phases, conditions and actions orchestrate registered CineFX scenes instead of streaming
 * thousands of per-frame changes from the server.
 */
public record EventProgram(
        Identifier id,
        String initialPhase,
        Map<String, Phase> phases,
        Map<String, String> metadata
) {
    public EventProgram {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (initialPhase == null || initialPhase.isBlank()) throw new IllegalArgumentException("initialPhase is required");
        phases = phases == null ? Map.of() : Map.copyOf(phases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (!phases.containsKey(initialPhase)) throw new IllegalArgumentException("Unknown initial phase: " + initialPhase);
    }

    public record Phase(
            String id,
            List<Action> onEnter,
            List<Action> onExit,
            List<Transition> transitions
    ) {
        public Phase {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("phase id is required");
            onEnter = onEnter == null ? List.of() : List.copyOf(onEnter);
            onExit = onExit == null ? List.of() : List.copyOf(onExit);
            ArrayList<Transition> sorted = new ArrayList<>(transitions == null ? List.of() : transitions);
            sorted.sort(Comparator.comparingInt(Transition::priority).reversed());
            transitions = List.copyOf(sorted);
        }
    }

    public record Transition(String targetPhase, int priority, Condition condition, List<Action> actions) {
        public Transition {
            if (targetPhase == null || targetPhase.isBlank()) throw new IllegalArgumentException("targetPhase is required");
            condition = condition == null ? Condition.always() : condition;
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    @FunctionalInterface
    public interface Condition {
        boolean test(Context context);

        static Condition always() { return context -> true; }
        static Condition after(double ticks) { return context -> context.phaseTicks() >= ticks; }
        static Condition variableEquals(String key, String expected) {
            return context -> expected == null ? context.variable(key) == null : expected.equals(context.variable(key));
        }
        static Condition variableAtLeast(String key, double minimum) {
            return context -> {
                try { return Double.parseDouble(context.variable(key)) >= minimum; }
                catch (Exception ignored) { return false; }
            };
        }
        static Condition assetsReady(Identifier bundleId) { return context -> context.assetsReady(bundleId); }
        static Condition all(Condition... values) {
            return context -> {
                for (Condition value : values) if (value != null && !value.test(context)) return false;
                return true;
            };
        }
        static Condition any(Condition... values) {
            return context -> {
                for (Condition value : values) if (value != null && value.test(context)) return true;
                return false;
            };
        }
        static Condition not(Condition value) { return context -> value == null || !value.test(context); }
    }

    @FunctionalInterface
    public interface Action {
        void run(Context context);

        static Action play(Identifier sceneId) { return play(sceneId, 0L, 0L); }
        static Action play(Identifier sceneId, long startOffsetTicks, long seedSalt) {
            return context -> context.playScene(sceneId, startOffsetTicks, seedSalt, Map.of());
        }
        static Action play(Identifier sceneId, long startOffsetTicks, long seedSalt, Map<String, String> variables) {
            Map<String, String> safe = variables == null ? Map.of() : Map.copyOf(variables);
            return context -> context.playScene(sceneId, startOffsetTicks, seedSalt, safe);
        }
        static Action stop(Identifier sceneId) { return context -> context.stopScene(sceneId); }
        static Action preload(Identifier bundleId) { return context -> context.requestPreload(bundleId); }
        static Action set(String key, String value) { return context -> context.setVariable(key, value); }
        static Action add(String key, double delta) {
            return context -> {
                double current;
                try { current = Double.parseDouble(context.variable(key)); }
                catch (Exception ignored) { current = 0.0; }
                context.setVariable(key, Double.toString(current + delta));
            };
        }
        static Action marker(String name, Map<String, String> parameters) {
            Map<String, String> safe = parameters == null ? Map.of() : Map.copyOf(parameters);
            return context -> context.marker(name, safe);
        }
    }

    /** Runtime surface intentionally small enough for mods/plugins to implement custom actions safely. */
    public interface Context {
        Identifier programId();
        long sessionId();
        String phaseId();
        double phaseTicks();
        long worldTime();
        String variable(String key);
        Map<String, String> variables();
        void setVariable(String key, String value);
        void playScene(Identifier sceneId, long startOffsetTicks, long seedSalt, Map<String, String> variables);
        void stopScene(Identifier sceneId);
        void requestPreload(Identifier bundleId);
        boolean assetsReady(Identifier bundleId);
        void marker(String name, Map<String, String> parameters);
    }

    public static Builder builder(Identifier id, String initialPhase) { return new Builder(id, initialPhase); }

    public static final class Builder {
        private final Identifier id;
        private final String initialPhase;
        private final LinkedHashMap<String, Phase> phases = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();

        private Builder(Identifier id, String initialPhase) {
            this.id = id;
            this.initialPhase = initialPhase;
        }

        public Builder phase(Phase phase) {
            if (phase == null) throw new IllegalArgumentException("phase is required");
            phases.put(phase.id(), phase);
            return this;
        }

        public Builder meta(String key, String value) {
            metadata.put(key, value);
            return this;
        }

        public EventProgram build() { return new EventProgram(id, initialPhase, phases, metadata); }
    }
}
