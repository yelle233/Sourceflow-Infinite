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
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 无限核心物品。
 * <p>
 * 支持绑定两种物质类型：
 * <ul>
 *   <li><b>流体</b>（原版/模组）—— 右键液体方块或流体容器</li>
 *   <li><b>化学品</b>（Mekanism）—— 右键 Mekanism 化学品储罐（需安装 Mekanism）</li>
 * </ul>
 * 潜行右键清除绑定。两种绑定互斥，同一核心只能绑定一种。
 */
public class InfiniteCoreItem extends Item {

    public InfiniteCoreItem(Properties properties) {
        super(properties);
    }

    /* ====== 绑定类型枚举 ====== */

    /** 标识当前绑定的物质类型 */
    public enum BindType {
        /** 未绑定 */
        NONE,
        /** 原版/模组流体 */
        FLUID,
        /** Mekanism 化学品 */
        CHEMICAL
    }

    /* ====== 右键方块交互 ====== */

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();



        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();

        // 1) 尝试从流体容器绑定
        ResourceLocation fluidId = tryGetFluidIdFromHandler(level, pos, face);
        if (fluidId != null) {
            return tryBind(player, stack, ctx.getHand(), fluidId, BindType.FLUID);
        }

        // 2) 尝试从 Mekanism 化学品储罐绑定（仅在 Mekanism 已加载时）
        if (MekanismChecker.isLoaded()) {
            ResourceLocation chemId = MekChemicalHelper.tryGetChemicalIdFromHandler(level, pos, face);
            if (chemId != null) {
                return tryBind(player, stack, ctx.getHand(), chemId, BindType.CHEMICAL);
            }
        }

