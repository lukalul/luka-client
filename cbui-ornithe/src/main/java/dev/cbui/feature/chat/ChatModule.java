package dev.cbui.feature.chat;

import dev.cbui.chat.Mentions;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.util.regex.Pattern;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

/** How chat looks and behaves. With the mod off, chat is plain vanilla. */
public final class ChatModule extends FeatureModule {
  public final Setting.Toggle noBackground = grouped("Look", add(new Setting.Toggle("No chat background", false, "Remove the dark box behind chat lines")));
  public final Setting.Toggle shadow = grouped("Look", add(new Setting.Toggle("Chat text shadow", true, "Drop shadow on chat text")));
  public final Setting.Toggle smooth = grouped("Look", add(new Setting.Toggle("Smooth chat", true, "New messages slide in instead of jumping into place")));
  public final Setting.Toggle highlight = grouped("Mentions", add(new Setting.Toggle("Highlight your name", true, "Your name turns yellow when someone writes it in chat")));
  public final Setting.Toggle mentionSound = grouped("Mentions", add(new Setting.Toggle("Mention sound", true, "Play a sound when someone writes your name in chat")));
  public final Setting.Toggle stack = grouped("Messages", add(new Setting.Toggle("Stack repeated messages", true, "Identical messages in a row become one line with a count")));
  public final Setting.Toggle timestamps = grouped("Messages", add(new Setting.Toggle("Timestamps", false, "A grey [HH:mm] in front of every new message")));
  public final Setting.Toggle longHistory = grouped("Messages", add(new Setting.Toggle("Long chat history", true, "Remember 1000 lines instead of 100")));
  public final Setting.Toggle copy = grouped("Messages", add(new Setting.Toggle("Ctrl+click copies", true, "Hold Ctrl and click a message to copy it")));

  private String patternName;
  private Pattern pattern;

  public ChatModule() {
    super("Chat", true);
  }

  @Override public String category() { return "UTILITY"; }

  /** The module while it is switched on, or null when chat should stay vanilla. */
  public static ChatModule active() {
    ChatModule module = Features.get(ChatModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** Applies the mention options to a new message and returns the message to show. */
  public IChatComponent onMessage(IChatComponent message) {
    if (!highlight.get() && !mentionSound.get()) return message;
    Pattern name = namePattern();
    if (name == null || !Mentions.mentioned(message.getUnformattedText(), name)) return message;
    if (mentionSound.get()) mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.5f));
    return highlight.get() ? mark(message, name) : message;
  }

  private Pattern namePattern() {
    String me = mc.getSession().getUsername();
    if (me == null || me.length() < 3) return null;
    if (!me.equals(patternName)) {
      patternName = me;
      pattern = Mentions.pattern(me);
    }
    return pattern;
  }

  private static IChatComponent mark(IChatComponent component, Pattern name) {
    IChatComponent out;
    String base = component.getChatStyle().getFormattingCode();
    if (component instanceof ChatComponentText) {
      out = new ChatComponentText(Mentions.highlight(component.getUnformattedTextForChat(), name, base));
    } else if (component instanceof ChatComponentTranslation) {
      ChatComponentTranslation translation = (ChatComponentTranslation) component;
      Object[] args = translation.getFormatArgs().clone();
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof IChatComponent) args[i] = mark((IChatComponent) args[i], name);
        else if (args[i] instanceof String) args[i] = Mentions.highlight((String) args[i], name, base);
      }
      out = new ChatComponentTranslation(translation.getKey(), args);
    } else {
      return component;
    }
    out.setChatStyle(component.getChatStyle().createShallowCopy());
    for (IChatComponent sibling : component.getSiblings()) out.appendSibling(mark(sibling, name));
    return out;
  }
}
