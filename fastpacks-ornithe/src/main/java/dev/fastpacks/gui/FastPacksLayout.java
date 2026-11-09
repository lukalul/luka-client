package dev.fastpacks.gui;

/** Pure GUI-coordinate layout used by the screen and its headless self-check. */
public final class FastPacksLayout {
    public static final int ROW_HEIGHT = 40;
    public final Rect title, search, nav, breadcrumb, availableHeading, selectedHeading;
    public final Rect availableList, selectedList, move, background, previews, done, refresh, cancel;

    private FastPacksLayout(int width, int height) {
        int m = 6, gap = 4, mid = width / 2;
        title = new Rect(m, 3, width - m, 13);
        int searchWidth = Math.min(220, Math.max(80, width - 2 * m));
        search = new Rect((width - searchWidth) / 2, 16, (width + searchWidth) / 2, 32);
        nav = new Rect(m, 35, width - m, 55);
        breadcrumb = new Rect(m, 58, width - m, 68);
        availableHeading = new Rect(m, 71, mid - gap, 81);
        selectedHeading = new Rect(mid + gap, 71, width - m, 81);
        int listBottom = Math.max(83, height - 53);
        availableList = new Rect(m, 83, mid - gap, listBottom);
        selectedList = new Rect(mid + gap, 83, width - m, listBottom);

        int upperY = height - 48, lowerY = height - 24;
        int third = Math.max(1, (width - 2 * m - 2 * gap) / 3);
        move = new Rect(m, upperY, m + third, upperY + 20);
        background = new Rect(m + third + gap, upperY, m + 2 * third + gap, upperY + 20);
        previews = new Rect(m + 2 * third + 2 * gap, upperY, width - m, upperY + 20);
        done = new Rect(m, lowerY, m + third, lowerY + 20);
        refresh = new Rect(m + third + gap, lowerY, m + 2 * third + gap, lowerY + 20);
        cancel = new Rect(m + 2 * third + 2 * gap, lowerY, width - m, lowerY + 20);
    }

    public static FastPacksLayout calculate(int width, int height) { return new FastPacksLayout(width, height); }

    public static final class Rect {
        public final int left, top, right, bottom;
        Rect(int left, int top, int right, int bottom) { this.left=left; this.top=top; this.right=right; this.bottom=bottom; }
        public int width() { return Math.max(0, right-left); }
        public int height() { return Math.max(0, bottom-top); }
        public boolean contains(int x,int y) { return x>=left&&x<right&&y>=top&&y<bottom; }
        public boolean overlaps(Rect r) { return left<r.right&&right>r.left&&top<r.bottom&&bottom>r.top; }
    }
}
