package dev.cbui.feature.shotchat;

import dev.cbui.Cbui;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.ClickEvent$Action;
import net.minecraft.event.HoverEvent;
import net.minecraft.event.HoverEvent$Action;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * luka's screenshot chat line and its buttons. Each button runs a hidden client command, {@code /lukashot <action>
 * <id>}, that is answered here and never sent to the server. Ids carry a random per-session tag, so a button on a
 * message from an earlier session is recognised as no longer tracked instead of acting on a different file.
 */
public final class ShotLinks {
  public static final String COMMAND = "/lukashot";
  private static final int REMEMBERED = 100;
  private static final long CONFIRM_MS = 10_000L;
  private static final String SESSION = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36), 36);

  private static final Map<String, File> TRACKED = new LinkedHashMap<String, File>();
  private static int counter;
  private static String confirmId;
  private static long confirmUntil;

  private ShotLinks() {}

  // ---- the chat line -------------------------------------------------------------------------------------------

  /** Swaps vanilla's success message for luka's line while the module is on; anything else is returned unchanged. */
  public static IChatComponent replaceMessage(IChatComponent message) {
    File file = savedFile(message);
    if (file == null) return message;
    ShotChatModule module = ShotChatModule.active();
    if (module == null) return message;
    String id = track(file);
    IChatComponent line = new ChatComponentText("");
    line.appendSibling(coloured("Screenshot taken", EnumChatFormatting.GRAY));
    if (module.fileName.get()) {
      ChatComponentText name = new ChatComponentText(file.getName());
      name.getChatStyle().setUnderlined(true)
          .setChatClickEvent(new ClickEvent(ClickEvent$Action.OPEN_FILE, file.getAbsolutePath()))
          .setChatHoverEvent(new HoverEvent(HoverEvent$Action.SHOW_TEXT, new ChatComponentText("Open this picture")));
      line.appendText(" ");
      line.appendSibling(name);
    }
    if (module.viewButton.get()) addButton(line, "View", EnumChatFormatting.GREEN, "Open " + file.getName(), "view", id);
    if (module.browseButton.get()) addButton(line, "Browse", EnumChatFormatting.YELLOW, folder(file).getAbsolutePath(), "browse", id);
    if (module.clipboardButton.get()) addButton(line, "Clipboard", EnumChatFormatting.AQUA, "Put this picture on the clipboard", "copy", id);
    if (module.shareButton.get()) addButton(line, "Share", EnumChatFormatting.LIGHT_PURPLE, "Upload to catbox.moe (public) and copy the link", "share", id);
    if (module.removeButton.get()) addButton(line, "Remove", EnumChatFormatting.RED, "Delete this picture from disk", "remove", id);
    return line;
  }

  /** The file named by vanilla's "screenshot saved" message, or null for any other message. */
  private static File savedFile(IChatComponent message) {
    if (!(message instanceof ChatComponentTranslation)) return null;
    ChatComponentTranslation translated = (ChatComponentTranslation) message;
    if (!"screenshot.success".equals(translated.getKey())) return null;
    for (Object arg : translated.getFormatArgs()) {
      if (!(arg instanceof IChatComponent)) continue;
      ClickEvent click = ((IChatComponent) arg).getChatStyle().getChatClickEvent();
      if (click != null && click.getAction() == ClickEvent$Action.OPEN_FILE && click.getValue() != null) return new File(click.getValue());
    }
    return null;
  }

  private static void addButton(IChatComponent line, String label, EnumChatFormatting colour, String hover, String action, String id) {
    line.appendText(" ");
    line.appendSibling(button(label, colour, hover, action, id));
  }

  private static IChatComponent button(String label, EnumChatFormatting colour, String hover, String action, String id) {
    ChatComponentText b = new ChatComponentText("[" + label + "]");
    b.getChatStyle().setBold(true).setColor(colour)
        .setChatClickEvent(new ClickEvent(ClickEvent$Action.RUN_COMMAND, COMMAND + " " + action + " " + id))
        .setChatHoverEvent(new HoverEvent(HoverEvent$Action.SHOW_TEXT, new ChatComponentText(hover)));
    return b;
  }

  private static IChatComponent coloured(String text, EnumChatFormatting colour) {
    ChatComponentText c = new ChatComponentText(text);
    c.getChatStyle().setColor(colour);
    return c;
  }

  static IChatComponent line(String text, EnumChatFormatting colour) {
    return coloured(text, colour);
  }

  private static synchronized String track(File file) {
    String id = SESSION + "-" + (++counter);
    TRACKED.put(id, file);
    if (TRACKED.size() > REMEMBERED) {
      Iterator<String> oldest = TRACKED.keySet().iterator();
      oldest.next();
      oldest.remove();
    }
    return id;
  }

  private static synchronized File tracked(String id) {
    return TRACKED.get(id);
  }

  private static File folder(File file) {
    File dir = file.getParentFile();
    return dir != null ? dir : new File(Minecraft.getMinecraft().mcDataDir, "screenshots");
  }

  // ---- the hidden command --------------------------------------------------------------------------------------

  /** True for text that is luka's screenshot command (it must then never reach the server). */
  public static boolean isCommand(String text) {
    if (text == null) return false;
    String t = text.trim();
    int n = COMMAND.length();
    return t.regionMatches(true, 0, COMMAND, 0, n) && (t.length() == n || Character.isWhitespace(t.charAt(n)));
  }

  /** Answers a button click. Works while the module is off, for buttons on messages already in chat. */
  public static void run(String text) {
    try {
      String[] parts = text.trim().split("\\s+");
      String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
      String id = parts.length > 2 ? parts[2] : "";
      File file = tracked(id);
      if (file == null) {
        say(line("That screenshot is no longer tracked in this session.", EnumChatFormatting.GOLD));
        return;
      }
      if ("view".equals(action)) view(file);
      else if ("browse".equals(action)) browse(file);
      else if ("copy".equals(action)) copy(file);
      else if ("share".equals(action)) share(file);
      else if ("remove".equals(action)) remove(file, id);
      else say(line("Unknown screenshot action.", EnumChatFormatting.GOLD));
    } catch (Throwable error) {
      Cbui.reportOnce("Screenshot Message Enhancer command", error);
    }
  }

  private static void view(File file) {
    ShotQueue.afterWrites(file, () -> {
      if (!file.isFile()) {
        say(line("Screenshot not found: " + file.getName(), EnumChatFormatting.GOLD));
        return;
      }
      try {
        ShotDesktop.open(file);
      } catch (Throwable error) {
        say(line("Could not open " + file.getName() + ": " + ShotQueue.reason(error), EnumChatFormatting.RED));
      }
    });
  }

  private static void browse(File file) {
    File dir = folder(file);
    ShotQueue.afterWrites(file, () -> {
      try {
        if (!dir.isDirectory()) dir.mkdirs();
        ShotDesktop.open(dir);
      } catch (Throwable error) {
        say(line("Could not open the screenshots folder: " + ShotQueue.reason(error), EnumChatFormatting.RED));
      }
    });
  }

  private static void copy(File file) {
    ShotQueue.afterWrites(file, () -> {
      if (!file.isFile()) {
        say(line("Screenshot not found: " + file.getName(), EnumChatFormatting.GOLD));
        return;
      }
      try {
        ShotDesktop.copyImage(file);
        say(line("Copied " + file.getName() + " to the clipboard.", EnumChatFormatting.GREEN));
      } catch (Throwable error) {
        say(line("Could not copy the picture: " + ShotQueue.reason(error), EnumChatFormatting.RED));
      }
    });
  }

  private static void share(File file) {
    ShotQueue.afterWrites(file, () -> {
      if (!file.isFile()) {
        say(line("Screenshot not found: " + file.getName(), EnumChatFormatting.GOLD));
        return;
      }
      say(line("Uploading " + file.getName() + " to catbox.moe...", EnumChatFormatting.YELLOW));
      String link;
      try {
        link = CatboxUpload.upload(file);
      } catch (Throwable error) {
        say(line("Upload failed: " + ShotQueue.reason(error), EnumChatFormatting.RED));
        return;
      }
      onGameThread(() -> {
        GuiScreen.setClipboardString(link);
        ChatComponentText url = new ChatComponentText(link);
        url.getChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true)
            .setChatClickEvent(new ClickEvent(ClickEvent$Action.OPEN_URL, link));
        IChatComponent done = coloured("Uploaded: ", EnumChatFormatting.AQUA);
        done.appendSibling(url);
        done.appendSibling(coloured(" (link copied)", EnumChatFormatting.GRAY));
        print(done);
      });
    });
  }

  private static void remove(File file, String id) {
    ShotChatModule module = ShotChatModule.find();
    boolean ask = module == null || module.askBeforeRemoving.get();
    long now = System.currentTimeMillis();
    synchronized (ShotLinks.class) {
      if (ask && !(id.equals(confirmId) && now <= confirmUntil)) {
        confirmId = id;
        confirmUntil = now + CONFIRM_MS;
        IChatComponent question = coloured("Delete " + file.getName() + " from disk? ", EnumChatFormatting.GOLD);
        question.appendSibling(button("Confirm", EnumChatFormatting.RED, "Click within 10 seconds to delete it", "remove", id));
        say(question);
        return;
      }
      confirmId = null;
    }
    ShotQueue.afterWrites(file, () -> {
      if (!file.exists()) {
        say(line(file.getName() + " was already gone.", EnumChatFormatting.GOLD));
        return;
      }
      if (file.delete()) say(line("Deleted " + file.getName() + ".", EnumChatFormatting.RED));
      else say(line("Could not delete " + file.getName() + ".", EnumChatFormatting.RED));
    });
  }

  // ---- chat output ---------------------------------------------------------------------------------------------

  /** Prints a line in chat from any thread (posted to the game thread). */
  static void say(IChatComponent message) {
    onGameThread(() -> print(message));
  }

  private static void print(IChatComponent message) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.ingameGUI != null) mc.ingameGUI.getChatGUI().printChatMessage(message);
  }

  private static void onGameThread(Runnable task) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.isCallingFromMinecraftThread()) task.run();
    else mc.addScheduledTask(task);
  }
}
