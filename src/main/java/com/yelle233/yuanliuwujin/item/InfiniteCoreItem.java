package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.registry.ModDataComponents;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 无限核心物品（Infinite Core）。
 * <p>
 * 绑定指定流体或化学品后，插入无限流体机器即可无限产出。
 * <p>
 * 各等级效果（虚空流体 : 任意流体）：
 * <ul>
 *   <li>Lv.1：1000 mB 虚空 → 1 mB 任意（默认）</li>
 *   <li>Lv.2：100 → 1</li>
 *   <li>Lv.3：10 → 1</li>
 *   <li>Lv.4：2 → 1</li>
 *   <li>超频 Lv.4：1 → 1（消耗与产出等量，但有爆炸风险）</li>
 * </ul>
 */
public class InfiniteCoreItem extends Item {

    public InfiniteCoreItem(Properties properties) {
        super(properties);
    }

    // ── 绑定类型枚举 ───────────────────────────────────────────

    public enum BindType { NONE, FLUID, CHEMICAL }

    // ── 等级工具方法 ────────────────────────────────────────────

    public static int getLevel(ItemStack stack) {
        Integer lvl = stack.get(ModDataComponents.CORE_LEVEL.get());
        return (lvl != null) ? Math.max(1, Math.min(4, lvl)) : 1;
    }

    public static void setLevel(ItemStack stack, int level) {
        stack.set(ModDataComponents.CORE_LEVEL.get(), Math.max(1, Math.min(4, level)));
    }

    public static boolean isOverclocked(ItemStack stack) {
        Boolean oc = stack.get(ModDataComponents.IS_OVERCLOCKED.get());
        return oc != null && oc && getLevel(stack) == 4;
    }

    public static void setOverclocked(ItemStack stack, boolean oc) {
        if (getLevel(stack) == 4) {
            stack.set(ModDataComponents.IS_OVERCLOCKED.get(), oc);
        }
    }

