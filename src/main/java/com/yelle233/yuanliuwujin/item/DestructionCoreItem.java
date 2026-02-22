package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DestructionCoreItem extends Item {

    private final int level;
    private final boolean overclocked;

    public DestructionCoreItem(Properties properties, int level, boolean overclocked) {
        super(properties);
        this.level = Math.max(1, Math.min(4, level));
        this.overclocked = overclocked && this.level == 4;
    }

    public int getCoreLevel() { return level; }
    public boolean isCoreOverclocked() { return overclocked; }

    public static int getLevel(ItemStack stack) {
        if (stack.getItem() instanceof DestructionCoreItem core) return core.getCoreLevel();
        return 1;
    }

    public static boolean isOverclocked(ItemStack stack) {
        if (stack.getItem() instanceof DestructionCoreItem core) return core.isCoreOverclocked();
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int lvl = this.level; boolean oc = this.overclocked;
        ChatFormatting levelColor = switch (lvl) { case 1 -> ChatFormatting.GRAY; case 2 -> ChatFormatting.GREEN; case 3 -> ChatFormatting.AQUA; case 4 -> oc ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE; default -> ChatFormatting.GRAY; };
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core_level", lvl + (oc ? " ★" : "")).withStyle(levelColor));
        int ratio = Modconfigs.getDestroyRatio(lvl, oc);
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.ratio", ratio).withStyle(ChatFormatting.DARK_GRAY));
        if (oc) tooltip.add(Component.translatable("tooltip.yuanliuwujin.overclock_warning").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
