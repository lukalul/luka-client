package dev.customcolors.core;

/** Reads the hex colour values used in pack property files. */
public final class Hex {
    private Hex() {
    }

    /**
     * Returns the RGB value written as one to six hex digits ("FF8800"; a leading '#' is tolerated),
     * or -1 when the text is missing or is not such a value.
     */
    public static int rgb(String text) {
        if (text == null) {
            return -1;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.isEmpty() || s.length() > 6) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < s.length(); i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }
}
