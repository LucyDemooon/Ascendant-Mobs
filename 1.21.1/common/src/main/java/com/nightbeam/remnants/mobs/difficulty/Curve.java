package com.nightbeam.remnants.mobs.difficulty;

import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.config.Json;
import java.util.Locale;

import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * How one difficulty factor turns its input into levels: {@code t = input / per}, then linear {@code t}, sqrt
 * {@code √t}, log {@code ln(1 + t)} or power {@code t^exponent}, capped at {@code max} (0 or less: no cap).
 */
public record Curve(boolean enabled, Shape shape, double per, double exponent, double max) {
    public enum Shape { LINEAR, SQRT, LOG, POWER }

    public static final Curve OFF = new Curve(false, Shape.LINEAR, 1, 1, 0);

    public double levels(double input) {
        if (!enabled || input <= 0) {
            return 0;
        }
        double t = input / per;
        double value = switch (shape) {
            case LINEAR -> t;
            case SQRT -> Math.sqrt(t);
            case LOG -> Math.log1p(t);
            case POWER -> Math.pow(t, exponent);
        };
        return max > 0 ? Math.min(value, max) : value;
    }

    /** Reads a curve; missing keys come from {@code fallback}. */
    public static Curve read(JsonObject o, Curve fallback) {
        Shape shape = switch (Json.str(o, "curve", fallback.shape.name()).toLowerCase(Locale.ROOT)) {
            case "sqrt" -> Shape.SQRT;
            case "log" -> Shape.LOG;
            case "power" -> Shape.POWER;
            case "linear" -> Shape.LINEAR;
            default -> fallback.shape;
        };
        return new Curve(Json.bool(o, "enabled", fallback.enabled), shape, Math.max(1e-6, Json.num(o, "per", fallback.per)),
                Math.max(0.01, Json.num(o, "exponent", fallback.exponent)), Json.num(o, "max", fallback.max));
    }

    public JsonObject json() {
        return obj("enabled", enabled, "curve", shape.name().toLowerCase(Locale.ROOT), "per", per, "exponent", exponent, "max", max);
    }
}
