package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializable/data-driven counterpart to {@link EventProgram}. Unlike EventProgram's lambda-based
 * Condition and Action interfaces, every node here is a record and can therefore be authored by
 * tooling such as CineFX GUI, stored as JSON, reviewed in source control and compiled at runtime.
 */
public record EventProgramSpec(
        Identifier id,
        String initialPhase,
        List<PhaseSpec> phases,
        Map<String, String> metadata
) {
    public EventProgramSpec {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (initialPhase == null || initialPhase.isBlank()) throw new IllegalArgumentException("initialPhase is required");
        phases = phases == null ? List.of() : List.copyOf(phases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (phases.stream().noneMatch(phase -> initialPhase.equals(phase.id()))) {
            throw new IllegalArgumentException("Unknown initial phase: " + initialPhase);
        }
    }

    public EventProgram compile() {
        LinkedHashMap<String, EventProgram.Phase> compiled = new LinkedHashMap<>();
        for (PhaseSpec phase : phases) {
            if (compiled.containsKey(phase.id())) throw new IllegalArgumentException("Duplicate phase id: " + phase.id());
            compiled.put(phase.id(), phase.compile());
        }
        for (PhaseSpec phase : phases) {
            for (TransitionSpec transition : phase.transitions()) {
                if (!compiled.containsKey(transition.targetPhase())) {
                    throw new IllegalArgumentException("Phase " + phase.id() + " targets unknown phase " + transition.targetPhase());
                }
            }
        }
        return new EventProgram(id, initialPhase, compiled, metadata);
    }

    public record PhaseSpec(
            String id,
            List<ActionSpec> onEnter,
            List<ActionSpec> onExit,
            List<TransitionSpec> transitions
    ) {
        public PhaseSpec {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("phase id is required");
            onEnter = onEnter == null ? List.of() : List.copyOf(onEnter);
            onExit = onExit == null ? List.of() : List.copyOf(onExit);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }

        EventProgram.Phase compile() {
            ArrayList<EventProgram.Transition> compiledTransitions = new ArrayList<>();
            for (TransitionSpec transition : transitions) compiledTransitions.add(transition.compile());
            return new EventProgram.Phase(id, compileActions(onEnter), compileActions(onExit), compiledTransitions);
        }
    }

    public record TransitionSpec(
            String targetPhase,
            int priority,
            ConditionSpec condition,
            List<ActionSpec> actions
    ) {
        public TransitionSpec {
            if (targetPhase == null || targetPhase.isBlank()) throw new IllegalArgumentException("targetPhase is required");
            condition = condition == null ? new ConditionSpec.Always() : condition;
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        EventProgram.Transition compile() {
            return new EventProgram.Transition(targetPhase, priority, condition.compile(), compileActions(actions));
        }
    }

    public sealed interface ConditionSpec permits ConditionSpec.Always, ConditionSpec.After,
            ConditionSpec.VariableEquals, ConditionSpec.VariableAtLeast, ConditionSpec.AssetsReady,
            ConditionSpec.All, ConditionSpec.Any, ConditionSpec.Not {
        EventProgram.Condition compile();

        record Always() implements ConditionSpec {
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.always(); }
        }

        record After(double ticks) implements ConditionSpec {
            public After {
                if (!Double.isFinite(ticks) || ticks < 0.0) throw new IllegalArgumentException("ticks must be finite and >= 0");
            }
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.after(ticks); }
        }

        record VariableEquals(String key, String expected) implements ConditionSpec {
            public VariableEquals {
                if (key == null || key.isBlank()) throw new IllegalArgumentException("variable key is required");
            }
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.variableEquals(key, expected); }
        }

        record VariableAtLeast(String key, double minimum) implements ConditionSpec {
            public VariableAtLeast {
                if (key == null || key.isBlank()) throw new IllegalArgumentException("variable key is required");
                if (!Double.isFinite(minimum)) throw new IllegalArgumentException("minimum must be finite");
            }
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.variableAtLeast(key, minimum); }
        }

        record AssetsReady(Identifier bundleId) implements ConditionSpec {
            public AssetsReady {
                if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
            }
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.assetsReady(bundleId); }
        }

        record All(List<ConditionSpec> conditions) implements ConditionSpec {
            public All { conditions = conditions == null ? List.of() : List.copyOf(conditions); }
            @Override public EventProgram.Condition compile() {
                return EventProgram.Condition.all(conditions.stream().map(ConditionSpec::compile).toArray(EventProgram.Condition[]::new));
            }
        }

        record Any(List<ConditionSpec> conditions) implements ConditionSpec {
            public Any { conditions = conditions == null ? List.of() : List.copyOf(conditions); }
            @Override public EventProgram.Condition compile() {
                return EventProgram.Condition.any(conditions.stream().map(ConditionSpec::compile).toArray(EventProgram.Condition[]::new));
            }
        }

        record Not(ConditionSpec condition) implements ConditionSpec {
            public Not { condition = condition == null ? new Always() : condition; }
            @Override public EventProgram.Condition compile() { return EventProgram.Condition.not(condition.compile()); }
        }
    }

    public sealed interface ActionSpec permits ActionSpec.Play, ActionSpec.Stop, ActionSpec.Preload,
            ActionSpec.SetVariable, ActionSpec.AddVariable, ActionSpec.Marker {
        EventProgram.Action compile();

        record Play(Identifier sceneId, long startOffsetTicks, long seedSalt, Map<String, String> variables) implements ActionSpec {
            public Play {
                if (sceneId == null) throw new IllegalArgumentException("sceneId is required");
                variables = variables == null ? Map.of() : Map.copyOf(variables);
            }
            @Override public EventProgram.Action compile() { return EventProgram.Action.play(sceneId, startOffsetTicks, seedSalt, variables); }
        }

        record Stop(Identifier sceneId) implements ActionSpec {
            public Stop { if (sceneId == null) throw new IllegalArgumentException("sceneId is required"); }
            @Override public EventProgram.Action compile() { return EventProgram.Action.stop(sceneId); }
        }

        record Preload(Identifier bundleId) implements ActionSpec {
            public Preload { if (bundleId == null) throw new IllegalArgumentException("bundleId is required"); }
            @Override public EventProgram.Action compile() { return EventProgram.Action.preload(bundleId); }
        }

        record SetVariable(String key, String value) implements ActionSpec {
            public SetVariable { if (key == null || key.isBlank()) throw new IllegalArgumentException("variable key is required"); }
            @Override public EventProgram.Action compile() { return EventProgram.Action.set(key, value); }
        }

        record AddVariable(String key, double delta) implements ActionSpec {
            public AddVariable {
                if (key == null || key.isBlank()) throw new IllegalArgumentException("variable key is required");
                if (!Double.isFinite(delta)) throw new IllegalArgumentException("delta must be finite");
            }
            @Override public EventProgram.Action compile() { return EventProgram.Action.add(key, delta); }
        }

        record Marker(String name, Map<String, String> parameters) implements ActionSpec {
            public Marker {
                if (name == null || name.isBlank()) throw new IllegalArgumentException("marker name is required");
                parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            }
            @Override public EventProgram.Action compile() { return EventProgram.Action.marker(name, parameters); }
        }
    }

    /** Hot-reloadable declarative program registry for tooling and data-driven integrations. */
    public static final class Registry {
        private static final ConcurrentHashMap<Identifier, EventProgramSpec> SPECS = new ConcurrentHashMap<>();
        private Registry() { }

        public static void register(EventProgramSpec spec) {
            if (spec == null) throw new IllegalArgumentException("spec is required");
            EventProgramSpec previous = SPECS.putIfAbsent(spec.id(), spec);
            if (previous != null) throw new IllegalStateException("CineFX event program already registered: " + spec.id());
        }

        public static void replace(EventProgramSpec spec) {
            if (spec == null) throw new IllegalArgumentException("spec is required");
            // Compile before publishing so a bad transition never replaces the last good version.
            spec.compile();
            SPECS.put(spec.id(), spec);
        }

        public static boolean remove(Identifier id) { return id != null && SPECS.remove(id) != null; }
        public static Optional<EventProgramSpec> find(Identifier id) { return Optional.ofNullable(SPECS.get(id)); }
        public static Optional<EventProgram> compiled(Identifier id) { return find(id).map(EventProgramSpec::compile); }
        public static Map<Identifier, EventProgramSpec> snapshot() { return Map.copyOf(SPECS); }
    }

    private static List<EventProgram.Action> compileActions(List<ActionSpec> specs) {
        ArrayList<EventProgram.Action> out = new ArrayList<>(specs.size());
        for (ActionSpec spec : specs) if (spec != null) out.add(spec.compile());
        return List.copyOf(out);
    }

    /** Minimal valid two-phase program that tooling can use as a starting point. */
    public static EventProgramSpec starter(Identifier id) {
        PhaseSpec intro = new PhaseSpec("intro", List.of(), List.of(),
                List.of(new TransitionSpec("end", 0, new ConditionSpec.After(100.0), List.of())));
        PhaseSpec end = new PhaseSpec("end", List.of(), List.of(), List.of());
        return new EventProgramSpec(id, "intro", List.of(intro, end), Map.of());
    }
}
