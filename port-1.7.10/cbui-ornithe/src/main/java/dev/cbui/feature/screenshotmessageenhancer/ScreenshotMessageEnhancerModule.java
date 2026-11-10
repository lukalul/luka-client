package dev.cbui.feature.screenshotmessageenhancer;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

public final class ScreenshotMessageEnhancerModule extends FeatureModule {
  public final Setting.Toggle showName = grouped("Screenshot Message", add(new Setting.Toggle("Show Name", false, "Show the screenshot file name in the screenshot message")));
  public final Setting.Toggle showCopyButton = grouped("Screenshot Message", add(new Setting.Toggle("Show Copy Button", true, "Show the copy button in the screenshot message")));
  public final Setting.Toggle showOpenButton = grouped("Screenshot Message", add(new Setting.Toggle("Show Open Button", true, "Show the open button in the screenshot message")));
  public final Setting.Toggle showOpenFolderButton = grouped("Screenshot Message", add(new Setting.Toggle("Show Open Folder Button", true, "Show the open folder button in the screenshot message")));
  public final Setting.Toggle showDeleteButton = grouped("Screenshot Message", add(new Setting.Toggle("Show Delete Button", true, "Show the delete button in the screenshot message")));
  public final Setting.Toggle showUploadButton = grouped("Screenshot Message", add(new Setting.Toggle("Show Upload Button", true, "Show the upload button (uploads to catbox.moe only when you click it)")));
  public final Setting.Toggle confirmDelete = grouped("Screenshot Message", add(new Setting.Toggle("Confirm Delete", true, "Ask for a second click before a screenshot is deleted (luka addition, safer)")));
  public final Setting.Toggle autoCopy = grouped("Misc", add(new Setting.Toggle("Automatically Copy Screenshot", false, "Copy every new screenshot to the clipboard")));
  public final Setting.Toggle compress = grouped("Misc", add(new Setting.Toggle("Screenshot Compression", true, "Compress screenshots without any quality loss")));

  private static volatile ScreenshotMessageEnhancerModule installed;

  public ScreenshotMessageEnhancerModule() {
    super("Screenshot Message Enhancer", true);
    installed = this;
  }

  @Override public String category() { return "UTILITY"; }

  public static ScreenshotMessageEnhancerModule lookup(boolean requireEnabled) {
    ScreenshotMessageEnhancerModule module = installed;
    return module == null || requireEnabled && !module.enabled() ? null : module;
  }
}
