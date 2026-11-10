package dev.envoy.module.glint;

import dev.envoy.LunarHost;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class GlintState {
    public static final String LUNAR_TAG = "lunar";
    public static final String GLINT_KEY = "glint";

    private GlintState() { }

    public static boolean hasGlint(ItemStack stack) {
        NBTTagCompound lunar = customData(stack);
        return !LunarHost.isLunar() && lunar != null && lunar.hasKey(GLINT_KEY);
    }

    public static int glintArgb(ItemStack stack) {
        NBTTagCompound lunar = customData(stack);
        return lunar == null ? 0 : lunar.getInteger(GLINT_KEY);
    }

    private static NBTTagCompound customData(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        NBTTagCompound root = stack.getTagCompound();
        return root.hasKey(LUNAR_TAG) ? root.getCompoundTag(LUNAR_TAG) : null;
    }
}
