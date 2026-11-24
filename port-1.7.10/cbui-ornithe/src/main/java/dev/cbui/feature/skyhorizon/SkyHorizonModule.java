package dev.cbui.feature.skyhorizon;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;

/**
 * How the sky is drawn near and under the horizon: where the dark lower part starts, whether it (and the black walls
 * drawn with it) is drawn at all, the dawn/dusk glow, and no sky at all below the world.
 */
public final class SkyHorizonModule extends FeatureModule {
  private static final String HORIZON = "Horizon";
  private static final String MORE = "More";
  /** Eye height under which the whole sky is skipped when "No sky below the world" is on. */
  private static final double VOID_EYE_Y = -1.5;

  private final Setting.Toggle horizonAtZero = grouped(HORIZON, add(new Setting.Toggle(
      "Darken only below the world", true,
      "The bottom of the sky only turns dark once you are below the world, not in every cave")));
  private final Setting.Toggle hideLowerDome = grouped(HORIZON, add(new Setting.Toggle(
      "Hide the dark lower sky", true, "Do not draw the dark half of the sky under the horizon")));
  private final Setting.Toggle hideGlow = grouped(MORE, add(new Setting.Toggle(
      "Hide sunrise and sunset glow", false, "No orange glow on the horizon at dawn and dusk")));
  private final Setting.Toggle noVoidSky = grouped(MORE, add(new Setting.Toggle(
      "No sky below the world", false, "Stop drawing the sky once you fall out of the bottom of the world")));

  public SkyHorizonModule() {
    super("Smooth Skies", true);
  }

  @Override public String category() { return "VISUALS"; }

  /** The module when it exists and is switched on, else null. */
  public static SkyHorizonModule active() {
    SkyHorizonModule module = Features.get(SkyHorizonModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** The horizon height the client sky should use instead of the world's own. */
  public double horizon(double worldHorizon) {
    return horizonAtZero.get() ? 0.0 : worldHorizon;
  }

  /** True when the dark lower dome and the black walls under the horizon are not drawn. */
  public boolean hidesLowerSky() { return hideLowerDome.get(); }

  /** True when the sunrise/sunset fan is not drawn. */
  public boolean hidesGlow() { return hideGlow.get(); }

  /** True when the whole sky should be skipped this frame because the camera is below the world. */
  public boolean skipsWholeSky(float partialTicks) {
    if (!noVoidSky.get()) return false;
    EntityLivingBase view = mc.renderViewEntity;
    if (view == null) return false;
    // On 1.7.10 the client player's position already sits at eye height.
    Vec3 eyes = view.getPosition(partialTicks);
    return eyes != null && eyes.yCoord < VOID_EYE_Y;
  }
}
