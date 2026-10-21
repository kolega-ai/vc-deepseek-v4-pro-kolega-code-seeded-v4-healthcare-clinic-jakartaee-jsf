package com.oakfield.clinic.svc;

/** Minimal JSON writer for audit values and integration payloads (no reflection, no raw user HTML). */
public final class Json {

    private Json() { }

    public static String obj(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(keyValues[i])).append("\":\"").append(esc(keyValues[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
