package com.yelle233.yuanliuwujin.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 销毁核心物品（1.20.1 Forge 版本）。
 * <p>
 * 无需绑定特定流体，插入销毁机器后，机器即可接收并虚空销毁
 * 任意流体（原版/模组）或 Mekanism 化学品。
 * <p>
 * 不同于 {@link InfiniteCoreItem}，此核心无绑定状态，始终处于"就绪"状态。
 */
public class DestructionCoreItem extends Item {

    public DestructionCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        // 提示：此核心可销毁任意流体和化学品
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
