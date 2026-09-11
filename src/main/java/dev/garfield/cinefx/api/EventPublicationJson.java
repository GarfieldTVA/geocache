package dev.garfield.cinefx.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable tagged JSON wire/storage codec for EventPublication. It never depends on GUI-only DTOs. */
public final class EventPublicationJson {
    public static final int VERSION = 1;
    private EventPublicationJson() { }

    public static String encode(EventPublication publication) {
        if (publication == null) throw new IllegalArgumentException("publication is required");
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("program", encodeProgram(publication.program()));
        JsonArray bundles = new JsonArray();
        for (AssetBundle bundle : publication.bundles()) bundles.add(encodeBundle(bundle));
        root.add("bundles", bundles);
        return root.toString();
    }

    public static EventPublication decode(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("publication JSON is empty");
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("publication JSON must be an object");
        JsonObject root = parsed.getAsJsonObject();
        int version = integer(root, "version", 0);
        if (version != VERSION) throw new IllegalArgumentException("unsupported publication version: " + version);
        EventProgramSpec program = decodeProgram(requiredObject(root, "program"));
        ArrayList<AssetBundle> bundles = new ArrayList<>();
        JsonArray array = array(root, "bundles");
        if (array != null) for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("bundle entry must be an object");
            bundles.add(decodeBundle(element.getAsJsonObject()));
        }
        return new EventPublication(program, bundles);
    }

    private static JsonObject encodeProgram(EventProgramSpec spec) {
        JsonObject out = new JsonObject();
        out.addProperty("id", spec.id().toString());
        out.addProperty("initialPhase", spec.initialPhase());
        out.add("metadata", encodeMap(spec.metadata()));
        JsonArray phases = new JsonArray();
        for (EventProgramSpec.PhaseSpec phase : spec.phases()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", phase.id());
            p.add("onEnter", encodeActions(phase.onEnter()));
            p.add("onExit", encodeActions(phase.onExit()));
            JsonArray transitions = new JsonArray();
            for (EventProgramSpec.TransitionSpec transition : phase.transitions()) {
                JsonObject t = new JsonObject();
                t.addProperty("targetPhase", transition.targetPhase());
                t.addProperty("priority", transition.priority());
                t.add("condition", encodeCondition(transition.condition()));
                t.add("actions", encodeActions(transition.actions()));
                transitions.add(t);
            }
            p.add("transitions", transitions);
            phases.add(p);
        }
        out.add("phases", phases);
        return out;
    }

    private static EventProgramSpec decodeProgram(JsonObject in) {
        Identifier id = identifier(string(in, "id", null), "program id");
        String initial = requiredString(in, "initialPhase");
        ArrayList<EventProgramSpec.PhaseSpec> phases = new ArrayList<>();
        JsonArray phaseArray = requiredArray(in, "phases");
        for (JsonElement element : phaseArray) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("phase entry must be an object");
            JsonObject p = element.getAsJsonObject();
            String phaseId = requiredString(p, "id");
            List<EventProgramSpec.ActionSpec> onEnter = decodeActions(array(p, "onEnter"));
            List<EventProgramSpec.ActionSpec> onExit = decodeActions(array(p, "onExit"));
            ArrayList<EventProgramSpec.TransitionSpec> transitions = new ArrayList<>();
            JsonArray transitionArray = array(p, "transitions");
            if (transitionArray != null) for (JsonElement raw : transitionArray) {
                if (!raw.isJsonObject()) throw new IllegalArgumentException("transition entry must be an object");
                JsonObject t = raw.getAsJsonObject();
                transitions.add(new EventProgramSpec.TransitionSpec(
                        requiredString(t, "targetPhase"),
                        integer(t, "priority", 0),
                        t.has("condition") && t.get("condition").isJsonObject()
                                ? decodeCondition(t.getAsJsonObject("condition"))
                                : new EventProgramSpec.ConditionSpec.Always(),
                        decodeActions(array(t, "actions"))));
            }
            phases.add(new EventProgramSpec.PhaseSpec(phaseId, onEnter, onExit, transitions));
        }
        return new EventProgramSpec(id, initial, phases, decodeMap(object(in, "metadata")));
    }

    private static JsonArray encodeActions(List<EventProgramSpec.ActionSpec> actions) {
        JsonArray out = new JsonArray();
        if (actions != null) for (EventProgramSpec.ActionSpec action : actions) if (action != null) out.add(encodeAction(action));
        return out;
    }

    private static JsonObject encodeAction(EventProgramSpec.ActionSpec action) {
        JsonObject out = new JsonObject();
        if (action instanceof EventProgramSpec.ActionSpec.Play value) {
            out.addProperty("type", "play"); out.addProperty("sceneId", value.sceneId().toString());
            out.addProperty("startOffsetTicks", value.startOffsetTicks()); out.addProperty("seedSalt", value.seedSalt());
            out.add("variables", encodeMap(value.variables()));
        } else if (action instanceof EventProgramSpec.ActionSpec.Stop value) {
            out.addProperty("type", "stop"); out.addProperty("sceneId", value.sceneId().toString());
        } else if (action instanceof EventProgramSpec.ActionSpec.Preload value) {
            out.addProperty("type", "preload"); out.addProperty("bundleId", value.bundleId().toString());
        } else if (action instanceof EventProgramSpec.ActionSpec.SetVariable value) {
            out.addProperty("type", "setVariable"); out.addProperty("key", value.key()); out.addProperty("value", value.value());
        } else if (action instanceof EventProgramSpec.ActionSpec.AddVariable value) {
            out.addProperty("type", "addVariable"); out.addProperty("key", value.key()); out.addProperty("delta", value.delta());
        } else if (action instanceof EventProgramSpec.ActionSpec.Marker value) {
            out.addProperty("type", "marker"); out.addProperty("name", value.name()); out.add("parameters", encodeMap(value.parameters()));
        } else throw new IllegalArgumentException("unsupported action: " + action.getClass().getName());
        return out;
    }

    private static List<EventProgramSpec.ActionSpec> decodeActions(JsonArray array) {
        if (array == null) return List.of();
        ArrayList<EventProgramSpec.ActionSpec> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("action entry must be an object");
            JsonObject in = element.getAsJsonObject();
            String type = requiredString(in, "type");
            out.add(switch (type) {
                case "play" -> new EventProgramSpec.ActionSpec.Play(
                        identifier(requiredString(in, "sceneId"), "sceneId"),
                        longValue(in, "startOffsetTicks", 0L), longValue(in, "seedSalt", 0L),
                        decodeMap(object(in, "variables")));
                case "stop" -> new EventProgramSpec.ActionSpec.Stop(identifier(requiredString(in, "sceneId"), "sceneId"));
                case "preload" -> new EventProgramSpec.ActionSpec.Preload(identifier(requiredString(in, "bundleId"), "bundleId"));
                case "setVariable" -> new EventProgramSpec.ActionSpec.SetVariable(requiredString(in, "key"), string(in, "value", null));
                case "addVariable" -> new EventProgramSpec.ActionSpec.AddVariable(requiredString(in, "key"), number(in, "delta", 0.0));
                case "marker" -> new EventProgramSpec.ActionSpec.Marker(requiredString(in, "name"), decodeMap(object(in, "parameters")));
                default -> throw new IllegalArgumentException("unknown action type: " + type);
            });
        }
        return List.copyOf(out);
    }

    private static JsonObject encodeCondition(EventProgramSpec.ConditionSpec condition) {
        JsonObject out = new JsonObject();
        if (condition == null || condition instanceof EventProgramSpec.ConditionSpec.Always) {
            out.addProperty("type", "always");
        } else if (condition instanceof EventProgramSpec.ConditionSpec.After value) {
            out.addProperty("type", "after"); out.addProperty("ticks", value.ticks());
        } else if (condition instanceof EventProgramSpec.ConditionSpec.VariableEquals value) {
            out.addProperty("type", "variableEquals"); out.addProperty("key", value.key()); out.addProperty("expected", value.expected());
        } else if (condition instanceof EventProgramSpec.ConditionSpec.VariableAtLeast value) {
            out.addProperty("type", "variableAtLeast"); out.addProperty("key", value.key()); out.addProperty("minimum", value.minimum());
        } else if (condition instanceof EventProgramSpec.ConditionSpec.AssetsReady value) {
            out.addProperty("type", "assetsReady"); out.addProperty("bundleId", value.bundleId().toString());
        } else if (condition instanceof EventProgramSpec.ConditionSpec.All value) {
            out.addProperty("type", "all"); out.add("conditions", encodeConditions(value.conditions()));
        } else if (condition instanceof EventProgramSpec.ConditionSpec.Any value) {
            out.addProperty("type", "any"); out.add("conditions", encodeConditions(value.conditions()));
        } else if (condition instanceof EventProgramSpec.ConditionSpec.Not value) {
            out.addProperty("type", "not"); out.add("condition", encodeCondition(value.condition()));
        } else throw new IllegalArgumentException("unsupported condition: " + condition.getClass().getName());
        return out;
    }

    private static JsonArray encodeConditions(List<EventProgramSpec.ConditionSpec> conditions) {
        JsonArray out = new JsonArray();
        if (conditions != null) for (EventProgramSpec.ConditionSpec condition : conditions) out.add(encodeCondition(condition));
        return out;
    }

    private static EventProgramSpec.ConditionSpec decodeCondition(JsonObject in) {
        String type = string(in, "type", "always");
        return switch (type) {
            case "always" -> new EventProgramSpec.ConditionSpec.Always();
            case "after" -> new EventProgramSpec.ConditionSpec.After(number(in, "ticks", 0.0));
            case "variableEquals" -> new EventProgramSpec.ConditionSpec.VariableEquals(requiredString(in, "key"), string(in, "expected", null));
            case "variableAtLeast" -> new EventProgramSpec.ConditionSpec.VariableAtLeast(requiredString(in, "key"), number(in, "minimum", 0.0));
            case "assetsReady" -> new EventProgramSpec.ConditionSpec.AssetsReady(identifier(requiredString(in, "bundleId"), "bundleId"));
            case "all" -> new EventProgramSpec.ConditionSpec.All(decodeConditions(array(in, "conditions")));
            case "any" -> new EventProgramSpec.ConditionSpec.Any(decodeConditions(array(in, "conditions")));
            case "not" -> new EventProgramSpec.ConditionSpec.Not(in.has("condition") && in.get("condition").isJsonObject()
                    ? decodeCondition(in.getAsJsonObject("condition")) : new EventProgramSpec.ConditionSpec.Always());
            default -> throw new IllegalArgumentException("unknown condition type: " + type);
        };
    }

    private static List<EventProgramSpec.ConditionSpec> decodeConditions(JsonArray array) {
        if (array == null) return List.of();
        ArrayList<EventProgramSpec.ConditionSpec> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("condition entry must be an object");
            out.add(decodeCondition(element.getAsJsonObject()));
        }
        return List.copyOf(out);
    }

    private static JsonObject encodeBundle(AssetBundle bundle) {
        JsonObject out = new JsonObject();
        out.addProperty("id", bundle.id().toString());
        JsonArray resources = new JsonArray(); for (Identifier id : bundle.resources()) resources.add(id.toString()); out.add("resources", resources);
        JsonArray logical = new JsonArray(); for (Identifier id : bundle.logicalAssets()) logical.add(id.toString()); out.add("logicalAssets", logical);
        out.add("metadata", encodeMap(bundle.metadata()));
        return out;
    }

    private static AssetBundle decodeBundle(JsonObject in) {
        Identifier id = identifier(requiredString(in, "id"), "bundle id");
        ArrayList<Identifier> resources = decodeIdentifiers(array(in, "resources"), "resource");
        ArrayList<Identifier> logical = decodeIdentifiers(array(in, "logicalAssets"), "logical asset");
        return new AssetBundle(id, resources, logical, decodeMap(object(in, "metadata")));
    }

    private static ArrayList<Identifier> decodeIdentifiers(JsonArray array, String label) {
        ArrayList<Identifier> out = new ArrayList<>();
        if (array != null) for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException(label + " id must be a string");
            out.add(identifier(element.getAsString(), label));
        }
        return out;
    }

    private static JsonObject encodeMap(Map<String, String> map) {
        JsonObject out = new JsonObject();
        if (map != null) for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey() != null) out.add(entry.getKey(), entry.getValue() == null ? new JsonPrimitive("") : new JsonPrimitive(entry.getValue()));
        }
        return out;
    }

    private static Map<String, String> decodeMap(JsonObject object) {
        if (object == null) return Map.of();
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("map value for " + entry.getKey() + " must be primitive");
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(out);
    }

    private static Identifier identifier(String value, String label) {
        Identifier id = Identifier.tryParse(value == null ? "" : value);
        if (id == null) throw new IllegalArgumentException("invalid " + label + ": " + value);
        return id;
    }
    private static JsonObject requiredObject(JsonObject object, String key) { JsonObject value = object(object, key); if (value == null) throw new IllegalArgumentException(key + " object is required"); return value; }
    private static JsonArray requiredArray(JsonObject object, String key) { JsonArray value = array(object, key); if (value == null) throw new IllegalArgumentException(key + " array is required"); return value; }
    private static JsonObject object(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null; }
    private static JsonArray array(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : null; }
    private static String requiredString(JsonObject object, String key) { String value = string(object, key, null); if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required"); return value; }
    private static String string(JsonObject object, String key, String fallback) { try { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static int integer(JsonObject object, String key, int fallback) { try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static long longValue(JsonObject object, String key, long fallback) { try { return object.has(key) ? object.get(key).getAsLong() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static double number(JsonObject object, String key, double fallback) { try { return object.has(key) ? object.get(key).getAsDouble() : fallback; } catch (RuntimeException ignored) { return fallback; } }
}
