package dev.envoy.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import dev.envoy.EnvoySettings;
import dev.envoy.module.nametag.AdventureText;
import dev.envoy.module.nametag.NametagState;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import dev.envoy.platform.RenderLivingEvent;

/**
 * Draws the Apollo nametags above players, replacing the vanilla nametag (by cancelling
 * {@code RenderLivingEvent.Specials.Pre}, which wraps {@code renderName}).
 *
 * <p>The billboard transform is taken from Eterion's {@code CustomNametagRenderer}. The lines (from
 * {@link NametagState}) are stacked above the head, each run drawn with its RGB color for full hex
 * support.</p>
 */
public final class NametagRenderer {

    private static final float TEXT_SCALE = 0.02666667F;

    public void onRenderName(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();

        List<List<AdventureText.Run>> lines = NametagState.get(player.getUniqueID());
        boolean hasLines = lines != null && !lines.isEmpty();
        // "Remove nametags" also removes the server's tags
        if (EnvoySettings.nametagsHidden && hasLines) {
            event.setCanceled(true);
            return;
        }
        // Staff Mode's sticky nametags: every other player gets a tag through walls and from far away, drawn by stickyPass()
        if (sticky() && player != mc.thePlayer) {
            event.setCanceled(true);
            return;
        }
        if (!hasLines) {
            return;
        }
        // optional: leave the server's tags alone until staff mode (off by default: the Nametags card is Apollo compatible)
        if (EnvoySettings.nametagsOnlyStaff && !dev.envoy.module.staff.StaffState.active()) {
            return;
        }
        if (player == mc.thePlayer && mc.gameSettings.thirdPersonView == 0 && !EnvoySettings.renderSelf) {
            return;
        }
        if (player.getDistanceSqToEntity(mc.renderViewEntity) > (double) EnvoySettings.nametagMaxDistance * EnvoySettings.nametagMaxDistance) {
            return;
        }

        event.setCanceled(true);
        // drawn at the end of the world pass (see renderQueued), after translucent blocks, so glass cannot tint the tag
        if (PENDING.size() > 512) PENDING.clear();
        PENDING.add(new PendingTag(player, lines, event.x, event.y, event.z));
    }

    private static boolean sticky() {
        return EnvoySettings.staffSticky && dev.envoy.module.staff.StaffState.allowsNametags();
    }