        // 3) 尝试从液体方块绑定
        ResourceLocation worldFluidId = tryGetFluidIdFromWorld(level, pos, face);
        if (worldFluidId != null) {
            return tryBind(player, stack, ctx.getHand(), worldFluidId, BindType.FLUID);
        }

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            forceUpdateStack(player, ctx.getHand(), stack);
            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    /* ====== 右键空气交互（射线检测液体方块） ====== */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            forceUpdateStack(player, hand, stack);
            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.consume(stack);
        }

        // 射线检测液体方块
        BlockPos fluidPos = findFluidPos(level, player);
        if (fluidPos == null) return InteractionResultHolder.pass(stack);

        ResourceLocation fluidId = resolveSourceFluidId(level.getFluidState(fluidPos));
        if (fluidId == null) return InteractionResultHolder.pass(stack);

        InteractionResult result = tryBind(player, stack, hand, fluidId, BindType.FLUID);
        return result == InteractionResult.CONSUME
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.pass(stack);
    }

    /* ====== 库存 Tick：同步模型数据 ====== */

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;

        boolean shouldLookBound = false;

        // 检查流体绑定
        ResourceLocation boundFluid = getBoundFluid(stack);
        if (boundFluid != null && !Modconfigs.isFluidBanned(boundFluid)) {
            shouldLookBound = true;
        }

        // 检查化学品绑定
        if (!shouldLookBound) {
            ResourceLocation boundChem = getBoundChemical(stack);
            if (boundChem != null) {
                shouldLookBound = true;
            }
        }

        if (shouldLookBound) {
            if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
            }
        } else {
            if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            }
        }
    }

    /* ====== Tooltip ====== */

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation boundFluidId = getBoundFluid(stack);
        ResourceLocation boundChemId = getBoundChemical(stack);

        if (boundFluidId == null && boundChemId == null) {
            // 未绑定
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.unbound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_hint").withStyle(ChatFormatting.DARK_GRAY));
            if (MekanismChecker.isLoaded()) {
                tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_chem_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (boundFluidId != null) {
            // 绑定了流体
            Fluid fluid = BuiltInRegistries.FLUID.get(boundFluidId);
            Component fluidName = fluid.getFluidType().getDescription();
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound", fluidName).withStyle(ChatFormatting.GRAY));

            if (Modconfigs.isFluidBanned(boundFluidId)) {
                tooltip.add(Component.literal("BANNED by config").withStyle(ChatFormatting.RED));
            }
        } else {
            // 绑定了化学品
            Component chemName = null;
            if (MekanismChecker.isLoaded()) {
                chemName = MekChemicalHelper.getChemicalName(boundChemId);
            }
            if (chemName == null) {
                chemName = Component.literal(boundChemId.toString());
            }
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound_chemical", chemName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /* ====== 绑定数据读写 ====== */

    /** 获取绑定类型 */
    public static BindType getBindType(ItemStack stack) {
        if (stack.has(ModDataComponents.BOUND_FLUID.get())) return BindType.FLUID;
        if (stack.has(ModDataComponents.BOUND_CHEMICAL.get())) return BindType.CHEMICAL;
        return BindType.NONE;
    }

    /** 读取绑定的流体 ID */
    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_FLUID.get());
    }

    /** 读取绑定的化学品 ID */
    @Nullable
    public static ResourceLocation getBoundChemical(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_CHEMICAL.get());
    }

    /** 设置流体绑定（同时清除化学品绑定） */
    private static void setBoundFluid(ItemStack stack, ResourceLocation id) {
        stack.set(ModDataComponents.BOUND_FLUID.get(), id);
        stack.remove(ModDataComponents.BOUND_CHEMICAL.get()); // 互斥
    }

    /** 设置化学品绑定（同时清除流体绑定） */
    private static void setBoundChemical(ItemStack stack, ResourceLocation id) {
        stack.set(ModDataComponents.BOUND_CHEMICAL.get(), id);
        stack.remove(ModDataComponents.BOUND_FLUID.get()); // 互斥
    }

    /** 清除所有绑定 */
    private static void clearBinding(ItemStack stack) {
        stack.remove(ModDataComponents.BOUND_FLUID.get());
        stack.remove(ModDataComponents.BOUND_CHEMICAL.get());
    }

    /* ====== 统一绑定入口 ====== */

    /**
     * 统一的绑定入口：检查 banlist、重复绑定，然后执行绑定。
     *
     * @param type 绑定类型（FLUID 或 CHEMICAL）
     */
    private InteractionResult tryBind(Player player, ItemStack stack, InteractionHand hand,
                                       ResourceLocation substanceId, BindType type) {
        // 流体 banlist 检查（化学品暂无 banlist，可后续扩展）
        if (type == BindType.FLUID && Modconfigs.isFluidBanned(substanceId)) {
            player.displayClientMessage(
                    Component.translatable("tooltip.fluid_banned", substanceId.toString()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // 已绑定检查：不允许在已有绑定时再绑定
        BindType currentType = getBindType(stack);
        if (currentType != BindType.NONE) {
            ResourceLocation currentId = (currentType == BindType.FLUID)
                    ? getBoundFluid(stack) : getBoundChemical(stack);
            if (currentId != null && currentId.equals(substanceId)) {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        // 执行绑定
        bindOneCore(player, stack, substanceId, type);
        forceUpdateStack(player, hand, stack);

        String translationKey = (type == BindType.CHEMICAL)
                ? "tooltip.yuanliuwujin.core.text3_chem"
                : "tooltip.yuanliuwujin.core.text3";
        player.displayClientMessage(
                Component.translatable(translationKey).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(substanceId.toString()).withStyle(ChatFormatting.AQUA)),
                true);
        return InteractionResult.CONSUME;
    }

    /* ====== 核心绑定/解绑（处理堆叠拆分） ====== */

    private static void bindOneCore(Player player, ItemStack stackInHand,
                                     ResourceLocation id, BindType type) {
        if (player.level().isClientSide) return;

        if (stackInHand.getCount() == 1) {
            applyBinding(stackInHand, id, type);
            setCoreModelState(stackInHand, true);
            syncInventory(player);
            return;
        }

        // 堆叠 > 1：拆出单个
        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        applyBinding(single, id, type);
        setCoreModelState(single, true);

        if (!player.addItem(single)) player.drop(single, false);
        syncInventory(player);
    }

    /** 根据类型设置对应的绑定组件 */
    private static void applyBinding(ItemStack stack, ResourceLocation id, BindType type) {
        if (type == BindType.CHEMICAL) {
            setBoundChemical(stack, id);
        } else {
            setBoundFluid(stack, id);
        }
    }

    private static void unbindOneCore(Player player, ItemStack stackInHand) {
        if (stackInHand.getCount() == 1) {
            clearBinding(stackInHand);
            setCoreModelState(stackInHand, false);
            return;
        }

        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        clearBinding(single);
        setCoreModelState(single, false);

        if (!player.addItem(single)) player.drop(single, false);
    }

    private static void setCoreModelState(ItemStack stack, boolean filled) {
        if (filled) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    /* ====== 工具方法 ====== */

    private static void forceUpdateStack(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        if (!player.level().isClientSide) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void syncInventory(Player player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /* ====== 流体检测工具方法 ====== */

    @Nullable
    private static ResourceLocation tryGetFluidIdFromWorld(Level level, BlockPos pos, Direction clickedFace) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty()) {
            fluidState = level.getFluidState(pos.relative(clickedFace));
        }
        return fluidState.isEmpty() ? null : resolveSourceFluidId(fluidState);
    }

    @Nullable
    private static ResourceLocation resolveSourceFluidId(FluidState fluidState) {
        if (fluidState.isEmpty()) return null;
        Fluid fluid = fluidState.getType();
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    @Nullable
    private static BlockPos findFluidPos(Level level, Player player) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        double step = 0.1;
        int steps = (int) Math.ceil(reach / step);
        BlockPos lastPos = null;

        for (int i = 0; i <= steps; i++) {
            Vec3 point = eye.add(look.scale(i * step));
            BlockPos pos = BlockPos.containing(point);
            if (pos.equals(lastPos)) continue;
            lastPos = pos;
            if (!level.getFluidState(pos).isEmpty()) return pos;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation tryGetFluidIdFromHandler(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        if (preferredSide != null) {
            ResourceLocation id = firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, preferredSide));
            if (id != null) return id;
        }

        for (Direction d : Direction.values()) {
            ResourceLocation id = firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d));
            if (id != null) return id;
        }

        try {
            return firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceLocation firstNonEmptyFluidId(@Nullable IFluidHandler handler) {
        if (handler == null) return null;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (fs == null || fs.isEmpty()) continue;
            Fluid fluid = fs.getFluid();
            if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) return id;
        }
        return null;
    }
}
