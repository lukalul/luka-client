package dev.envoy.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader for server configuration payloads. */
final class SimpleJson {
    private final String text;
    private int at;

    private SimpleJson(String text) { this.text = text; }

    static Object parse(String text) {
        SimpleJson p = new SimpleJson(text);
        Object value = p.value();
        p.space();
        if (p.at != text.length()) throw new IllegalArgumentException("Trailing JSON at " + p.at);
        return value;
    }

    private Object value() {
        space();
        if (at >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = text.charAt(at);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') { word("true"); return Boolean.TRUE; }
        if (c == 'f') { word("false"); return Boolean.FALSE; }
        if (c == 'n') { word("null"); return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        at++;
        space();
        if (take('}')) return out;
        do {
            space();
            String key = string();
            space();
            need(':');
            out.put(key, value());
            space();
        } while (take(','));
        need('}');
        return out;
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<Object>();
        at++;
        space();
        if (take(']')) return out;
        do { out.add(value()); space(); } while (take(','));
        need(']');
        return out;
    }

    private String string() {
        need('"');
        StringBuilder out = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (at >= text.length()) break;
            c = text.charAt(at++);
            if (c == '"' || c == '\\' || c == '/') out.append(c);
            else if (c == 'b') out.append('\b');
            else if (c == 'f') out.append('\f');
            else if (c == 'n') out.append('\n');
            else if (c == 'r') out.append('\r');
            else if (c == 't') out.append('\t');
            else if (c == 'u' && at + 4 <= text.length()) {
                out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                at += 4;
            } else throw new IllegalArgumentException("Bad escape at " + at);
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private Number number() {
        int start = at;
        while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
        String n = text.substring(start, at);
        try { return n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0
                ? Long.valueOf(n) : Double.valueOf(n); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Bad number at " + start, e); }
    }

    private void word(String word) {
        if (!text.regionMatches(at, word, 0, word.length())) throw new IllegalArgumentException("Bad JSON at " + at);
        at += word.length();
    }
    private void space() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
    private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private void need(char c) { if (!take(c)) throw new IllegalArgumentException("Expected " + c + " at " + at); }
}
