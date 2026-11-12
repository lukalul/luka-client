package dev.cbui.config;

import com.google.gson.*;
import dev.cbui.Cbui;
import dev.cbui.hud.*;
import java.io.*;

public final class ConfigStore {
  private final File file;

  public ConfigStore(File file) {
    this.file = file;
  }

  public void load(HudManager manager) {
    if (!file.isFile()) return;
    try {
      apply(manager, JsonFiles.read(file));
    } catch (Throwable error) {
      Cbui.reportOnce("config load", error);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void readSetting(JsonObject values, Setting setting) {
    if (!values.has(setting.name())) return;
    JsonElement value = values.get(setting.name());
    if (setting instanceof Setting.Toggle) setting.set(value.getAsBoolean());
    else if (setting instanceof Setting.Slider) setting.set(value.getAsFloat());
    else if (setting instanceof Setting.Color) setting.set(value.getAsInt());
    else if (setting instanceof Setting.Choice) setting.set(value.getAsString());
    else if (setting instanceof Setting.Text) setting.set(value.getAsString());
  }

  public void save(HudManager manager) {
    try {
      File parent = file.getParentFile();
      if (!parent.isDirectory() && !parent.mkdirs())
        throw new IOException("Could not create " + parent);
      JsonFiles.writeAtomic(file, snapshot(manager));
    } catch (Throwable error) {
      Cbui.reportOnce("config save", error);
    }
  }

  public JsonObject snapshot(HudManager manager) {
    JsonObject modules = new JsonObject();
    for (AbstractHudModule module : manager.modules()) {
      JsonObject data = new JsonObject();
      data.addProperty("enabled", module.userEnabled());
      data.addProperty("hudVisible", module.hudVisible());
      data.addProperty("anchor", module.anchor().name());
      data.addProperty("x", module.offsetX());
      data.addProperty("y", module.offsetY());
      data.addProperty("scale", module.scale());
      JsonObject settings = new JsonObject();
      for (Setting<?> setting : module.settings()) {
        Object v = setting.get();
        if (v instanceof Boolean) settings.addProperty(setting.name(), (Boolean) v);
        else if (v instanceof Number) settings.addProperty(setting.name(), (Number) v);
        else if (v instanceof String) settings.addProperty(setting.name(), (String) v);
      }
      data.add("settings", settings);
      modules.add(module.name(), data);
    }
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);
    JsonObject options = new JsonObject();
    options.addProperty("hideAchievements", GlobalOptions.hideAchievements);
    options.addProperty("textShadow", GlobalOptions.textShadow);
    options.addProperty("borderless", GlobalOptions.borderless);
    options.addProperty("chunkPace", GlobalOptions.chunkPace);
    options.addProperty("tileDistance", GlobalOptions.tileDistance);
    options.addProperty("layerDistance", GlobalOptions.layerDistance);
    options.addProperty("itemDistance", GlobalOptions.itemDistance);
    options.addProperty("nametagDistance", GlobalOptions.nametagDistance);
    options.addProperty("glintDistance", GlobalOptions.glintDistance);
    options.addProperty("hudRefresh", GlobalOptions.hudRefresh);
    options.addProperty("unfocusedLimit", GlobalOptions.unfocusedLimit);
    options.addProperty("hideFoliage", GlobalOptions.hideFoliage);
    options.addProperty("hideStuckArrows", GlobalOptions.hideStuckArrows);
    options.addProperty("highPriority", GlobalOptions.highPriority);
    options.addProperty("leavesMode", GlobalOptions.leavesMode);
    options.addProperty("clearGlass", GlobalOptions.clearGlass);
    options.addProperty("resolution", GlobalOptions.resolution);
    options.addProperty("fogStart", GlobalOptions.fogStart);
    options.addProperty("fogEnd", GlobalOptions.fogEnd);
    options.addProperty("cloudHeight", GlobalOptions.cloudHeight);
    options.addProperty("anisotropy", GlobalOptions.anisotropy);
    options.addProperty("fxaa", GlobalOptions.fxaa);
    options.addProperty("betterGrass", GlobalOptions.betterGrass);
    options.addProperty("vignette", GlobalOptions.vignette);
    com.google.gson.JsonObject packFeatures = new com.google.gson.JsonObject();
    for (java.util.Map.Entry<String, Boolean> feature : GlobalOptions.packFeatures.entrySet()) packFeatures.addProperty(feature.getKey(), feature.getValue());
    options.add("packFeatures", packFeatures);
    options.addProperty("entityCulling", GlobalOptions.entityCulling);
    JsonArray recent = new JsonArray();
    for (Integer c : GlobalOptions.recentColors) recent.add(new JsonPrimitive(c));
    options.add("recentColors", recent);
    JsonArray disabledAnimations = new JsonArray();
    for (String key : GlobalOptions.disabledAnimations) disabledAnimations.add(new JsonPrimitive(key));
    options.add("disabledAnimations", disabledAnimations);
    root.add("options", options);
    root.add("modules", modules);
    JsonArray placed = new JsonArray();
    for (String name : manager.oneConfigPlaced()) placed.add(new JsonPrimitive(name));
    root.add("oneconfigPlaced", placed);
    return root;
  }

  public void apply(HudManager manager, JsonObject root) {
    if (root != null && root.has("options") && root.get("options").isJsonObject()) {
      JsonObject options = root.getAsJsonObject("options");
      if (options.has("hideAchievements")) GlobalOptions.hideAchievements = options.get("hideAchievements").getAsBoolean();
      if (options.has("recentColors") && options.get("recentColors").isJsonArray()) {
        GlobalOptions.recentColors.clear();
        for (JsonElement c : options.getAsJsonArray("recentColors")) GlobalOptions.recentColors.add(c.getAsInt());
      }
      if (options.has("unfocusedLimit")) GlobalOptions.unfocusedLimit = options.get("unfocusedLimit").getAsBoolean();
      if (options.has("entityCulling")) GlobalOptions.entityCulling = options.get("entityCulling").getAsBoolean();
      if (options.has("betterGrass")) GlobalOptions.betterGrass = options.get("betterGrass").getAsBoolean();
      if (options.has("fxaa")) GlobalOptions.fxaa = options.get("fxaa").getAsBoolean();
      if (options.has("anisotropy")) GlobalOptions.anisotropy = options.get("anisotropy").getAsInt();
      if (options.has("fogStart")) GlobalOptions.fogStart = options.get("fogStart").getAsInt();
      if (options.has("fogEnd")) GlobalOptions.fogEnd = options.get("fogEnd").getAsInt();
      if (options.has("cloudHeight")) GlobalOptions.cloudHeight = options.get("cloudHeight").getAsInt();
      if (options.has("leavesMode")) GlobalOptions.leavesMode = options.get("leavesMode").getAsInt();
      if (options.has("resolution")) GlobalOptions.resolution = options.get("resolution").getAsString();
      if (options.has("clearGlass")) GlobalOptions.clearGlass = Math.max(0, Math.min(2, options.get("clearGlass").getAsInt()));
      if (options.has("vignette")) GlobalOptions.vignette = options.get("vignette").getAsBoolean();
      if (options.has("packFeatures"))
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> feature : options.getAsJsonObject("packFeatures").entrySet())
          GlobalOptions.packFeatures.put(feature.getKey(), feature.getValue().getAsBoolean());
      if (options.has("highPriority")) GlobalOptions.highPriority = options.get("highPriority").getAsBoolean();
      if (options.has("hideFoliage")) GlobalOptions.hideFoliage = options.get("hideFoliage").getAsBoolean();
      if (options.has("hideStuckArrows")) GlobalOptions.hideStuckArrows = options.get("hideStuckArrows").getAsBoolean();
      if (options.has("tileDistance")) GlobalOptions.tileDistance = options.get("tileDistance").getAsInt();
      if (options.has("layerDistance")) GlobalOptions.layerDistance = options.get("layerDistance").getAsInt();
      if (options.has("itemDistance")) GlobalOptions.itemDistance = options.get("itemDistance").getAsInt();
      if (options.has("nametagDistance")) GlobalOptions.nametagDistance = options.get("nametagDistance").getAsInt();
      if (options.has("glintDistance")) GlobalOptions.glintDistance = options.get("glintDistance").getAsInt();
      if (options.has("hudRefresh")) GlobalOptions.hudRefresh = options.get("hudRefresh").getAsInt();
      if (options.has("chunkPace")) GlobalOptions.chunkPace = options.get("chunkPace").getAsInt();
      if (options.has("borderless")) GlobalOptions.borderless = options.get("borderless").getAsBoolean();
      if (options.has("textShadow")) GlobalOptions.textShadow = options.get("textShadow").getAsBoolean();
      if (options.has("disabledAnimations") && options.get("disabledAnimations").isJsonArray()) {
        GlobalOptions.disabledAnimations.clear();
        for (JsonElement key : options.getAsJsonArray("disabledAnimations"))
          GlobalOptions.disabledAnimations.add(key.getAsString());
      }
    }
    JsonObject modules = root == null ? null : root.getAsJsonObject("modules");
    if (modules == null) return;
    if (root.has("oneconfigPlaced") && root.get("oneconfigPlaced").isJsonArray())
      for (JsonElement name : root.getAsJsonArray("oneconfigPlaced"))
        manager.oneConfigPlaced().add(name.getAsString());
    for (AbstractHudModule module : manager.modules()) {
      JsonObject data = modules.getAsJsonObject(module.name());
      if (data == null) continue;
      if (data.has("enabled")) module.enabled(data.get("enabled").getAsBoolean());
      if (data.has("hudVisible")) module.hudVisible(data.get("hudVisible").getAsBoolean());
      if (data.has("anchor"))
        try {
          module.anchor(Anchor.valueOf(data.get("anchor").getAsString()));
        } catch (IllegalArgumentException ignored) {
        }
      if (data.has("x") && data.has("y"))
        module.offset(data.get("x").getAsFloat(), data.get("y").getAsFloat());
      if (data.has("scale")) module.scale(data.get("scale").getAsFloat());
      JsonObject settings = data.getAsJsonObject("settings");
      if (settings != null)
        for (Setting<?> setting : module.settings()) readSetting(settings, setting);
    }
  }

  public File file() {
    return file;
  }
}