    /** A tag over every other player, through walls and up to the chosen distance, Apollo lines when the server sent them. */
    private void renderStaffTags(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        RenderManager rm = RenderManager.instance;
        double max = EnvoySettings.staffStickyDistance;
        equipmentBudget = EQUIPMENT_PLAYERS;
        for (Object o : new java.util.ArrayList<Object>(mc.theWorld.playerEntities)) {
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || p.isDead || p == mc.renderViewEntity) {
                continue;
            }
            double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks - rm.viewerPosX;
            double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks - rm.viewerPosY;
            double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks - rm.viewerPosZ;
            if (x * x + y * y + z * z > max * max) {
                continue;
            }
            List<List<AdventureText.Run>> lines = NametagState.get(p.getUniqueID());
            if (lines == null || lines.isEmpty()) {
                if (EnvoySettings.nametagsHidden) {
                    continue;
                }
                List<AdventureText.Run> name = new java.util.ArrayList<AdventureText.Run>();
                name.add(new AdventureText.Run(p.getFormattedCommandSenderName().getFormattedText(), 0xFFFFFF));
                lines = new java.util.ArrayList<List<AdventureText.Run>>();
                lines.add(name);
            } else if (EnvoySettings.nametagsHidden) {
                continue;
            }
            // item icons are the costly part of a tag: only the first few players that are near enough get them
            drawGear = x * x + y * y + z * z <= EQUIPMENT_RANGE * EQUIPMENT_RANGE && equipmentBudget > 0;
            if (drawGear) {
                equipmentBudget--;
            }
            paintTag(p, lines, x, y, z, true);
        }
        drawGear = true;
    }

    private static final int EQUIPMENT_PLAYERS = 10;
    private static final double EQUIPMENT_RANGE = 48.0D;
    private int equipmentBudget;
    private boolean drawGear = true;

    /** One extra line under the tag: health (+ absorption) coloured by how low it is, and the distance in blocks. */
    private static List<List<AdventureText.Run>> decorateStaffLines(EntityPlayer p, List<List<AdventureText.Run>> lines, double distance) {
        StringBuilder text = new StringBuilder();
        if (EnvoySettings.staffTagHealth) {
            float health = p.getHealth();
            String color = health >= 12.0F ? "§a" : health >= 6.0F ? "§e" : "§c";
            text.append(color).append(Math.round(health));
            if (p.getAbsorptionAmount() > 0.0F) {
                text.append("§6+").append(Math.round(p.getAbsorptionAmount()));
            }
        }
        if (EnvoySettings.staffTagDistance) {
            if (text.length() > 0) {
                text.append(" §7| ");
            }
            text.append("§7").append(Math.round(distance)).append('m');
        }
        List<List<AdventureText.Run>> out = new java.util.ArrayList<List<AdventureText.Run>>();
        String status = EnvoySettings.staffTagStatus ? statusText(p) : "";
        if (status.length() > 0) {
            List<AdventureText.Run> line = new java.util.ArrayList<AdventureText.Run>();
            line.add(new AdventureText.Run(status, 0xFFFFFF));
            out.add(line);
        }
        if (text.length() > 0) {
            List<AdventureText.Run> line = new java.util.ArrayList<AdventureText.Run>();
            line.add(new AdventureText.Run(text.toString(), 0xFFFFFF));
            out.add(line);
        }
        out.addAll(lines);
        return out;
    }

    /** What the player is doing right now, for staff: crouching, invisible, sprinting, blocking, burning, riding. */
    private static String statusText(EntityPlayer p) {
        StringBuilder out = new StringBuilder();
        if (p.isSneaking()) out.append("§bSneaking ");
        if (p.isInvisible()) out.append("§dInvisible ");
        if (p.isSprinting()) out.append("§aSprinting ");
        if (p.isBlocking()) out.append("§eBlocking ");
        if (p.isBurning()) out.append("§6Burning ");
        if (p.isRiding()) out.append("§7Riding ");
        if (p.isUsingItem()) out.append("§2Using item ");
        return out.toString().trim();
    }

    private static final String[] ENCHANT_LABELS = new String[64];

    static {
        String[][] pairs = {{"0", "P"}, {"1", "FP"}, {"2", "FF"}, {"3", "BP"}, {"4", "PP"}, {"5", "R"}, {"6", "A"}, {"7", "Th"},
            {"16", "S"}, {"17", "Sm"}, {"18", "Ba"}, {"19", "K"}, {"20", "FA"}, {"21", "L"}, {"32", "E"}, {"33", "Si"},
            {"34", "U"}, {"35", "F"}, {"48", "Pw"}, {"49", "Pu"}, {"50", "Fl"}, {"51", "I"}, {"61", "LS"}, {"62", "Lu"}};
        for (String[] pair : pairs) {
            ENCHANT_LABELS[Integer.parseInt(pair[0])] = pair[1];
        }
    }

    /** Held item and armour as small item icons above the tag; enchant labels (P4 U3 ...) stack above each icon. */
    private static void drawEquipment(Minecraft mc, FontRenderer font, EntityPlayer p, int baseY, int lineHeight) {
        net.minecraft.item.ItemStack[] stacks = new net.minecraft.item.ItemStack[5];
        int present = 0;
        for (int slot = 0; slot < 5; slot++) {
            stacks[slot] = p.getEquipmentInSlot(slot);
            if (stacks[slot] != null) {
                present++;
            }
        }
        if (present == 0) {
            return;
        }
        int x = -(present * 8);
        for (net.minecraft.item.ItemStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            if (!drawFlatIcon(mc, stack, x, baseY)) {
                GL11.glPushMatrix();
                GL11.glScalef(1.0F, 1.0F, 0.01F);
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableRescaleNormal();
                GlStateManager.disableDepth();
                dev.luka.compat.Mc17.drawItemAndEffect(mc, stack, x, baseY);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                GlStateManager.disableRescaleNormal();
                GL11.glPopMatrix();
            }
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (EnvoySettings.staffTagEnchants) {
                int row = 0;
                java.util.Map<Integer, Integer> enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
                for (java.util.Map.Entry<Integer, Integer> e : enchants.entrySet()) {
                    if (row >= 4) {
                        break;
                    }
                    int id = e.getKey();
                    String label = (id >= 0 && id < ENCHANT_LABELS.length && ENCHANT_LABELS[id] != null ? ENCHANT_LABELS[id] : "#" + id) + levelText(e.getValue());
                    GL11.glPushMatrix();
                    GL11.glScalef(0.5F, 0.5F, 0.5F);
                    font.drawString(label, (x + 8) * 2 - font.getStringWidth(label) / 2, (baseY - 8 - row * 6) * 2, 0xFFFFFFFF, true);
                    GL11.glPopMatrix();
                    row++;
                }
            }
            x += 16;
        }
    }

    /** Servers fake unbreakable gear with enchant levels like 32767 (shown as -32768); print those as a star. */
    private static String levelText(int level) {
        return level < 0 || level >= 100 ? "*" : Integer.toString(level);
    }

    /**
     * Tools, armour and other flat items are drawn as their atlas sprite in one quad: the full item renderer samples OpenGL state
     * (other mods hook it) and that costs far more than the icon is worth when a dozen tags are on screen. False for blocks.
     */
    private static boolean drawFlatIcon(Minecraft mc, net.minecraft.item.ItemStack stack, int x, int y) {
        try {
            if (stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                return false;
            }
            net.minecraft.util.IIcon sprite = stack.getIconIndex();
            if (sprite == null) {
                return false;
            }
            mc.getTextureManager().bindTexture(mc.getTextureManager().getResourceLocation(stack.getItemSpriteNumber()));
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
            tess.startDrawing(7);
            tess.addVertexWithUV(x, y + 16, 0, sprite.getMinU(), sprite.getMaxV());
            tess.addVertexWithUV(x + 16, y + 16, 0, sprite.getMaxU(), sprite.getMaxV());
            tess.addVertexWithUV(x + 16, y, 0, sprite.getMaxU(), sprite.getMinV());
            tess.addVertexWithUV(x, y, 0, sprite.getMinU(), sprite.getMinV());
            tess.draw();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class PendingTag {
        final EntityPlayer player;
        final List<List<AdventureText.Run>> lines;
        final double x, y, z;

        PendingTag(EntityPlayer player, List<List<AdventureText.Run>> lines, double x, double y, double z) {
            this.player = player;
            this.lines = lines;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<PendingTag> PENDING = new java.util.ArrayList<PendingTag>();

    private static final class PreparedLine {
        final List<AdventureText.Run> runs;
        final int width;

        PreparedLine(List<AdventureText.Run> runs, FontRenderer font) {
            this.runs = runs;
            int measured = 0;
            for (AdventureText.Run run : runs) {
                measured += font.getStringWidth(run.display);
            }
            this.width = measured;
        }

        void draw(FontRenderer font, int y) {
            int cursor = -width / 2;
            if (EnvoySettings.nametagBackground) {
                drawBackground(cursor - 1, y - 1, cursor + width + 1, y + font.FONT_HEIGHT);
            }
            for (AdventureText.Run run : runs) {
                font.drawString(run.display, cursor, y, 0xFF000000 | run.rgb, EnvoySettings.nametagShadow);
                cursor += font.getStringWidth(run.display);
            }
        }
    }

    /** True when renderQueued would draw something this frame. */
    public boolean hasWork() {
        return sticky() || !PENDING.isEmpty();
    }

    /** Draws the tags collected while the entities were rendered. Called once per frame from the world-last hook. */
    public void renderQueued(float partialTicks) {
        if (sticky()) {
            try {
                renderStaffTags(partialTicks);
            } catch (Throwable t) {
                dev.envoy.Envoy.problem("sticky nametags", t);
            }
        }
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingTag> batch = new java.util.ArrayList<PendingTag>(PENDING);
        PENDING.clear();
        for (PendingTag q : batch) {
            paintTag(q.player, q.lines, q.x, q.y, q.z, false);
        }
    }

    /** {@code detailed}: the staff view - health/distance line, gear icons with enchants, optional constant size. */
    private void paintTag(EntityPlayer player, List<List<AdventureText.Run>> lines, double x, double y, double z, boolean detailed) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        double distance = Math.sqrt(x * x + y * y + z * z);
        if (detailed) {
            lines = decorateStaffLines(player, lines, distance);
        }

        float baseOffset = player.height + 0.5F + EnvoySettings.nametagHeight;
        int lineHeight = font.FONT_HEIGHT + 1;
        int count = lines.size();
        List<PreparedLine> prepared = new java.util.ArrayList<PreparedLine>(count);
        for (List<AdventureText.Run> line : lines) {
            prepared.add(new PreparedLine(line, font));
        }

        GL11.glPushMatrix();
        GL11.glTranslated(x, y + baseOffset, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
        float size = TEXT_SCALE * EnvoySettings.nametagScale;
        if (detailed) {
            size *= Math.max(0.25F, Math.min(3.0F, EnvoySettings.staffTagScale));
        }
        if (detailed && distance < 5.0D) {
            size *= (float) Math.max(0.3D, distance / 5.0D); // right next to the player (staff teleport) a tag in world units would fill the screen
        }
        if (detailed && EnvoySettings.staffTagConstantSize) {
            size *= (float) Math.max(1.0D, Math.min(8.0D, distance / 8.0D));
        }
        GL11.glScalef(-size, -size, size);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (EnvoySettings.nametagThroughWalls || sticky()) {
            GlStateManager.disableDepth();
        } else {
            GlStateManager.enableDepth();
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < prepared.size(); i++) {
            prepared.get(i).draw(font, -i * lineHeight);
        }

        if (detailed && drawGear && EnvoySettings.staffTagEquipment) {
            drawEquipment(mc, font, player, -count * lineHeight - 18, lineHeight);
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private static void drawBackground(int x1, int y1, int x2, int y2) {
        GlStateManager.disableTexture2D();
        int bg = EnvoySettings.nametagBackgroundColor;
        GlStateManager.color(((bg >> 16) & 0xFF) / 255.0F, ((bg >> 8) & 0xFF) / 255.0F, (bg & 0xFF) / 255.0F, EnvoySettings.nametagBackgroundOpacity);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
