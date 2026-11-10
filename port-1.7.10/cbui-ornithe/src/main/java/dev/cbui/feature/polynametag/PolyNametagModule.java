package dev.cbui.feature.polynametag;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/** Settings for Minecraft's own entity-label renderer. */
public final class PolyNametagModule extends FeatureModule {
  public final Setting.Toggle removeNametags = grouped("General", add(new Setting.Toggle("Remove nametags", false, "Removes all vanilla nametags.")));
  public final Setting.Slider heightOffset = grouped("General", add(new Setting.Slider("Height offset", 0f, -10f, 10f, .25f, "How much to offset the nametag vertically (positive is up)")));
  public final Setting.Slider scale = grouped("General", add(new Setting.Slider("Scale", 1f, .25f, 3f, .05f, "How big nametags are (1 is normal)")));
  public final Setting.Choice textShadow = grouped("General", add(Setting.tip(new Setting.Choice("Nametag Text Shadow", "No Shadow", "No Shadow", "Shadow"), "The type of shadow to render")));
  public final Setting.Toggle showOwn = grouped("General", add(new Setting.Toggle("Show own nametag", true, "Whether to show your own nametag (visible in third person)")));
  public final Setting.Toggle showInInventory = grouped("General", add(new Setting.Toggle("Show in inventory", false, "Show your own nametag on the model in the inventory screen")));
  public final Setting.Slider renderDistance = grouped("General", add(new Setting.Slider("Render distance", 64f, 1f, 256f, 1f, "Maximum distance for vanilla nametags")));
  public final Setting.Toggle hideEntity = grouped("Hidden HUD (F1)", add(new Setting.Toggle("Hide entity nametags when HUD hidden", true, "Hide non-player, non-armor stand nametags while the HUD is hidden (F1)")));
  public final Setting.Toggle hidePlayer = grouped("Hidden HUD (F1)", add(new Setting.Toggle("Hide player nametags when HUD hidden", true, "Hide player nametags while the HUD is hidden (F1)")));
  public final Setting.Toggle backgroundOn = grouped("Nametag background", add(new Setting.Toggle("Nametag Background", true, "Whether to render a background behind the nametag")));
  public final Setting.Color backgroundCol = grouped("Nametag background", add(new Setting.Color("Nametag Background color", 0x3F000000, "The color of the background")));
  public final Setting.Toggle rounded = grouped("Nametag background", add(new Setting.Toggle("Rounded Corners", false, "Round the corners of the background")));
  public final Setting.Slider cornerRadius = grouped("Nametag background", add(new Setting.Slider("Corner Radius", 3f, 0f, 10f, 1f, "The radius of the rounded corners")));
  public final Setting.Slider paddingX = grouped("Nametag background", add(new Setting.Slider("Padding X", 0f, 0f, 10f, 1f, "Horizontal padding around the text")));
  public final Setting.Slider paddingY = grouped("Nametag background", add(new Setting.Slider("Padding Y", 0f, 0f, 10f, 1f, "Vertical padding around the text")));
  public final Setting.Color textCol = grouped("Nametag text", add(new Setting.Color("Nametag Text color", 0xFFFFFFFF, "The color of the text")));
  public final Setting.Toggle overrideText = grouped("Nametag text", add(new Setting.Toggle("Nametag Override text color", false, "Force the text color above, ignoring colour codes and team/rank colours in the name")));

  public PolyNametagModule() { super("Nametags", true); }
  @Override public String category() { return "VISUALS"; }
}
