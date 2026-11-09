package dev.cbui.feature.oldanimations;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDaylightDetector;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBanner;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

/** The behaviour behind the Old Animations mixins. Callers wrap these in try/catch; a problem means vanilla behaviour. */
public final class OldAnimationsHooks {
  private OldAnimationsHooks() {}

  // ---- smooth sneaking: the camera height follows the sneak state over the ticks ----
  private static float lastEyeHeight, eyeHeight;
  private static Entity lastCamera;
  private static boolean lastSleeping;

  /** Once per client tick (EntityRenderer.updateRenderer). */
  public static void tickEyeHeight(Minecraft mc) {
    Entity camera = mc.getRenderViewEntity();
    if (camera == null) return;
    float target = camera.getEyeHeight();
    boolean sleeping = camera instanceof EntityPlayer && ((EntityPlayer) camera).isPlayerSleeping();
    if (camera != lastCamera || sleeping != lastSleeping) {
      eyeHeight = target;
      lastCamera = camera;
      lastSleeping = sleeping;
    }
    lastEyeHeight = eyeHeight;
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m != null && m.smoothSneaking.get() && m.longerUnsneak.get() && target > eyeHeight) eyeHeight = eyeHeight + (target - eyeHeight) * 0.6f;
    else eyeHeight = target;
  }

  /** The interpolated camera height. */
  public static float eyeHeight(float partialTicks) {
    return lastEyeHeight + (eyeHeight - lastEyeHeight) * partialTicks;
  }

  /** Replaces Entity.getEyeHeight() in the camera code. */
  public static float smoothEyeHeight(float original, float partialTicks) {
    OldAnimationsModule m = OldAnimationsModule.active();
    return m != null && m.smoothSneaking.get() ? eyeHeight(partialTicks) : original;
  }

  /** The y offset the own player model gets so the body follows the lowered camera. */
  public static double playerModelY(EntityLivingBase entity, double y, float partialTicks) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.smoothSneaking.get()) return y;
    Minecraft mc = Minecraft.getMinecraft();
    if (entity != mc.thePlayer || mc.getRenderViewEntity() != mc.thePlayer || mc.thePlayer.isPlayerSleeping()) return y;
    float height = eyeHeight(partialTicks);
    return y - (double) MathHelper.clamp_float(height - 1.62f, 0.0f, 0.08f);
  }

  // ---- health flashing ----
  public static boolean healthFlash(boolean original) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.healthFlashing.get()) return original;
    EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
    if (player == null) return original;
    int timer = player.hurtResistantTime;
    return timer >= 10 && (timer / 3) % 2 == 1;
  }

  // ---- sneaking pose: no vanilla 1.8 body shift while smooth sneaking is on ----
  public static boolean sneakPose(boolean original) {
    if (!original) return false;
    OldAnimationsModule m = OldAnimationsModule.active();
    return m == null || !m.smoothSneaking.get();
  }

  // ---- block hitting / always swing ----
  /** Replaces the "is hitting a block" check of Minecraft.rightClickMouse. */
  public static boolean hittingBlockForUse(boolean original) {
    if (!original) return false;
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.blockHitting.get()) return true;
    EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
    ItemStack held = player == null ? null : player.getCurrentEquippedItem();
    return held == null || (held.getItemUseAction() == EnumAction.NONE && !(held.getItem() instanceof ItemBlock));
  }

  /** Minecraft.sendClickBlockToController head: swing while holding attack on a block with an item in use. */
  public static void blockHitSwing(Minecraft mc, boolean holdingAttack, int leftClickCounter, int armSwingEnd) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.blockHitting.get() || !holdingAttack || leftClickCounter > 0) return;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null || !player.isUsingItem() || mc.objectMouseOver == null || mc.theWorld == null) return;
    if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
    Block block = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
    if (block.getMaterial() != Material.air) swing(mc, armSwingEnd);
  }

  public static void alwaysSwing(Minecraft mc, int armSwingEnd) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m != null && m.alwaysSwing.get()) swing(mc, armSwingEnd);
  }

  private static void swing(Minecraft mc, int armSwingEnd) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return;
    if (!player.isSwingInProgress || player.swingProgressInt >= armSwingEnd / 2 || player.swingProgressInt < 0) {
      player.swingProgressInt = -1;
      player.isSwingInProgress = true;
    }
  }

  /** ItemRenderer.transformFirstPersonItem's swing argument. */
  public static float firstPersonSwing(float original, float partialTicks) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.blockHitting.get()) return original;
    EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
    return player != null && player.getItemInUseCount() > 0 ? player.getSwingProgress(partialTicks) : original;
  }

  // ---- item transforms ----
  /** The 1.7 item placement shared by first and third person. */
  public static void legacyItemTransform() {
    GlStateManager.translate(0.0f, -0.3f, 0.0f);
    GlStateManager.scale(1.5f, 1.5f, 1.5f);
    GlStateManager.rotate(50.0f, 0.0f, 1.0f, 0.0f);
    GlStateManager.rotate(335.0f, 0.0f, 0.0f, 1.0f);
    GlStateManager.translate(-0.9375f, -0.0625f, 0.0f);
    GlStateManager.translate(0.5f, 0.5f, -0.03125f);
    GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
  }

  private static Block blockOf(Item item) {
    return item instanceof ItemBlock ? ((ItemBlock) item).getBlock() : null;
  }

  /** True when the 1.7 transform replaced the vanilla first person one (so vanilla must skip its own). */
  public static boolean applyFirstPerson(ItemStack stack, IBakedModel model) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || stack == null || model == null) return false;
    boolean fpt = m.firstPersonTransforms.get();
    Item item = stack.getItem();
    if (item == null) return false;
    Block block = blockOf(item);
    if (model.isBuiltInRenderer()) {
      if (fpt && (block instanceof BlockChest || block instanceof BlockEnderChest)) GlStateManager.rotate(-90.0f, 0.0f, 1.0f, 0.0f);
      return false;
    }
    if (model.isGui3d()) {
      if (block == null || !fpt) return false;
      boolean odd = block instanceof BlockFurnace || block instanceof BlockDispenser || block instanceof BlockPumpkin;
      GlStateManager.rotate(odd ? 270.0f : 90.0f, 0.0f, 1.0f, 0.0f);
      if (block instanceof BlockTrapDoor) GlStateManager.translate(0.0f, 0.203125f, 0.0f);
      return block instanceof BlockCarpet || block instanceof BlockSnow || block instanceof BlockDaylightDetector || block instanceof BlockTrapDoor;
    }
    if (item.shouldRotateAroundWhenRendering()) {
      if (!fpt) return false;
      GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
    } else {
      EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
      boolean blocking = player != null && player.getItemInUseCount() > 0 && stack.getItemUseAction() == EnumAction.BLOCK;
      if (!(blocking ? m.blockHitting.get() : fpt)) return false;
    }
    GlStateManager.scale(0.5f, 0.5f, 0.5f);
    legacyItemTransform();
    GlStateManager.scale(2.0f, 2.0f, 2.0f);
    return true;
  }

  /** Third person placement. True when the 1.7 placement was applied (the caller then asks vanilla for no transform of its own). */
  public static boolean applyThirdPerson(EntityLivingBase entity, ItemStack stack) {
    OldAnimationsModule m = OldAnimationsModule.active();
    if (m == null || !m.thirdPersonTransforms.get() || stack == null) return false;
    Item item = stack.getItem();
    if (item == null || item instanceof ItemSkull || item instanceof ItemBanner) return false;
    Block block = blockOf(item);
    if (block != null && block.getRenderType() == 2) {
      GlStateManager.rotate(-90.0f, 0.0f, 1.0f, 0.0f);
      return true;
    }
    if (block != null && Minecraft.getMinecraft().getRenderItem().shouldRenderItemIn3D(stack)) {
      GlStateManager.translate(0.0f, 0.1875f, -0.3125f);
      GlStateManager.rotate(20.0f, 1.0f, 0.0f, 0.0f);
      GlStateManager.rotate(45.0f, 0.0f, 1.0f, 0.0f);
      GlStateManager.scale(-0.375f, -0.375f, 0.375f);
      boolean odd = block instanceof BlockFurnace || block instanceof BlockDispenser || block instanceof BlockPumpkin;
      GlStateManager.rotate(odd ? 270.0f : 90.0f, 0.0f, 1.0f, 0.0f);
      if (block instanceof BlockTrapDoor) GlStateManager.translate(0.0f, 0.40625f, 0.0f);
      return true;
    }
    boolean flip = true;
    if (item == Items.bow) {
      GlStateManager.translate(0.0f, 0.125f, 0.3125f);
      GlStateManager.rotate(-20.0f, 0.0f, 1.0f, 0.0f);
      GlStateManager.scale(0.625f, -0.625f, 0.625f);
      GlStateManager.rotate(-100.0f, 1.0f, 0.0f, 0.0f);
      GlStateManager.rotate(45.0f, 0.0f, 1.0f, 0.0f);
    } else if (item.isFull3D()) {
      if (item.shouldRotateAroundWhenRendering()) {
        GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager.translate(0.0f, -0.125f, 0.0f);
      }
      if (entity instanceof EntityPlayer && ((EntityPlayer) entity).getItemInUseCount() > 0 && stack.getItemUseAction() == EnumAction.BLOCK) {
        GlStateManager.translate(0.05f, 0.0f, -0.1f);
        GlStateManager.rotate(-50.0f, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-10.0f, 1.0f, 0.0f, 0.0f);
        GlStateManager.rotate(-60.0f, 0.0f, 0.0f, 1.0f);
      }
      GlStateManager.translate(entity instanceof EntitySkeleton ? 0.09375f : 0.0f, 0.1875f, 0.0f);
      GlStateManager.scale(0.625f, -0.625f, 0.625f);
      GlStateManager.rotate(-100.0f, 1.0f, 0.0f, 0.0f);
      GlStateManager.rotate(45.0f, 0.0f, 1.0f, 0.0f);
    } else {
      flip = false;
      GlStateManager.translate(0.25f, 0.1875f, -0.1875f);
      GlStateManager.scale(0.375f, 0.375f, 0.375f);
      GlStateManager.rotate(60.0f, 0.0f, 0.0f, 1.0f);
      GlStateManager.rotate(-90.0f, 1.0f, 0.0f, 0.0f);
      GlStateManager.rotate(20.0f, 0.0f, 0.0f, 1.0f);
    }
    legacyItemTransform();
    if (flip) GlStateManager.scale(1.0f, 1.0f, -1.0f);
    return true;
  }
}
