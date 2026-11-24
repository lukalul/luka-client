package dev.customcolors.core;

/** Which character sits in which cell of vanilla's 16x16 font sheet (the sheet's layout is vanilla data). */
public final class GlyphTable {
    private static final String LOW =
        "ÀÁÂÈÊËÍÓÔÕÚßãõğİ"
        + "ıŒœŞşŴŵžȇ";
    private static final String HIGH =
        "ÇüéâäàåçêëèïîìÄÅ"
        + "ÉæÆôöòûùÿÖÜø£Ø×ƒ"
        + "áíóúñÑªº¿®¬½¼¡«»"
        + "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐"
        + "└┴┬├─┼╞╟╚╔╩╦╠═╬╧"
        + "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
        + "αβΓπΣσμτΦΘΩδ∞∅∈∩"
        + "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";

    private static final char[] CELLS = new char[256];
    private static final short[] CELL_OF = new short[65536];

    static {
        for (int i = 0; i < LOW.length(); i++) {
            CELLS[i] = LOW.charAt(i);
        }
        for (int i = 32; i < 127; i++) {
            CELLS[i] = (char) i;
        }
        for (int i = 0; i < HIGH.length() && 128 + i < 256; i++) {
            CELLS[128 + i] = HIGH.charAt(i);
        }
        java.util.Arrays.fill(CELL_OF, (short) -1);
        for (int i = 0; i < 256; i++) {
            char c = CELLS[i];
            if (c != 0 && CELL_OF[c] < 0) {
                CELL_OF[c] = (short) i;
            }
        }
    }

    private GlyphTable() {
    }

    /** Cell index 0..255 of the character on the sheet, or -1 when the sheet has no cell for it. */
    public static int cellOf(char c) {
        return CELL_OF[c];
    }

    public static char charAt(int cell) {
        return CELLS[cell & 255];
    }

    /** Length of the upper half table; 128 when the table is complete (used by the plain-Java check). */
    public static int highCount() {
        return HIGH.length();
    }
}
