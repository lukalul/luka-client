package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists every recording in the game's subframe folder: watch it, render it, rename it, delete it, open the folder.
 * Opened from a button on the main menu (vanilla and Lunar Client) or with the manager key.
 */
final class ReplayManagerScreen extends GuiScreen {
    private static final int WATCH = 1, RENDER = 2, RENAME = 3, DELETE = 4, FOLDER = 5, DONE = 6;

    /** Duration and frame count are read from the file in the background the first time an entry is selected. */
    private static final Map<String, String> INFO = new HashMap<String, String>();
    private static final Map<String, Long> INFO_STAMP = new HashMap<String, Long>();

    private final GuiScreen parent;
    private final List<File> files = new ArrayList<File>();
    private ReplayList list;
    private int selected = -1;
    private GuiButton watchButton, renderButton, renameButton, deleteButton;
    private String status = "";

    ReplayManagerScreen(GuiScreen parent) {
        this.parent = parent;
    }

    static File dir() {
        return new File(Minecraft.getMinecraft().mcDataDir, "subframe");
    }

    /** Opens the manager over whatever screen is showing now. */
    static void open() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof ReplayManagerScreen || Replay.active != null) {
            return;
        }
        mc.displayGuiScreen(new ReplayManagerScreen(mc.currentScreen));
    }

    private void reload(String keepName) {
        files.clear();
        File[] all = dir().listFiles();
        if (all != null) {
            for (File f : all) {
                if (f.isFile() && f.getName().endsWith(".sfr")) {
                    files.add(f);
                }
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        selected = -1;
        if (keepName != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getName().equals(keepName)) {
                    selected = i;
                }
            }
        }
        if (selected >= 0) {
            loadInfo(files.get(selected));
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(false);
        String keep = selected >= 0 && selected < files.size() ? files.get(selected).getName() : null;
        reload(keep);
        buttonList.clear();
        int bw = Math.min(98, (width - 30) / 5);
        int total = bw * 4 + 12;
        int x = width / 2 - total / 2;
        int y = height - 52;
        watchButton = new GuiButton(WATCH, x, y, bw, 20, "Watch");
        renderButton = new GuiButton(RENDER, x + bw + 4, y, bw, 20, "Render");
        renameButton = new GuiButton(RENAME, x + 2 * (bw + 4), y, bw, 20, "Rename");
        deleteButton = new GuiButton(DELETE, x + 3 * (bw + 4), y, bw, 20, "Delete");
        buttonList.add(watchButton);
        buttonList.add(renderButton);
        buttonList.add(renameButton);
        buttonList.add(deleteButton);
        buttonList.add(new GuiButton(FOLDER, width / 2 - 102, height - 28, 100, 20, "Open folder"));
        buttonList.add(new GuiButton(DONE, width / 2 + 2, height - 28, 100, 20, "Done"));
        list = new ReplayList();
        list.registerScrollButtons(7, 8);
        updateButtons();
    }

    private void updateButtons() {
        boolean has = selected >= 0 && selected < files.size();
        boolean idle = mc.theWorld == null && Replay.active == null;
        watchButton.enabled = has && idle;
        renderButton.enabled = has && idle;
        renameButton.enabled = has;
        deleteButton.enabled = has;
    }

    private File current() {
        return selected >= 0 && selected < files.size() ? files.get(selected) : null;
    }

    private static String baseName(File f) {
        return f.getName().replaceAll("\\.sfr$", "");
    }

    /** The mp4 files and PNG folders a recording's renders produced. */
    private static List<File> videosOf(File f) {
        List<File> out = new ArrayList<File>();
        String base = baseName(f);
        File[] all = f.getParentFile().listFiles();
        if (all != null) {
            for (File v : all) {
                String n = v.getName();
                boolean mp4 = v.isFile() && (n.equals(base + ".mp4") || n.matches(java.util.regex.Pattern.quote(base) + "-\\d+\\.mp4"));
                boolean png = v.isDirectory() && n.matches(java.util.regex.Pattern.quote(base) + "(-\\d+)?-frames");
                if (mp4 || png) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static long sizeOf(File f) {
        if (f.isFile()) {
            return f.length();
        }
        long n = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                n += sizeOf(k);
            }
        }
        return n;
    }

    static String bytes(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.0f KB", b / 1024.0);
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.2f GB", b / 1073741824.0);
    }

    private void loadInfo(final File f) {
        final String key = f.getName();
        Long stamp = INFO_STAMP.get(key);
        if (stamp != null && stamp == f.lastModified()) {
            return;
        }
        synchronized (INFO) {
            INFO_STAMP.put(key, f.lastModified());
            INFO.put(key, "reading...");
        }
        final long modified = f.lastModified();
        Thread t = new Thread("SubFrame replay info") {
            @Override
            public void run() {
                String text;
                try {
                    Recording r = Recording.load(f);
                    double secs = (r.endTime() - r.startTime()) / 1e9;
                    text = String.format(Locale.ROOT, "%.1f s, %d frames recorded (%.0f per second), player %s",
                            secs, r.frameCount, r.frameCount / Math.max(0.001, secs), r.playerName == null ? "?" : r.playerName);
                } catch (Throwable e) {
                    text = "could not read: " + e.getMessage();
                }
                synchronized (INFO) {
                    if (INFO_STAMP.get(key) != null && INFO_STAMP.get(key) == modified) {
                        INFO.put(key, text);
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        File f = current();
        switch (b.id) {
            case WATCH:
                if (f != null && watchButton.enabled) {
                    Replay.returnToManager = true;
                    Replay.startPreview(f);
                    if (Replay.active == null) {
                        Replay.returnToManager = false;
                        status = "Could not start the replay, see the game log.";
                    }
                }
                break;
            case RENDER:
                if (f != null && renderButton.enabled) {
                    Replay.returnToManager = true;
                    Replay.start(f);
                    if (Replay.active == null) {
                        Replay.returnToManager = false;
                        status = "Could not start the render, see the game log.";
                    }
                }
                break;
            case RENAME:
                if (f != null) {
                    mc.displayGuiScreen(new RenameScreen(this, f));
                }
                break;
            case DELETE:
                if (f != null) {
                    mc.displayGuiScreen(new DeleteScreen(this, f));
                }
                break;
            case FOLDER:
                openFolder();
                break;
            case DONE:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void openFolder() {
        File d = dir();
        d.mkdirs();
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(d);
                return;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] cmd = os.contains("win") ? new String[]{"explorer", d.getAbsolutePath()}
                    : os.contains("mac") ? new String[]{"open", d.getAbsolutePath()} : new String[]{"xdg-open", d.getAbsolutePath()};
            new ProcessBuilder(cmd).start();
        } catch (Throwable t) {
            status = "Could not open the folder: " + d.getAbsolutePath();
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (key == Keyboard.KEY_DELETE && current() != null) {
            mc.displayGuiScreen(new DeleteScreen(this, current()));
            return;
        }
        if (key == Keyboard.KEY_R && current() != null) {
            mc.displayGuiScreen(new RenameScreen(this, current()));
            return;
        }
        if (key == Keyboard.KEY_UP && selected > 0) {
            select(selected - 1);
        } else if (key == Keyboard.KEY_DOWN && selected < files.size() - 1) {
            select(selected + 1);
        } else if (key == Keyboard.KEY_RETURN && watchButton.enabled) {
            actionPerformed(watchButton);
        }
    }

    private void select(int i) {
        selected = i;
        File f = current();
        if (f != null) {
            loadInfo(f);
        }
        updateButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        list.handleMouseInput();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateButtons();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "SubFrame Replays", width / 2, 12, 0xFFFFFF);
        String where = dir().getPath();
        drawCenteredString(fontRendererObj, files.size() + " recording" + (files.size() == 1 ? "" : "s") + " in " + where, width / 2, 24, 0xA0A0A0);
        if (files.isEmpty()) {
            drawCenteredString(fontRendererObj, "No recordings yet. Press the start key in a world, then stop it to save one.", width / 2, height / 2 - 20, 0xC0C0C0);
        }
        File f = current();
        int y = height - 98;
        if (f != null) {
            String info;
            synchronized (INFO) {
                info = INFO.get(f.getName());
            }
            List<File> vids = videosOf(f);
            long vs = 0;
            for (File v : vids) {
                vs += sizeOf(v);
            }
            drawString(fontRendererObj, trim(baseName(f) + "  -  " + (info == null ? "" : info), width - 20), 10, y, 0xFFFFFF);
            drawString(fontRendererObj, "Recording " + bytes(f.length()) + ", " + vids.size() + " rendered video" + (vids.size() == 1 ? "" : "s")
                    + (vids.isEmpty() ? "" : " (" + bytes(vs) + ")"), 10, y + 11, 0xA0A0A0);
            RenderRange rr = RenderRange.load(f);
            drawString(fontRendererObj, rr.isSet() ? "Render range " + RenderRange.format(rr.start) + " - " + RenderRange.format(rr.end)
                    + " (set in the viewer with I and O)" : "Renders the whole recording (choose a range in the viewer with I and O)", 10, y + 22, 0xA0A0A0);
        }
        if (mc.theWorld != null) {
            drawCenteredString(fontRendererObj, "Leave the world to watch or render a replay.", width / 2, height - 66, 0xFFAA55);
        } else if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, height - 66, 0xFF7777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trim(String s, int px) {
        FontRenderer fr = fontRendererObj;
        return fr.getStringWidth(s) <= px ? s : fr.trimStringToWidth(s, px - 8) + "...";
    }

    /** Called by the sub-screens when something on disk changed. */
    void changed(String selectName) {
        reload(selectName);
    }

    // ---------------------------------------------------------------------------------------------------------

    private final class ReplayList extends GuiSlot {
        ReplayList() {
            super(ReplayManagerScreen.this.mc, ReplayManagerScreen.this.width, ReplayManagerScreen.this.height, 38, ReplayManagerScreen.this.height - 104, 26);
        }

        @Override
        protected int getSize() {
            return files.size();
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            // GuiSlot reports a click on every mouse event while the button is held; act on a change of selection
            // or on the double-click flag it works out itself
            boolean wasSelected = index == selected;
            select(index);
            if (doubleClick && wasSelected && watchButton.enabled) {
                ReplayManagerScreen.this.actionPerformed(watchButton);
            }
        }

        @Override
        public int getListWidth() {
            return Math.max(200, Math.min(ReplayManagerScreen.this.width - 60, 460));
        }

        @Override
        protected int getScrollBarX() {
            return ReplayManagerScreen.this.width / 2 + getListWidth() / 2 + 6;
        }

        @Override
        protected boolean isSelected(int index) {
            return index == selected;
        }

        @Override
        protected void drawBackground() {
            ReplayManagerScreen.this.drawDefaultBackground();
        }

        @Override
        protected void drawSlot(int index, int x, int y, int h, int mouseX, int mouseY) {
            if (index < 0 || index >= files.size()) {
                return;
            }
            File f = files.get(index);
            FontRenderer fr = ReplayManagerScreen.this.fontRendererObj;
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(f.lastModified()));
            ReplayManagerScreen.this.drawString(fr, trim(baseName(f), getListWidth() - 12), x + 2, y + 2, 0xFFFFFF);
            ReplayManagerScreen.this.drawString(fr, date + "   " + bytes(f.length()) + (videosOf(f).isEmpty() ? "" : "   video rendered"), x + 2, y + 13, 0x909090);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Asks for a new name; renames the recording and the files that belong to it. */
    static final class RenameScreen extends GuiScreen {
        private final ReplayManagerScreen back;
        private final File file;
        private GuiTextField field;
        private String error = "";
        private int typedLogs;

        /** Some clients deliver key presses without the typed character; work it out from the key (plain US layout). */
        private static char charFor(int key) {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            String n = Keyboard.getKeyName(key);
            if (n != null && n.length() == 1) {
                char ch = n.charAt(0);
                return Character.isLetter(ch) ? (shift ? Character.toUpperCase(ch) : Character.toLowerCase(ch)) : ch;
            }
            switch (key) {
                case Keyboard.KEY_SPACE:
                    return ' ';
                case Keyboard.KEY_MINUS:
                    return shift ? '_' : '-';
                case Keyboard.KEY_PERIOD:
                    return '.';
                default:
                    return 0;
            }
        }

        RenameScreen(ReplayManagerScreen back, File file) {
            this.back = back;
            this.file = file;
        }

        @Override
        public void initGui() {
            Keyboard.enableRepeatEvents(true);
            buttonList.clear();
            buttonList.add(new GuiButton(0, width / 2 - 102, height / 2 + 14, 100, 20, "Rename"));
            buttonList.add(new GuiButton(1, width / 2 + 2, height / 2 + 14, 100, 20, "Cancel"));
            field = new GuiTextField(2, fontRendererObj, width / 2 - 100, height / 2 - 12, 200, 20);
            field.setMaxStringLength(80);
            field.setText(baseName(file));
            field.setFocused(true);
            field.setCursorPositionEnd();
        }

        @Override
        public void onGuiClosed() {
            Keyboard.enableRepeatEvents(false);
        }

        @Override
        public void updateScreen() {
            field.updateCursorCounter();
        }

        @Override
        protected void keyTyped(char c, int key) {
            if (key == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(back);
                return;
            }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                doRename();
                return;
            }
            if (typedLogs++ < 6) {
                SubFrameMod.LOG.info("[manager] rename key: char=" + (int) c + " key=" + key + " (" + Keyboard.getKeyName(key) + ")");
            }
            field.textboxKeyTyped(c == 0 ? charFor(key) : c, key);
        }

        @Override
        protected void mouseClicked(int x, int y, int button) {
            super.mouseClicked(x, y, button);
            field.mouseClicked(x, y, button);
        }

        @Override
        protected void actionPerformed(GuiButton b) {
            if (b.id == 0) {
                doRename();
            } else {
                mc.displayGuiScreen(back);
            }
        }

        private void doRename() {
            String name = field.getText().trim();
            if (name.endsWith(".sfr")) {
                name = name.substring(0, name.length() - 4);
            }
            if (name.isEmpty() || !name.matches("[^\\\\/:*?\"<>|]+") || name.startsWith(".") || name.endsWith(" ")) {
                error = "That name has characters a file name cannot have.";
                return;
            }
            String old = baseName(file);
            if (name.equals(old)) {
                mc.displayGuiScreen(back);
                return;
            }
            File dir = file.getParentFile();
            if (new File(dir, name + ".sfr").exists()) {
                error = "A recording with that name already exists.";
                return;
            }
            // the recording first (it is what matters), then everything that carries its name
            File target = new File(dir, name + ".sfr");
            if (!file.renameTo(target)) {
                error = "Could not rename the file (is it open somewhere?).";
                return;
            }
            File live = LiveCapture.liveFile(file);
            if (live.exists()) {
                live.renameTo(LiveCapture.liveFile(target));
            }
            File rangeFile = RenderRange.file(file);
            if (rangeFile.exists()) {
                rangeFile.renameTo(RenderRange.file(target));
            }
            File[] all = dir.listFiles();
            if (all != null) {
                for (File v : all) {
                    String n = v.getName();
                    if (n.equals(old + ".mp4") || n.matches(java.util.regex.Pattern.quote(old) + "-\\d+\\.mp4")
                            || n.matches(java.util.regex.Pattern.quote(old) + "(-\\d+)?-frames")) {
                        File to = new File(dir, name + n.substring(old.length()));
                        if (!to.exists()) {
                            v.renameTo(to);
                        }
                    }
                }
            }
            File logs = new File(dir, "logs");
            for (String kind : new String[]{"record-", "render-"}) {
                File l = new File(logs, kind + old + ".log");
                if (l.exists()) {
                    l.renameTo(new File(logs, kind + name + ".log"));
                }
            }
            back.changed(name + ".sfr");
            mc.displayGuiScreen(back);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Rename recording", width / 2, height / 2 - 40, 0xFFFFFF);
            field.drawTextBox();
            if (!error.isEmpty()) {
                drawCenteredString(fontRendererObj, error, width / 2, height / 2 + 42, 0xFF7777);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Delete confirmation: the recording alone, or together with the videos rendered from it. */
    static final class DeleteScreen extends GuiScreen {
        private final ReplayManagerScreen back;
        private final File file;
        private final List<File> videos;

        DeleteScreen(ReplayManagerScreen back, File file) {
            this.back = back;
            this.file = file;
            this.videos = videosOf(file);
        }

        @Override
        public void initGui() {
            buttonList.clear();
            int y = height / 2 + 10;
            buttonList.add(new GuiButton(0, width / 2 - 100, y, 200, 20, "Delete the recording"));
            GuiButton both = new GuiButton(1, width / 2 - 100, y + 24, 200, 20, "Delete the recording and its videos");
            both.enabled = !videos.isEmpty();
            buttonList.add(both);
            buttonList.add(new GuiButton(2, width / 2 - 100, y + 52, 200, 20, "Cancel"));
        }

        @Override
        protected void keyTyped(char c, int key) {
            if (key == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(back);
            }
        }

        @Override
        protected void actionPerformed(GuiButton b) {
            if (b.id == 2) {
                mc.displayGuiScreen(back);
                return;
            }
            file.delete();
            LiveCapture.liveFile(file).delete();
            RenderRange.file(file).delete();
            new File(new File(file.getParentFile(), "logs"), "record-" + baseName(file) + ".log").delete();
            new File(new File(file.getParentFile(), "logs"), "render-" + baseName(file) + ".log").delete();
            if (b.id == 1) {
                for (File v : videos) {
                    deleteTree(v);
                }
            }
            back.changed(null);
            mc.displayGuiScreen(back);
        }

        private static void deleteTree(File f) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteTree(k);
                }
            }
            f.delete();
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Delete " + baseName(file) + "?", width / 2, height / 2 - 40, 0xFFFFFF);
            long vs = 0;
            for (File v : videos) {
                vs += sizeOf(v);
            }
            drawCenteredString(fontRendererObj, "Recording " + bytes(file.length())
                    + (videos.isEmpty() ? ", no rendered videos" : ", " + videos.size() + " rendered video(s) " + bytes(vs)), width / 2, height / 2 - 24, 0xA0A0A0);
            drawCenteredString(fontRendererObj, "This cannot be undone.", width / 2, height / 2 - 10, 0xFF7777);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
