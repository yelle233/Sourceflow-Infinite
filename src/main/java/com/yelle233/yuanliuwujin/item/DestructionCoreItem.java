package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.registry.ModDataComponents;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 销毁核心物品（Destruction Core）。
 * <p>
 * 各等级效果（任意流体 : 虚空流体）：
 * <ul>
 *   <li>Lv.1：1000 mB 任意 → 1 mB 虚空（默认配置）</li>
 *   <li>Lv.2：100 mB 任意 → 1 mB 虚空</li>
 *   <li>Lv.3：10 mB 任意 → 1 mB 虚空</li>
 *   <li>Lv.4：2 mB 任意 → 1 mB 虚空</li>
 *   <li>超频 Lv.4：1 mB 任意 → 1 mB 虚空（但机器会积累压力）</li>
 * </ul>
 * 等级和超频状态通过 DataComponent 存储在物品 NBT 中。
 */
public class DestructionCoreItem extends Item {

    public DestructionCoreItem(Properties properties) {
        super(properties);
    }

    // ── 等级工具方法 ────────────────────────────────────────────

    /** 获取核心等级（默认 1） */
    public static int getLevel(ItemStack stack) {
        Integer lvl = stack.get(ModDataComponents.CORE_LEVEL.get());
        return (lvl != null) ? Math.max(1, Math.min(4, lvl)) : 1;
    }

    /** 设置核心等级 */
    public static void setLevel(ItemStack stack, int level) {
        stack.set(ModDataComponents.CORE_LEVEL.get(), Math.max(1, Math.min(4, level)));
    }

    /** 是否已超频（只有等级 4 可超频） */
    public static boolean isOverclocked(ItemStack stack) {
        Boolean oc = stack.get(ModDataComponents.IS_OVERCLOCKED.get());
        return oc != null && oc && getLevel(stack) == 4;
    }

    /** 设置超频状态（仅对等级 4 生效） */
    public static void setOverclocked(ItemStack stack, boolean oc) {
        if (getLevel(stack) == 4) {
            stack.set(ModDataComponents.IS_OVERCLOCKED.get(), oc);
        }
    }

    // ── Tooltip ───────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        int level = getLevel(stack);
        boolean oc = isOverclocked(stack);

        // 等级显示
        ChatFormatting levelColor = switch (level) {
            case 1 -> ChatFormatting.GRAY;
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> oc ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core_level",
                level + (oc ? " ★" : "")).withStyle(levelColor));

        // 转换比例
        int ratio = Modconfigs.getDestroyRatio(level, oc);
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.ratio",
                ratio).withStyle(ChatFormatting.DARK_GRAY));

        // 超频警告
        if (oc) {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.overclock_warning")
                    .withStyle(ChatFormatting.RED));
        }

        // 使用提示
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.destruction_core.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 创建指定等级的销毁核心 ItemStack。
     */
    public static ItemStack ofLevel(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        setLevel(stack, level);
        return stack;
    }
}
