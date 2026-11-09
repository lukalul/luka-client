package dev.fastpacks.gui;

import java.util.regex.Pattern;

public final class LegacyText {
    private static final Pattern ARCHIVE_SUFFIX = Pattern.compile("(?i)\\.(zip|jar)$");
    private static final Pattern LEADING_BANGS = Pattern.compile("^\\s*!+\\s*");
    private static final Pattern BLANK_RUNS = Pattern.compile("[ \\t\\x0B\\f]+");

    private LegacyText() {}
    public interface Width { int width(String text); }

    public static String cleanName(String value) {
        String s = ARCHIVE_SUFFIX.matcher(clean(value)).replaceFirst("");
        return LEADING_BANGS.matcher(s).replaceFirst("");
    }

    public static String clean(String value) {
        if (value == null) return "";
        String s = value.replace("\u00c2\u00a7", "\u00a7").replace('\u00a0', ' ').trim();
        return BLANK_RUNS.matcher(s).replaceAll(" ");
    }

    public static String clip(String text, int pixels, Width metric) {
        String s = clean(text); if (pixels <= 0) return "";
        if (metric.width(s) <= pixels) return s;
        String dots = "..."; int room = pixels - metric.width(dots);
        if (room <= 0) return "";
        int end = s.length();
        while (end > 0 && metric.width(s.substring(0, end)) > room) end--;
        while (end > 0 && s.charAt(end-1) == '\u00a7') end--;
        return s.substring(0, end).trim() + dots;
    }
}
