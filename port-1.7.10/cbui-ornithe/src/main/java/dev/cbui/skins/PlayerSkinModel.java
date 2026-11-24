package dev.cbui.skins;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/**
 * Player model for 64x64 skins: separate left limbs, a second layer on every part, and 3-pixel arms for slim skins.
 * The base parts keep ModelBiped's field names, so every pose the game sets (sneaking, swinging, bow, blocking, held
 * item) keeps working unchanged; the layers only copy those poses.
 */
public class PlayerSkinModel extends ModelBiped {
  /** Second-layer parts sit this much outside their base part, enough to avoid z-fighting. */
  private static final float LAYER = 0.25F;

  public final boolean slim;
  public final ModelRenderer bipedBodyWear;
  public final ModelRenderer bipedRightArmWear;
  public final ModelRenderer bipedLeftArmWear;
  public final ModelRenderer bipedRightLegWear;
  public final ModelRenderer bipedLeftLegWear;

  public PlayerSkinModel(boolean slim) {
    super(0.0F, 0.0F, 64, 64);
    this.slim = slim;
    int armWidth = slim ? 3 : 4;
    // Slim arms start half a pixel lower, so their top is not flush with the shoulder line.
    float armY = slim ? 2.5F : 2.0F;
    // The narrower arm loses its outer column; the side touching the body stays where it was.
    float rightArmX = slim ? -2.0F : -3.0F;

    // Capes have their own 64x32 texture, unlike the skin, so the cape part keeps the old texture height.
    boxList.remove(bipedCloak);
    bipedCloak = new ModelRenderer(this, 0, 0).setTextureSize(64, 32);
    bipedCloak.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1, 0.0F);

    if (slim) {
      boxList.remove(bipedRightArm);
      bipedRightArm = new SlimRightArm(this, 40, 16);
      bipedRightArm.addBox(rightArmX, -2.0F, -2.0F, armWidth, 12, 4, 0.0F);
      bipedRightArm.setRotationPoint(-5.0F, armY, 0.0F);
    }
    // ModelBiped mirrors the right limbs for the left side; 64x64 skins paint the left limbs in their own slots.
    boxList.remove(bipedLeftArm);
    bipedLeftArm = new ModelRenderer(this, 32, 48);
    bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, armWidth, 12, 4, 0.0F);
    bipedLeftArm.setRotationPoint(5.0F, armY, 0.0F);

    boxList.remove(bipedLeftLeg);
    bipedLeftLeg = new ModelRenderer(this, 16, 48);
    bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
    bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);

    bipedBodyWear = new ModelRenderer(this, 16, 32);
    bipedBodyWear.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, LAYER);
    bipedRightArmWear = new ModelRenderer(this, 40, 32);
    bipedRightArmWear.addBox(rightArmX, -2.0F, -2.0F, armWidth, 12, 4, LAYER);
    bipedLeftArmWear = new ModelRenderer(this, 48, 48);
    bipedLeftArmWear.addBox(-1.0F, -2.0F, -2.0F, armWidth, 12, 4, LAYER);
    bipedRightLegWear = new ModelRenderer(this, 0, 32);
    bipedRightLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, LAYER);
    bipedLeftLegWear = new ModelRenderer(this, 0, 48);
    bipedLeftLegWear.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, LAYER);
  }

  @Override
  public void render(Entity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
      float scale) {
    super.render(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch, scale);
    // Players are never drawn as children; the child path stays vanilla rather than guessing its layer scaling.
    if (isChild) return;
    bipedBodyWear.render(scale);
    bipedRightArmWear.render(scale);
    bipedLeftArmWear.render(scale);
    bipedRightLegWear.render(scale);
    bipedLeftLegWear.render(scale);
  }

  @Override
  public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
      float scale, Entity entity) {
    super.setRotationAngles(limbSwing, limbSwingAmount, age, headYaw, headPitch, scale, entity);
    follow(bipedBodyWear, bipedBody);
    follow(bipedRightArmWear, bipedRightArm);
    follow(bipedLeftArmWear, bipedLeftArm);
    follow(bipedRightLegWear, bipedRightLeg);
    follow(bipedLeftLegWear, bipedLeftLeg);
  }

  /**
   * Copies the whole placement, including visibility, so code that hides a limb (for example to show only some body
   * parts) hides its layer with it.
   */
  private static void follow(ModelRenderer layer, ModelRenderer base) {
    layer.rotateAngleX = base.rotateAngleX;
    layer.rotateAngleY = base.rotateAngleY;
    layer.rotateAngleZ = base.rotateAngleZ;
    layer.rotationPointX = base.rotationPointX;
    layer.rotationPointY = base.rotationPointY;
    layer.rotationPointZ = base.rotationPointZ;
    layer.offsetX = base.offsetX;
    layer.offsetY = base.offsetY;
    layer.offsetZ = base.offsetZ;
    layer.showModel = base.showModel;
    layer.isHidden = base.isHidden;
  }

  /**
   * RenderPlayer places held items from the right arm's pivot with an offset sized for a 4-pixel arm. The slim arm's
   * middle is half a pixel further in, so the pivot moves with it while the item is placed.
   */
  private static final class SlimRightArm extends ModelRenderer {
    SlimRightArm(ModelBase model, int textureU, int textureV) {
      super(model, textureU, textureV);
    }

    @Override
    public void postRender(float scale) {
      rotationPointX += 0.5F;
      try {
        super.postRender(scale);
      } finally {
        rotationPointX -= 0.5F;
      }
    }
  }
}
