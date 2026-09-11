package dev.garfield.cinefxgui.editor;

import dev.garfield.cinefx.api.AssetBundle;
import dev.garfield.cinefx.api.EventProgramSpec;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mutable Gson-friendly authoring DTOs that compile into CineFX's immutable public API records. */
public final class EventAuthoringModel {
    public enum ConditionType { ALWAYS, AFTER, VARIABLE_EQUALS, VARIABLE_AT_LEAST, ASSETS_READY, ALL, ANY, NOT }
    public enum ActionType { PLAY, STOP, PRELOAD, SET_VARIABLE, ADD_VARIABLE, MARKER }

    public static final class Workspace {
        public int formatVersion = 1;
        public String name = "Untitled Event";
        public Program program = Program.starter();
        public ArrayList<Bundle> bundles = new ArrayList<>();
        public transient String sourceName;
        public transient boolean dirty;

        public static Workspace fresh() {
            Workspace workspace = new Workspace();
            workspace.bundles.add(Bundle.starter());
            return workspace;
        }
    }

    public static final class Program {
        public String id = "cinefx_gui:event";
        public String initialPhase = "intro";
        public ArrayList<Phase> phases = new ArrayList<>();
        public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();

        public static Program starter() {
            Program program = new Program();
            Phase intro = new Phase("intro");
            Transition transition = new Transition();
            transition.targetPhase = "end";
            transition.condition = Condition.after(100.0);
            intro.transitions.add(transition);
            program.phases.add(intro);
            program.phases.add(new Phase("end"));
            return program;
        }

        public EventProgramSpec compile() {
            Identifier identifier = requireId(id, "program id");
            ArrayList<EventProgramSpec.PhaseSpec> compiled = new ArrayList<>();
            for (Phase phase : phases) compiled.add(phase.compile());
            return new EventProgramSpec(identifier, nonBlank(initialPhase, "initialPhase"), compiled, safeMap(metadata));
        }
    }

    public static final class Phase {
        public String id = "phase";
        public ArrayList<Action> onEnter = new ArrayList<>();
        public ArrayList<Action> onExit = new ArrayList<>();
        public ArrayList<Transition> transitions = new ArrayList<>();

        public Phase() { }
        public Phase(String id) { this.id = id; }

        EventProgramSpec.PhaseSpec compile() {
            return new EventProgramSpec.PhaseSpec(nonBlank(id, "phase id"), compileActions(onEnter), compileActions(onExit),
                    transitions.stream().map(Transition::compile).toList());
        }
    }

    public static final class Transition {
        public String targetPhase = "end";
        public int priority;
        public Condition condition = new Condition();
        public ArrayList<Action> actions = new ArrayList<>();

        EventProgramSpec.TransitionSpec compile() {
            return new EventProgramSpec.TransitionSpec(nonBlank(targetPhase, "targetPhase"), priority,
                    condition == null ? new EventProgramSpec.ConditionSpec.Always() : condition.compile(), compileActions(actions));
        }
    }

    public static final class Condition {
        public ConditionType type = ConditionType.ALWAYS;
        public double number;
        public String key = "variable";
        public String value = "value";
        public String id = "cinefx_gui:assets";
        public ArrayList<Condition> children = new ArrayList<>();

        public static Condition after(double ticks) {
            Condition condition = new Condition(); condition.type = ConditionType.AFTER; condition.number = ticks; return condition;
        }

        public EventProgramSpec.ConditionSpec compile() {
            ConditionType resolved = type == null ? ConditionType.ALWAYS : type;
            return switch (resolved) {
                case ALWAYS -> new EventProgramSpec.ConditionSpec.Always();
                case AFTER -> new EventProgramSpec.ConditionSpec.After(Math.max(0.0, finite(number, 0.0)));
                case VARIABLE_EQUALS -> new EventProgramSpec.ConditionSpec.VariableEquals(nonBlank(key, "variable key"), value);
                case VARIABLE_AT_LEAST -> new EventProgramSpec.ConditionSpec.VariableAtLeast(nonBlank(key, "variable key"), finite(number, 0.0));
                case ASSETS_READY -> new EventProgramSpec.ConditionSpec.AssetsReady(requireId(id, "bundle id"));
                case ALL -> new EventProgramSpec.ConditionSpec.All(compileConditions(children));
                case ANY -> new EventProgramSpec.ConditionSpec.Any(compileConditions(children));
                case NOT -> new EventProgramSpec.ConditionSpec.Not(children == null || children.isEmpty()
                        ? new EventProgramSpec.ConditionSpec.Always() : children.getFirst().compile());
            };
        }
    }

