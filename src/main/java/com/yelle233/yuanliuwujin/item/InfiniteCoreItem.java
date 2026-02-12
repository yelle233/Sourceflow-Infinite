package com.yelle233.yuanliuwujin.item;

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
 * 功能：
 * <ul>
 *   <li>右键液体方块或含液体的容器方块 → 绑定该液体</li>
 *   <li>潜行右键 → 清除绑定</li>
 *   <li>绑定后放入无限流体机器即可无限产出该液体</li>
 * </ul>
 */
public class InfiniteCoreItem extends Item {

    public InfiniteCoreItem(Properties properties) {
        super(properties);
    }

    /* ====== 右键方块交互（优先从容器绑定，其次液体方块） ====== */

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            forceUpdateStack(player, ctx.getHand(), stack);
            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        BlockPos pos = ctx.getClickedPos();

        // 1) 优先尝试从容器方块绑定液体
        ResourceLocation fluidId = tryGetFluidIdFromHandler(level, pos, ctx.getClickedFace());
        if (fluidId != null) {
            return tryBind(player, stack, ctx.getHand(), fluidId);
        }

        // 2) 尝试从液体方块绑定
        fluidId = tryGetFluidIdFromWorld(level, pos, ctx.getClickedFace());
        if (fluidId != null) {
            return tryBind(player, stack, ctx.getHand(), fluidId);
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

        // 射线检测玩家准星方向的液体方块
        BlockPos fluidPos = findFluidPos(level, player);
        if (fluidPos == null) return InteractionResultHolder.pass(stack);

        ResourceLocation fluidId = resolveSourceFluidId(level.getFluidState(fluidPos));
        if (fluidId == null) return InteractionResultHolder.pass(stack);

        InteractionResult result = tryBind(player, stack, hand, fluidId);
        return result == InteractionResult.CONSUME
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.pass(stack);
    }

    /* ====== 库存 Tick：同步模型数据 ====== */

    /**
     * 每秒检查一次绑定状态，更新 CustomModelData 以切换物品外观。
     * CustomModelData(1) = 已绑定外观，无 = 默认外观。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;

        ResourceLocation bound = getBoundFluid(stack);
        boolean shouldLookBound = bound != null && !Modconfigs.isFluidBanned(bound);

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
        ResourceLocation boundId = getBoundFluid(stack);

        if (boundId == null) {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.unbound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_hint").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        Component fluidName = fluid.getFluidType().getDescription();
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound", fluidName).withStyle(ChatFormatting.GRAY));

        if (Modconfigs.isFluidBanned(boundId)) {
            tooltip.add(Component.literal("BANNED by config").withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /* ====== 绑定数据的读写（Data Component） ====== */

    /** 读取绑定的液体 ID，未绑定则返回 null */
    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_FLUID.get());
    }

    private static void setBoundFluid(ItemStack stack, ResourceLocation id) {
        stack.set(ModDataComponents.BOUND_FLUID.get(), id);
    }

    private static void clearBoundFluid(ItemStack stack) {
        stack.remove(ModDataComponents.BOUND_FLUID.get());
    }

    /* ====== 核心绑定/解绑逻辑（处理堆叠拆分） ====== */

    /**
     * 统一的绑定入口：检查 banlist、重复绑定，然后执行绑定。
     * @return CONSUME 或 PASS
     */
    private InteractionResult tryBind(Player player, ItemStack stack, InteractionHand hand, ResourceLocation fluidId) {
        // banlist 检查
        if (Modconfigs.isFluidBanned(fluidId)) {
            player.displayClientMessage(
                    Component.translatable("tooltip.fluid_banned", fluidId.toString()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // 已绑定检查
        ResourceLocation bound = getBoundFluid(stack);
        if (bound != null) {
            if (bound.equals(fluidId)) {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        // 执行绑定
        bindOneCore(player, stack, fluidId);
        forceUpdateStack(player, hand, stack);

        player.displayClientMessage(
                Component.translatable("tooltip.yuanliuwujin.core.text3").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(fluidId.toString()).withStyle(ChatFormatting.AQUA)),
                true);
        return InteractionResult.CONSUME;
    }

    /**
     * 绑定一个核心。如果堆叠数 > 1，先拆出单个再绑定。
     */
    private static void bindOneCore(Player player, ItemStack stackInHand, ResourceLocation fluidId) {
        if (player.level().isClientSide) return;

        if (stackInHand.getCount() == 1) {
            setBoundFluid(stackInHand, fluidId);
            setCoreModelState(stackInHand, true);
            syncInventory(player);
            return;
        }

        // 堆叠 > 1：减少原堆叠，创建单个已绑定的副本
        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        setBoundFluid(single, fluidId);
        setCoreModelState(single, true);

        if (!player.addItem(single)) {
            player.drop(single, false);
        }
        syncInventory(player);
    }

    /**
     * 解绑一个核心。如果堆叠数 > 1，先拆出单个再清除。
     */
    private static void unbindOneCore(Player player, ItemStack stackInHand) {
        if (stackInHand.getCount() == 1) {
            clearBoundFluid(stackInHand);
            setCoreModelState(stackInHand, false);
            return;
        }

        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        clearBoundFluid(single);
        setCoreModelState(single, false);

        if (!player.addItem(single)) {
            player.drop(single, false);
        }
    }

    /** 设置物品的 CustomModelData 来切换外观 */
    private static void setCoreModelState(ItemStack stack, boolean filled) {
        if (filled) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    /* ====== 工具方法 ====== */

    /** 强制同步物品栈到客户端 */
    private static void forceUpdateStack(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        if (!player.level().isClientSide) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** 同步背包变更 */
    private static void syncInventory(Player player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * 从世界中的液体方块获取源液体的 ID。
     * 先检查点击位置，再检查点击面的对面。
     */
    @Nullable
    private static ResourceLocation tryGetFluidIdFromWorld(Level level, BlockPos pos, Direction clickedFace) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty()) {
            // 点击位置没有液体，检查点击面的外侧
            fluidState = level.getFluidState(pos.relative(clickedFace));
        }
        return fluidState.isEmpty() ? null : resolveSourceFluidId(fluidState);
    }

    /** 将 FluidState 解析为源液体的 ResourceLocation */
    @Nullable
    private static ResourceLocation resolveSourceFluidId(FluidState fluidState) {
        if (fluidState.isEmpty()) return null;
        Fluid fluid = fluidState.getType();
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    /** 沿玩家视线方向搜索最近的液体方块 */
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

            // 跳过同一个方块位置
            if (pos.equals(lastPos)) continue;
            lastPos = pos;

            if (!level.getFluidState(pos).isEmpty()) return pos;
        }
        return null;
    }

    /* ====== 容器内液体检测 ====== */

    /**
     * 尝试从指定位置的方块的流体 Handler 中获取液体 ID。
     * 优先使用点击面的方向，然后遍历所有方向，最后尝试 null。
     */
    @Nullable
    private static ResourceLocation tryGetFluidIdFromHandler(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        // 优先检查点击面
        if (preferredSide != null) {
            ResourceLocation id = firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, preferredSide));
            if (id != null) return id;
        }

        // 遍历所有方向
        for (Direction d : Direction.values()) {
            ResourceLocation id = firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d));
            if (id != null) return id;
        }

        // 最后尝试 null context
        try {
            return firstNonEmptyFluidId(
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从 FluidHandler 的所有槽中找到第一个非空液体的源液体 ID */
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
