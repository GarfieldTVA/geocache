package dev.garfield.cinefx.api;

/** Convenience layer for reactive/branching events backed by the Director variable system. */
public final class EventSignals {
    private static final String PREFIX = "signal.";

    private EventSignals() { }

    public static EventProgram.Condition condition(String signal) {
        return EventProgram.Condition.variableEquals(key(signal), "true");
    }

    public static EventProgram.Condition condition(String signal, boolean expected) {
        return EventProgram.Condition.variableEquals(key(signal), Boolean.toString(expected));
    }

    public static void emit(EventDirector.SessionHandle handle, String signal) {
        EventDirector.INSTANCE.setVariable(handle, key(signal), "true");
    }

    public static void clear(EventDirector.SessionHandle handle, String signal) {
        EventDirector.INSTANCE.setVariable(handle, key(signal), null);
    }

    public static void set(EventDirector.SessionHandle handle, String signal, boolean value) {
        EventDirector.INSTANCE.setVariable(handle, key(signal), Boolean.toString(value));
    }

    private static String key(String signal) {
        if (signal == null || signal.isBlank()) throw new IllegalArgumentException("signal is required");
        return signal.startsWith(PREFIX) ? signal : PREFIX + signal;
    }
}
