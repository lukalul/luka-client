package dev.cbui.skins;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.entity.Entity;

/**
 * Player head (skull) for 64x64 skins. The game's own head model assumes a 64x32 texture and would stretch the face over
 * twice the rows; this one reads the same pixels at the right height and adds the hat layer.
 */
public class PlayerHeadModel extends ModelSkeletonHead {
  private final ModelRenderer hat;

  public PlayerHeadModel() {
    super(0, 0, 64, 64);
    hat = new ModelRenderer(this, 32, 0);
    hat.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.25F);
  }

  @Override
  public void render(Entity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
      float scale) {
    super.render(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch, scale);
    hat.rotateAngleX = skeletonHead.rotateAngleX;
    hat.rotateAngleY = skeletonHead.rotateAngleY;
    hat.rotateAngleZ = skeletonHead.rotateAngleZ;
    hat.rotationPointX = skeletonHead.rotationPointX;
    hat.rotationPointY = skeletonHead.rotationPointY;
    hat.rotationPointZ = skeletonHead.rotationPointZ;
    hat.render(scale);
  }
}
