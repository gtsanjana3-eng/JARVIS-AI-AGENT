package com.jarvis.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, dependency-free JSON support.
 *
 * <ul>
 *   <li>{@link #object()} — fluent builder for JSON output.</li>
 *   <li>{@link #parseAny(String)} — full parser: objects become {@code Map}, arrays become
 *       {@code List}, numbers become {@code Double}.</li>
 *   <li>{@link #stringify(Object)} — serialises maps, lists, strings, numbers, booleans and
 *       {@link JsonSerializable} objects.</li>
 * </ul>
 */
public final class Json {

    private Json() {
    }

    /** Implemented by classes that know how to write themselves as JSON. */
    public interface JsonSerializable {
        String toJson();
    }

    // ------------------------------------------------------------------ builder

    public static Builder object() {
        return new Builder();
    }

    public static final class Builder implements JsonSerializable {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        private Builder key(String key) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(key)).append(':');
            return this;
        }

        public Builder put(String key, String value) {
            key(key).sb.append(value == null ? "null" : quote(value));
            return this;
        }

        public Builder put(String key, Number value) {
            key(key).sb.append(value == null ? "null" : formatNumber(value));
            return this;
        }

        public Builder put(String key, boolean value) {
            key(key).sb.append(value);
            return this;
        }

        /** Insert any value (map, list, entity...) — serialised with {@link #stringify}. */
        public Builder putValue(String key, Object value) {
            key(key).sb.append(stringify(value));
            return this;
        }

        /** Insert an already-serialised JSON value (object or array). */
        public Builder putRaw(String key, String json) {
            key(key).sb.append(json == null ? "null" : json);
            return this;
        }

        @Override
        public String toJson() {
            return sb + "}";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    /** Joins already-serialised JSON values into an array. */
    public static String array(Collection<String> jsonValues) {
        return "[" + String.join(",", jsonValues) + "]";
    }

    // ------------------------------------------------------------------ serialiser

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof JsonSerializable js) {
            sb.append(js.toJson());
        } else if (v instanceof String s) {
            sb.append(quote(s));
        } else if (v instanceof Number n) {
            sb.append(formatNumber(n));
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Enum<?> e) {
            sb.append(quote(e.name()));
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            sb.append(quote(v.toString()));
        }
    }

    public static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String formatNumber(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "null";
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(Math.round(d * 100.0) / 100.0);
        }
        return n.toString();
    }

    // ------------------------------------------------------------------ parser

    /** Parses a JSON object. Returns an empty map for blank input. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object v = parseAny(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) v;
    }

    /** Parses any JSON value. */
    public static Object parseAny(String text) {
        Parser p = new Parser(text.trim());
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw new IllegalArgumentException("Unexpected trailing characters in JSON at position " + p.pos);
        }
        return v;
    }

    // ------------------------------------------------------------------ typed helpers

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object v) {
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    public static String asString(Object v, String fallback) {
        return v == null ? fallback : v instanceof String s ? s : stringify(v);
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + (pos - 1));
            }
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWs();
            char c = peek();
            if (c == '{') {
                return readObject();
            }
            if (c == '[') {
                return readArray();
            }
            if (c == '"') {
                return readString();
            }
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                map.put(key, readValue());
                skipWs();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
                }
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
                }
            }
        }

        String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> out.append(e);
                    }
                } else {
                    out.append(c);
                }
            }
        }
    }
}