    public static final class Action {
        public ActionType type = ActionType.PLAY;
        public String id = "cinefx_gui:scene";
        public String key = "variable";
        public String value = "value";
        public double number;
        public long startOffsetTicks;
        public long seedSalt;
        public LinkedHashMap<String, String> parameters = new LinkedHashMap<>();

        public EventProgramSpec.ActionSpec compile() {
            ActionType resolved = type == null ? ActionType.PLAY : type;
            return switch (resolved) {
                case PLAY -> new EventProgramSpec.ActionSpec.Play(requireId(id, "scene id"), startOffsetTicks, seedSalt, safeMap(parameters));
                case STOP -> new EventProgramSpec.ActionSpec.Stop(requireId(id, "scene id"));
                case PRELOAD -> new EventProgramSpec.ActionSpec.Preload(requireId(id, "bundle id"));
                case SET_VARIABLE -> new EventProgramSpec.ActionSpec.SetVariable(nonBlank(key, "variable key"), value);
                case ADD_VARIABLE -> new EventProgramSpec.ActionSpec.AddVariable(nonBlank(key, "variable key"), finite(number, 0.0));
                case MARKER -> new EventProgramSpec.ActionSpec.Marker(nonBlank(value, "marker name"), safeMap(parameters));
            };
        }
    }

    public static final class Bundle {
        public String id = "cinefx_gui:assets";
        public ArrayList<String> resources = new ArrayList<>();
        public ArrayList<String> logicalAssets = new ArrayList<>();
        public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();

        public static Bundle starter() { return new Bundle(); }

        public AssetBundle compile() {
            return new AssetBundle(requireId(id, "bundle id"), ids(resources, "resource"), ids(logicalAssets, "logical asset"), safeMap(metadata));
        }
    }

    private EventAuthoringModel() { }

    private static List<EventProgramSpec.ActionSpec> compileActions(List<Action> actions) {
        if (actions == null) return List.of();
        return actions.stream().filter(java.util.Objects::nonNull).map(Action::compile).toList();
    }

    private static List<EventProgramSpec.ConditionSpec> compileConditions(List<Condition> conditions) {
        if (conditions == null) return List.of();
        return conditions.stream().filter(java.util.Objects::nonNull).map(Condition::compile).toList();
    }

    private static List<Identifier> ids(List<String> values, String label) {
        if (values == null) return List.of();
        ArrayList<Identifier> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            out.add(requireId(value, label));
        }
        return List.copyOf(out);
    }

    private static Identifier requireId(String value, String label) {
        Identifier id = Identifier.tryParse(value == null ? "" : value.trim());
        if (id == null) throw new IllegalArgumentException("Invalid " + label + ": " + value);
        return id;
    }

    private static String nonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static Map<String, String> safeMap(Map<String, String> values) { return values == null ? Map.of() : Map.copyOf(values); }

    public static String describe(Action action) {
        if (action == null) return "Action";
        return switch (action.type == null ? ActionType.PLAY : action.type) {
            case PLAY -> "Play " + action.id;
            case STOP -> "Stop " + action.id;
            case PRELOAD -> "Preload " + action.id;
            case SET_VARIABLE -> "Set " + action.key + " = " + action.value;
            case ADD_VARIABLE -> "Add " + action.key + " " + signed(action.number);
            case MARKER -> "Marker " + action.value;
        };
    }

    public static String describe(Condition condition) {
        if (condition == null) return "Always";
        return switch (condition.type == null ? ConditionType.ALWAYS : condition.type) {
            case ALWAYS -> "Always";
            case AFTER -> "After " + trimNumber(condition.number) + " ticks";
            case VARIABLE_EQUALS -> condition.key + " == " + condition.value;
            case VARIABLE_AT_LEAST -> condition.key + " >= " + trimNumber(condition.number);
            case ASSETS_READY -> "Assets ready: " + condition.id;
            case ALL -> "All (" + size(condition.children) + ")";
            case ANY -> "Any (" + size(condition.children) + ")";
            case NOT -> "Not " + (condition.children == null || condition.children.isEmpty() ? "Always" : describe(condition.children.getFirst()));
        };
    }

    private static int size(List<?> list) { return list == null ? 0 : list.size(); }
    private static String signed(double v) { return v >= 0 ? "+" + trimNumber(v) : trimNumber(v); }
    private static String trimNumber(double value) { return String.format(Locale.ROOT, Math.rint(value) == value ? "%.0f" : "%.2f", value); }
}