    // ── 绑定方法 ───────────────────────────────────────────────

    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_FLUID.get());
    }

    @Nullable
    public static ResourceLocation getBoundChemical(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_CHEMICAL.get());
    }

    public static BindType getBindType(ItemStack stack) {
        if (getBoundFluid(stack) != null) return BindType.FLUID;
        if (getBoundChemical(stack) != null) return BindType.CHEMICAL;
        return BindType.NONE;
    }

    public static boolean hasValidBinding(ItemStack stack) {
        return getBindType(stack) != BindType.NONE;
    }

    // ── 右键交互 ──────────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();

        // 潜行右键 → 清除绑定
        if (player.isShiftKeyDown()) {
            return handleClear(player, stack, ctx.getHand());
        }

        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();

        // 1) 尝试从流体容器绑定
        ResourceLocation fluidId = tryGetFluidIdFromHandler(level, pos, face);
        if (fluidId != null) return tryBind(player, stack, ctx.getHand(), fluidId, BindType.FLUID);

        // 2) 尝试从 Mekanism 化学品储罐绑定
        if (MekanismChecker.isLoaded()) {
            ResourceLocation chemId = MekChemicalHelper.tryGetChemicalIdFromHandler(level, pos, face);
            if (chemId != null) return tryBind(player, stack, ctx.getHand(), chemId, BindType.CHEMICAL);
        }

        // 3) 尝试从液体方块绑定
        ResourceLocation worldFluidId = tryGetFluidIdFromWorld(level, pos, face);
        if (worldFluidId != null) return tryBind(player, stack, ctx.getHand(), worldFluidId, BindType.FLUID);

        return InteractionResult.PASS;
    }

    private InteractionResult handleClear(Player player, ItemStack stack, InteractionHand hand) {
        if (!Modconfigs.ALLOW_UNBIND_SURVIVAL.get() && !player.isCreative()) {
            player.sendSystemMessage(Component.translatable("msg.yuanliuwujin.unbind_disabled")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (getBindType(stack) == BindType.NONE) return InteractionResult.PASS;
        stack.remove(ModDataComponents.BOUND_FLUID.get());
        stack.remove(ModDataComponents.BOUND_CHEMICAL.get());
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        player.sendSystemMessage(Component.translatable("tooltip.yuanliuwujin.core.text4")
                .withStyle(ChatFormatting.YELLOW));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult tryBind(Player player, ItemStack stack, InteractionHand hand,
                                       ResourceLocation id, BindType type) {
        BindType existing = getBindType(stack);
        if (existing != BindType.NONE && existing != type) {
            player.sendSystemMessage(Component.translatable("tooltip.yuanliuwujin.core.text2")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        // 检查 ban list
        if (type == BindType.FLUID && isBanned(id)) {
            player.sendSystemMessage(Component.translatable("tooltip.fluid_banned", id)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        // 已绑定同一流体
        ResourceLocation current = (type == BindType.FLUID)
                ? getBoundFluid(stack) : getBoundChemical(stack);
        if (id.equals(current)) {
            player.sendSystemMessage(Component.translatable("tooltip.yuanliuwujin.core.text1")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        // 执行绑定
        if (type == BindType.FLUID) {
            stack.set(ModDataComponents.BOUND_FLUID.get(), id);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        } else {
            stack.set(ModDataComponents.BOUND_CHEMICAL.get(), id);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(2));
        }
        player.sendSystemMessage(Component.literal(
                (type == BindType.FLUID ? "§a绑定流体: " : "§b绑定化学品: ") + id));
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static ResourceLocation tryGetFluidIdFromHandler(Level level, BlockPos pos, Direction face) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        if (handler == null) return null;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (!fs.isEmpty()) {
                Fluid fluid = fs.getFluid();
                if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
                return BuiltInRegistries.FLUID.getKey(fluid);
            }
        }
        return null;
    }

    @Nullable
    private static ResourceLocation tryGetFluidIdFromWorld(Level level, BlockPos pos, Direction face) {
        BlockPos target = (face == Direction.UP) ? pos : pos.relative(face);
        FluidState fs = level.getFluidState(target);
        if (fs.isEmpty()) return null;
        Fluid fluid = fs.getType();
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    private static boolean isBanned(ResourceLocation fluidId) {
        List<? extends String> banned = Modconfigs.BANNED_FLUIDS.get();
        String fluidStr = fluidId.toString();
        for (String entry : banned) {
            if (entry.startsWith("#")) {
                // tag 检查（简化版）
                ResourceLocation tagId = ResourceLocation.parse(entry.substring(1));
                Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
                if (fluid != null && fluid.builtInRegistryHolder()
                        .is(net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.FLUID, tagId))) {
                    return true;
                }
            } else if (entry.equals(fluidStr)) {
                return true;
            }
        }
        return false;
    }

    // ── Tooltip ───────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        int lvl = getLevel(stack);
        boolean oc = isOverclocked(stack);

        ChatFormatting levelColor = switch (lvl) {
            case 1 -> ChatFormatting.GRAY;
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> oc ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core_level",
                lvl + (oc ? " ★" : "")).withStyle(levelColor));

        // 转换比例提示
        int ratio = Modconfigs.getInfiniteRatio(lvl, oc);
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.infinite_core.ratio",
                ratio).withStyle(ChatFormatting.DARK_GRAY));

        // 绑定状态
        BindType bindType = getBindType(stack);
        if (bindType == BindType.FLUID) {
            ResourceLocation id = getBoundFluid(stack);
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound", id.toString())
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));

        } else if (bindType == BindType.CHEMICAL) {
            ResourceLocation id = getBoundChemical(stack);
            Component chemName = MekanismChecker.isLoaded()
                    ? MekChemicalHelper.getChemicalName(id) : null;

            Object arg = (chemName != null) ? chemName : id.toString();
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound_chemical", arg)
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));

        } else {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.unbound")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (MekanismChecker.isLoaded()) {
                tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_chem_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }


        if (oc) {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.overclock_warning")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
