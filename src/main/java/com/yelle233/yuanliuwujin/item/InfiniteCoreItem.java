package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.registry.ModDataComponents;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

public class InfiniteCoreItem extends Item {

    private static final String TAG_BOUND_FLUID = "BoundFluidId";

    public InfiniteCoreItem(Properties properties) {
        super(properties);
    }

    // 右键方块：优先从容器(IFluidHandler)绑定，其次绑定液体方块
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            // 强制写回并保存
            forceUpdateStack(player, ctx.getHand(), stack);

            player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        // ===== 1) 如果点到的是容器，就从容器里绑定 =====
        ResourceLocation tankFluidId = tryGetFluidIdFromHandler(level, pos, ctx.getClickedFace());
        if (tankFluidId != null) {

            // banlist：禁止绑定
            if (Modconfigs.isFluidBanned(tankFluidId)) {
                player.displayClientMessage(
                        Component.translatable("tooltip.fluid_banned", tankFluidId.toString()).withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.CONSUME;
            }

            ResourceLocation bound = getBoundFluid(stack);
            if (bound != null) {
                if (bound.equals(tankFluidId)) {
                    player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.CONSUME;
            }

            bindOneCore(player, stack, tankFluidId);

            // 强制写回并保存
            forceUpdateStack(player, ctx.getHand(), stack);

            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text3").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(tankFluidId.toString()).withStyle(ChatFormatting.AQUA)),
                    true
            );
            return InteractionResult.CONSUME;
        }

        // ===== 2) 否则按液体方块绑定 =====
        FluidState fluidState = level.getFluidState(pos);

        // 如果点击位置没有液体，检查点击面的对侧（液体可能在那里）
        if (fluidState.isEmpty()) {
            Direction face = ctx.getClickedFace();
            BlockPos fluidPos = pos.relative(face);
            fluidState = level.getFluidState(fluidPos);

            if (fluidState.isEmpty()) {
                return InteractionResult.PASS;
            }
        }

        Fluid fluid = fluidState.getType();
        if (fluid instanceof FlowingFluid ff) {
            fluid = ff.getSource();
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);

        // banlist：禁止绑定
        if (Modconfigs.isFluidBanned(id)) {
            player.displayClientMessage(
                    Component.translatable("tooltip.fluid_banned", id.toString()).withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.CONSUME;
        }

        ResourceLocation bound = getBoundFluid(stack);
        if (bound != null) {
            if (bound.equals(id)) {
                player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
            } else {
                player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        bindOneCore(player, stack, id);

        // ✅ 强制写回并保存
        forceUpdateStack(player, ctx.getHand(), stack);

        player.displayClientMessage(
                Component.translatable("tooltip.yuanliuwujin.core.text3").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)),
                true
        );

        return InteractionResult.CONSUME;
    }

    // 空中右键：潜行清除 / 射线绑定液体方块
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 潜行空中右键：清除绑定
        if (!level.isClientSide && player.isShiftKeyDown()) {
            unbindOneCore(player, stack);

            // 强制写回并保存
            forceUpdateStack(player, hand, stack);

            player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.consume(stack);
        }

        // 空中右键：射线检测玩家准星指向（包含液体）
        if (!level.isClientSide) {
            BlockPos fluidPos = findFluidPos(level, player);
            if (fluidPos != null) {
                FluidState fluidState = level.getFluidState(fluidPos);
                Fluid fluid = fluidState.getType();
                if (fluid instanceof FlowingFluid ff) {
                    fluid = ff.getSource();
                }

                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);

                // banlist：禁止绑定
                if (Modconfigs.isFluidBanned(id)) {
                    player.displayClientMessage(
                            Component.translatable("tooltip.fluid_banned", id.toString()).withStyle(ChatFormatting.RED),
                            true
                    );
                    return InteractionResultHolder.consume(stack);
                }

                ResourceLocation bound = getBoundFluid(stack);
                if (bound != null) {
                    if (bound.equals(id)) {
                        player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
                    }
                    return InteractionResultHolder.consume(stack);
                }

                bindOneCore(player, stack, id);

                //  强制写回并保存
                forceUpdateStack(player, hand, stack);

                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.text3").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)),
                        true
                );

                return InteractionResultHolder.consume(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * ✅ 改进：每秒校正一次 + 调试日志
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        if (level.getGameTime() % 20 != 0) return;

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

    @Nullable
    private static BlockPos findFluidPos(Level level, Player player) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        double step = 0.1;
        int steps = (int) Math.ceil(reach / step);

        BlockPos lastPos = null;

        for (int i = 0; i <= steps; i++) {
            double dist = i * step;
            Vec3 p = eye.add(look.x * dist, look.y * dist, look.z * dist);
            BlockPos pos = BlockPos.containing(p);

            if (lastPos != null && lastPos.equals(pos)) continue;
            lastPos = pos;

            if (!level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation boundId = getBoundFluid(stack);

        if (boundId == null) {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.unbound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_hint").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        var fluid = BuiltInRegistries.FLUID.get(boundId);
        Component fluidName = fluid.getFluidType().getDescription();

        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound", fluidName).withStyle(ChatFormatting.GRAY));

        if (Modconfigs.isFluidBanned(boundId)) {
            tooltip.add(Component.literal("BANNED by config").withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void setCoreModelState(ItemStack stack, boolean filled) {
        if (filled) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    private static void bindOneCore(Player player, ItemStack stackInHand, ResourceLocation fluidId) {
        if (player.level().isClientSide) return;

        // 手里只有 1 个：直接绑定
        if (stackInHand.getCount() == 1) {
            setBoundFluid(stackInHand, fluidId);
            setCoreModelState(stackInHand, true);

            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return;
        }

        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);

        setBoundFluid(single, fluidId);
        setCoreModelState(single, true);

        // 拆出来的 1 个塞回背包（塞不进去就丢地上）
        if (!player.addItem(single)) {
            player.drop(single, false);
        }

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

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

    /**
     * ✅ 修复：使用更可靠的方式存储绑定数据
     */
    private static void setBoundFluid(ItemStack stack, ResourceLocation id) {
//        CompoundTag tag = getOrCreateCustomTag(stack);
//        tag.putString(TAG_BOUND_FLUID, id.toString());
//
//        // 确保数据被正确包装并持久化
//        CustomData customData = CustomData.of(tag);
//        stack.set(DataComponents.CUSTOM_DATA, customData);
        stack.set(ModDataComponents.BOUND_FLUID.get(), id);
    }

    private static void clearBoundFluid(ItemStack stack) {
//        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
//        if (data == null) return;
//
//        CompoundTag tag = data.copyTag();
//        tag.remove(TAG_BOUND_FLUID);
//
//        if (tag.isEmpty()) {
//            stack.remove(DataComponents.CUSTOM_DATA);
//        } else {
//            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
//        }
        stack.remove(ModDataComponents.BOUND_FLUID.get());
    }

    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
//        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
//        if (data == null) {
//            return null;
//        }
//
//        CompoundTag tag = data.copyTag();
//        if (!tag.contains(TAG_BOUND_FLUID)) {
//            return null;
//        }
//
//        String fluidIdStr = tag.getString(TAG_BOUND_FLUID);
//        ResourceLocation result = ResourceLocation.tryParse(fluidIdStr);
//        return result;
        return stack.get(ModDataComponents.BOUND_FLUID.get());
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    /**
     * ✅ 新增：强制更新物品栈并保存
     * 确保数据被正确持久化
     */
    private static void forceUpdateStack(Player player, InteractionHand hand, ItemStack stack) {
        // 方法1：强制设置回玩家手中（触发同步）
        player.setItemInHand(hand, stack);

        // 方法2：标记背包已修改（触发保存）
        player.getInventory().setChanged();

        // 方法3：如果是服务端，额外触发一次数据同步
        if (!player.level().isClientSide) {
            // 这会确保客户端收到最新的物品数据
            player.inventoryMenu.broadcastChanges();
        }
    }

    // ===== 检测容器内液体 =====
    @Nullable
    private static ResourceLocation tryGetFluidIdFromHandler(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        if (preferredSide != null) {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, preferredSide);
            ResourceLocation id = firstNonEmptyFluidId(h);
            if (id != null) return id;
        }

        for (Direction d : Direction.values()) {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d);
            ResourceLocation id = firstNonEmptyFluidId(h);
            if (id != null) return id;
        }

        try {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
            ResourceLocation id = firstNonEmptyFluidId(h);
            if (id != null) return id;
        } catch (Throwable ignored) {}

        return null;
    }

    @Nullable
    private static ResourceLocation firstNonEmptyFluidId(@Nullable IFluidHandler h) {
        if (h == null) return null;

        for (int i = 0; i < h.getTanks(); i++) {
            FluidStack fs = h.getFluidInTank(i);
            if (fs == null || fs.isEmpty()) continue;

            Fluid fluid = fs.getFluid();
            if (fluid instanceof FlowingFluid ff) {
                fluid = ff.getSource();
            }

            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) return id;
        }
        return null;
    }
}
