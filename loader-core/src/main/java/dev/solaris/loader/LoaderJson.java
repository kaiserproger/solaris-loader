package dev.solaris.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Closed-schema JSON reading shared by the Loader manifest, artifact index and
 * wire-3 view codecs. Every field is named explicitly, every string, number and
 * collection is bounded, and unknown or out-of-bound values fail closed.
 */
final class LoaderJson {
    /** Identifier bound shared by screens, widgets, fields, markers and ids. */
    static final int MAX_IDENTIFIER_BYTES = 128;

    private LoaderJson() {
    }

    static JsonObject document(byte[] payload, int maxBytes, String name) {
        if (payload == null || payload.length == 0 || payload.length > maxBytes) {
            throw new IllegalArgumentException(
                    name + " size is outside 1..=" + maxBytes);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(payload))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException(name + " is not valid UTF-8", error);
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonParseException error) {
            throw new IllegalArgumentException(name + " is malformed", error);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    static void rejectUnknown(JsonObject object, Set<String> allowed, String name) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(name + " contains unknown field " + field);
            }
        }
    }

    /** Required string of at most {@code maxBytes} UTF-8 bytes; may be empty. */
    static String text(JsonObject object, String field, int maxBytes, String name) {
        return bounded(string(require(object, field, name), name), maxBytes, name);
    }

    /** A string already read from a document, bounded to {@code maxBytes} UTF-8 bytes. */
    static String bounded(String value, int maxBytes, String name) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(
                    name + " must contain at most " + maxBytes + " bytes");
        }
        return value;
    }

    /** Required string of 1..{@code maxBytes} UTF-8 bytes. */
    static String nonEmpty(JsonObject object, String field, int maxBytes, String name) {
        String text = string(require(object, field, name), name);
        int length = text.getBytes(StandardCharsets.UTF_8).length;
        if (length == 0 || length > maxBytes) {
            throw new IllegalArgumentException(
                    name + " must contain 1..=" + maxBytes + " bytes");
        }
        return text;
    }

    /** Required identifier of 1..{@value #MAX_IDENTIFIER_BYTES} UTF-8 bytes. */
    static String identifier(JsonObject object, String field, String name) {
        return nonEmpty(object, field, MAX_IDENTIFIER_BYTES, name);
    }

    static Optional<String> optionalIdentifier(
            JsonObject object,
            String field,
            String name) {
        return object.has(field) ? Optional.of(identifier(object, field, name)) : Optional.empty();
    }

    static Optional<String> optionalText(
            JsonObject object,
            String field,
            int maxBytes,
            String name) {
        return object.has(field) ? Optional.of(text(object, field, maxBytes, name)) : Optional.empty();
    }

    static boolean flag(JsonObject object, String field, String name) {
        JsonElement value = require(object, field, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    static Optional<Boolean> optionalFlag(JsonObject object, String field, String name) {
        return object.has(field) ? Optional.of(flag(object, field, name)) : Optional.empty();
    }

    static long integer(JsonObject object, String field, long min, long max, String name) {
        long value = integer(require(object, field, name), name);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " is outside " + min + "..=" + max);
        }
        return value;
    }

    static double number(JsonObject object, String field, String name) {
        JsonElement value = require(object, field, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a JSON number");
        }
        // Gson parses NaN/Infinity literals in lenient mode; the contract is finite only.
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return number;
    }

    static double number(JsonObject object, String field, double min, double max, String name) {
        double value = number(object, field, name);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " is outside " + min + "..=" + max);
        }
        return value;
    }

    static Optional<Double> optionalNumber(JsonObject object, String field, String name) {
        return object.has(field) ? Optional.of(number(object, field, name)) : Optional.empty();
    }

    static Optional<Double> optionalNumber(
            JsonObject object,
            String field,
            double min,
            double max,
            String name) {
        return object.has(field) ? Optional.of(number(object, field, min, max, name)) : Optional.empty();
    }

    static Optional<LoaderFormation> optionalFormation(
            JsonObject object,
            String field,
            String name) {
        if (!object.has(field)) {
            return Optional.empty();
        }
        return Optional.of(LoaderFormation.fromWireName(
                string(require(object, field, name), name)));
    }

    /** Required JSON array with at most {@code maxEntries} entries. */
    static List<JsonElement> array(JsonObject object, String field, int maxEntries, String name) {
        JsonElement value = require(object, field, name);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be a JSON array");
        }
        return entries(value.getAsJsonArray(), maxEntries, name);
    }

    /** JSON array with at most {@code maxEntries} entries; an absent field is empty. */
    static List<JsonElement> optionalArray(
            JsonObject object,
            String field,
            int maxEntries,
            String name) {
        if (!object.has(field)) {
            return List.of();
        }
        return array(object, field, maxEntries, name);
    }

    private static List<JsonElement> entries(
            JsonArray array,
            int maxEntries,
            String name) {
        if (array.size() > maxEntries) {
            throw new IllegalArgumentException(
                    name + " exceeds " + maxEntries + " entries");
        }
        List<JsonElement> entries = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            entries.add(entry);
        }
        return entries;
    }

    static JsonObject object(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    static JsonElement require(JsonObject object, String field, String name) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value;
    }

    static String string(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static long integer(JsonElement value, String name) {
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        BigDecimal decimal = primitive.getAsBigDecimal();
        if (decimal.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(name + " is outside the supported range", error);
        }
    }
}
